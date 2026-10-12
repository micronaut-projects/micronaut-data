/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.connection.reactive;

import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.support.RecordingReactorConnectionOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens when a synchronization callback of a reactive connection fails: which callbacks still run,
 * in which order, and what the subscriber sees.
 */
class ReactiveConnectionSynchronizationErrorsTest {

    private static final String C1 = "CONNECTION_1";

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final RecordingReactorConnectionOperations operations = new RecordingReactorConnectionOperations(events);
    private final IllegalStateException callbackFailure = new IllegalStateException("callback");
    private final IllegalArgumentException workFailure = new IllegalArgumentException("work");

    static Stream<Arguments> completionCallbacks() {
        return Stream.of("onComplete", "onClose", "afterClose")
            .flatMap(phase -> Stream.of(Failure.values()).map(failure -> Arguments.of(phase, failure)));
    }

    @ParameterizedTest
    @MethodSource("completionCallbacks")
    void callbackFailureWhenTheWorkCompletes(String phase, Failure failure) {
        Mono<String> work = operations.withConnectionMono(status -> {
            register(status, new Recording("first"));
            registerFailing(status, phase, failure);
            register(status, new Recording("last"));
            return Mono.just("value");
        });

        if (failure == Failure.THROWN) {
            // Only a callback that throws instead of returning a publisher is reported
            Throwable e = failureOf(work);
            assertEquals("Async resource cleanup failed after onComplete", e.getMessage());
            assertSame(callbackFailure, e.getCause());
        } else {
            assertEquals("value", work.block());
        }
        // Every callback runs and the connection is closed
        assertEquals(expectedEvents("onComplete", phase, failure), events);
    }

    @ParameterizedTest
    @EnumSource(value = Failure.class, names = "SYNCHRONOUS_ADAPTER", mode = EnumSource.Mode.EXCLUDE)
    void callbackFailureWhenTheWorkFails(Failure failure) {
        Mono<String> work = operations.withConnectionMono(status -> {
            register(status, new Recording("first"));
            registerFailing(status, "onError", failure);
            register(status, new Recording("last"));
            return Mono.error(workFailure);
        });

        Throwable e = failureOf(work);

        if (failure == Failure.THROWN) {
            // The callback failure replaces the failure of the work, which is only suppressed
            assertEquals("Async resource cleanup failed after onError", e.getMessage());
            assertSame(callbackFailure, e.getCause());
            assertArrayEquals(new Throwable[]{workFailure}, e.getSuppressed());
            assertArrayEquals(new Throwable[]{workFailure}, callbackFailure.getSuppressed());
        } else {
            assertSame(workFailure, e);
            assertEquals(0, e.getSuppressed().length);
        }
        assertEquals(expectedEvents("onError", "onError", failure), events);
    }

    @ParameterizedTest
    @EnumSource(value = Failure.class, names = "SYNCHRONOUS_ADAPTER", mode = EnumSource.Mode.EXCLUDE)
    void callbackFailureWhenTheWorkIsCancelled(Failure failure) throws InterruptedException {
        Disposable subscription = operations.withConnectionFlux(status -> {
            register(status, new Recording("first"));
            registerFailing(status, "onCancel", failure);
            register(status, new Recording("last"));
            return Flux.<String>never();
        }).subscribe();

        subscription.dispose();

        awaitEvent("first afterClose");
        assertEquals(expectedEvents("onCancel", "onCancel", failure), events);
    }

    @Test
    void synchronousSynchronizationIsNotCompletedWhenTheWorkFails() {
        Mono<String> work = operations.withConnectionMono(status -> {
            status.registerSynchronization(new RecordingSynchronization());
            return Mono.error(workFailure);
        });

        assertSame(workFailure, failureOf(work));
        // A synchronous connection always invokes executionComplete
        assertEquals(List.of("OPEN " + C1, "beforeClosed", "CLOSE " + C1, "afterClosed"), events);
    }

    @Test
    void synchronousSynchronizationIsNotCompletedWhenTheWorkIsCancelled() {
        Disposable subscription = operations.withConnectionFlux(status -> {
            status.registerSynchronization(new RecordingSynchronization());
            return Flux.<String>never();
        }).subscribe();

        subscription.dispose();

        assertEquals(List.of("OPEN " + C1, "beforeClosed", "CLOSE " + C1, "afterClosed"), events);
    }

    @Test
    void synchronizationOfAReusedConnectionIsNeverInvoked() {
        String result = operations.withConnectionMono(outer ->
            operations.withConnectionMono(inner -> {
                assertFalse(inner.isNew());
                register(inner, new Recording("inner"));
                inner.registerSynchronization(new RecordingSynchronization());
                return Mono.just("value");
            })
        ).block();

        assertEquals("value", result);
        // A synchronous connection invokes executionComplete when the scope reusing the connection completes
        assertEquals(List.of("OPEN " + C1, "CLOSE " + C1), events);
    }

    private List<String> expectedEvents(String completion, String failingPhase, Failure failure) {
        List<String> expected = new ArrayList<>();
        expected.add("OPEN " + C1);
        for (String phase : List.of(completion, "onClose", "afterClose")) {
            if (phase.equals("afterClose")) {
                expected.add("CLOSE " + C1);
            }
            boolean failing = phase.equals(failingPhase);
            if (failing && failure == Failure.THROWN) {
                // The callbacks are invoked while the chain is built, before the first one is subscribed
                expected.add("failing " + phase);
            }
            expected.add("last " + phase);
            if (failing && failure != Failure.THROWN && failure != Failure.SYNCHRONOUS_ADAPTER) {
                expected.add("failing " + phase);
            }
            expected.add("first " + phase);
            if (failing && failure == Failure.SYNCHRONOUS_ADAPTER) {
                // An ordered synchronization runs after the reactive synchronizations, which have the lowest precedence
                expected.add("failing " + phase);
            }
        }
        return expected;
    }

    /**
     * The error signalled to the subscriber: {@code block()} would add a suppressed exception to it.
     */
    private static Throwable failureOf(Mono<?> mono) {
        Signal<?> signal = mono.materialize().block();
        assertTrue(signal.isOnError(), () -> "Expected an error but was " + signal);
        return signal.getThrowable();
    }

    private void awaitEvent(String event) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!events.contains(event) && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(events.contains(event), () -> "Missing '" + event + "' in " + events);
    }

    private static void register(ConnectionStatus<String> status, ReactiveConnectionSynchronization synchronization) {
        ((ReactiveConnectionStatus<String>) status).registerReactiveSynchronization(synchronization);
    }

    private void registerFailing(ConnectionStatus<String> status, String phase, Failure failure) {
        if (failure == Failure.SYNCHRONOUS_ADAPTER) {
            status.registerSynchronization(new ConnectionSynchronization() {
                @Override
                public void executionComplete() {
                    failIn("onComplete");
                }

                @Override
                public void beforeClosed() {
                    failIn("onClose");
                }

                @Override
                public void afterClosed() {
                    failIn("afterClose");
                }

                private void failIn(String callback) {
                    if (callback.equals(phase)) {
                        events.add("failing " + phase);
                        throw callbackFailure;
                    }
                }
            });
        } else {
            register(status, new Failing(phase, failure));
        }
    }

    /**
     * How a callback fails.
     */
    enum Failure {
        /**
         * The callback throws instead of returning a publisher.
         */
        THROWN,
        /**
         * The callback returns a publisher that signals an error.
         */
        SIGNALLED,
        /**
         * The callback returns a publisher that throws when it is subscribed.
         */
        DEFERRED,
        /**
         * The callback returns a publisher that signals an error later, from another thread.
         */
        ASYNC,
        /**
         * A {@link ConnectionSynchronization} that throws.
         */
        SYNCHRONOUS_ADAPTER
    }

    private final class Recording implements ReactiveConnectionSynchronization {

        private final String name;

        private Recording(String name) {
            this.name = name;
        }

        @Override
        public Publisher<Void> onComplete() {
            return record("onComplete");
        }

        @Override
        public Publisher<Void> onError(Throwable throwable) {
            return record("onError");
        }

        @Override
        public Publisher<Void> onCancel() {
            return record("onCancel");
        }

        @Override
        public Publisher<Void> onClose() {
            return record("onClose");
        }

        @Override
        public Publisher<Void> afterClose() {
            return record("afterClose");
        }

        private Publisher<Void> record(String phase) {
            return Mono.fromRunnable(() -> events.add(name + " " + phase));
        }
    }

    private final class Failing implements ReactiveConnectionSynchronization {

        private final String phase;
        private final Failure failure;

        private Failing(String phase, Failure failure) {
            this.phase = phase;
            this.failure = failure;
        }

        @Override
        public Publisher<Void> onComplete() {
            return failIn("onComplete");
        }

        @Override
        public Publisher<Void> onError(Throwable throwable) {
            return failIn("onError");
        }

        @Override
        public Publisher<Void> onCancel() {
            return failIn("onCancel");
        }

        @Override
        public Publisher<Void> onClose() {
            return failIn("onClose");
        }

        @Override
        public Publisher<Void> afterClose() {
            return failIn("afterClose");
        }

        private Publisher<Void> failIn(String callback) {
            if (!callback.equals(phase)) {
                return Mono.empty();
            }
            return switch (failure) {
                case THROWN -> {
                    events.add("failing " + phase);
                    throw callbackFailure;
                }
                case SIGNALLED ->
                    Mono.<Void>error(callbackFailure).doOnSubscribe(subscription -> events.add("failing " + phase));
                case DEFERRED -> Mono.defer(() -> {
                    events.add("failing " + phase);
                    throw callbackFailure;
                });
                case ASYNC -> Mono.delay(Duration.ofMillis(20)).then(Mono.defer(() -> {
                    events.add("failing " + phase);
                    return Mono.error(callbackFailure);
                }));
                case SYNCHRONOUS_ADAPTER -> throw new IllegalStateException("Registered as a ConnectionSynchronization");
            };
        }
    }

    private final class RecordingSynchronization implements ConnectionSynchronization {

        @Override
        public void executionComplete() {
            events.add("executionComplete");
        }

        @Override
        public void beforeClosed() {
            events.add("beforeClosed");
        }

        @Override
        public void afterClosed() {
            events.add("afterClosed");
        }
    }
}
