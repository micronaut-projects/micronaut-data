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
package io.micronaut.transaction;

import io.micronaut.transaction.async.AsyncTransactionOperations;
import io.micronaut.transaction.async.AsyncUsingReactiveTransactionOperations;
import io.micronaut.transaction.async.AsyncUsingSyncTransactionOperations;
import io.micronaut.transaction.exceptions.UnexpectedRollbackException;
import io.micronaut.transaction.support.DefaultTransactionDefinition;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour of the asynchronous transaction operations implemented on top of
 * a synchronous and a reactive transaction manager.
 */
class AsyncTransactionOperationsTest {

    private static final String C1 = "CONNECTION_1";

    private static final List<String> NEW_TX = List.of("OPEN " + C1, "BEGIN TX " + C1, "COMMIT TX " + C1, "CLOSE " + C1);
    private static final List<String> ROLLED_BACK = List.of("OPEN " + C1, "BEGIN TX " + C1, "ROLLBACK TX " + C1, "CLOSE " + C1);

    @TestFactory
    List<DynamicTest> commitsWhenTheStageCompletes() {
        return forEachBackend(fixture -> {
            Object result = get(fixture.operations.withTransaction(status -> {
                assertTrue(status.isNewTransaction());
                assertEquals(C1, status.getConnection());
                return CompletableFuture.completedFuture("done");
            }));

            assertEquals("done", result);
            assertEquals(NEW_TX, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> rollsBackWhenTheStageFails() {
        return forEachBackend(fixture -> {
            IllegalStateException failure = new IllegalStateException("work");

            Throwable e = failureOf(fixture.operations.withTransaction(status -> CompletableFuture.failedFuture(failure)));

            assertSame(failure, e);
            assertEquals(ROLLED_BACK, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> rollsBackWhenTheHandlerThrows() {
        return forEachBackend(fixture -> {
            IllegalStateException failure = new IllegalStateException("work");

            Throwable e = failureOf(fixture.operations.withTransaction(status -> {
                throw failure;
            }));

            assertSame(failure, e);
            assertEquals(ROLLED_BACK, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> failureThatDoesNotRollBack() {
        return forEachBackend(fixture -> {
            DefaultTransactionDefinition noRollback = new DefaultTransactionDefinition();
            noRollback.setDontRollbackOn(List.of(IllegalStateException.class));
            IllegalStateException failure = new IllegalStateException("work");

            Throwable e = failureOf(fixture.operations.withTransaction(noRollback, status -> CompletableFuture.failedFuture(failure)));

            assertSame(failure, e);
            assertEquals(NEW_TX, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> failureThatIsNotInTheRollbackOnRules() {
        return forEachBackend(fixture -> {
            DefaultTransactionDefinition rollbackOnOther = new DefaultTransactionDefinition();
            rollbackOnOther.setRollbackOn(List.of(IllegalArgumentException.class));
            IllegalStateException failure = new IllegalStateException("work");

            Throwable e = failureOf(fixture.operations.withTransaction(rollbackOnOther, status -> CompletableFuture.failedFuture(failure)));

            assertSame(failure, e);
            assertEquals(NEW_TX, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> failureThatDoesNotRollBackInARollbackOnlyTransaction() {
        return forEachBackend(fixture -> {
            DefaultTransactionDefinition noRollback = new DefaultTransactionDefinition();
            noRollback.setDontRollbackOn(List.of(IllegalStateException.class));
            IllegalStateException failure = new IllegalStateException("work");

            Throwable e = failureOf(fixture.operations.withTransaction(noRollback, status -> {
                status.setRollbackOnly();
                return CompletableFuture.failedFuture(failure);
            }));

            assertSame(failure, e);
            assertEquals(ROLLED_BACK, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> failureThatDoesNotRollBackWrappedInACompletionException() {
        return forEachBackend(fixture -> {
            DefaultTransactionDefinition noRollback = new DefaultTransactionDefinition();
            noRollback.setDontRollbackOn(List.of(IllegalStateException.class));
            IllegalStateException failure = new IllegalStateException("work");

            // A dependent stage reports the failure wrapped in a CompletionException
            Throwable e = failureOf(fixture.operations.withTransaction(noRollback, status ->
                CompletableFuture.completedFuture("value").thenApply(value -> {
                    throw failure;
                })
            ));

            assertSame(failure, e);
            assertEquals(NEW_TX, fixture.log());
        });
    }

    @TestFactory
    List<DynamicTest> recoveredFailureOfAJoinedScope() {
        return forEachBackend(fixture -> {
            Supplier<CompletionStage<String>> work = () -> fixture.operations.withTransaction(outer ->
                fixture.operations.<String>withTransaction(inner -> {
                        assertEquals(false, inner.isNewTransaction());
                        return CompletableFuture.failedFuture(new IllegalStateException("inner"));
                    })
                    .exceptionally(e -> "recovered")
            );

            // The failure marks the transaction as rollback-only and fails the commit
            assertInstanceOf(UnexpectedRollbackException.class, failureOf(work.get()));
            assertEquals(ROLLED_BACK, fixture.log());
        });
    }

    private static <T> T get(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AssertionError(e);
        }
    }

    private static Throwable failureOf(CompletionStage<?> stage) {
        return assertThrows(ExecutionException.class, () -> stage.toCompletableFuture().get()).getCause();
    }

    private static List<DynamicTest> forEachBackend(Consumer<Fixture> test) {
        List<DynamicTest> tests = new ArrayList<>();
        for (Backend backend : Backend.values()) {
            tests.add(DynamicTest.dynamicTest(backend.name(), () -> test.accept(new Fixture(backend))));
        }
        return tests;
    }

    private enum Backend {
        SYNC, REACTIVE
    }

    private static final class Fixture {

        private final OpLogger opLogger = new OpLogger();
        private final AsyncTransactionOperations<String> operations;

        private Fixture(Backend backend) {
            if (backend == Backend.SYNC) {
                operations = new AsyncUsingSyncTransactionOperations<>(new SyncTxManager(new SyncConnManager(opLogger), opLogger));
            } else {
                operations = new AsyncUsingReactiveTransactionOperations<>(new ReactiveTxManager(new ReactiveConnManager(opLogger), opLogger));
            }
        }

        List<String> log() {
            return opLogger.getLogs();
        }
    }
}
