/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionSynchronization;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The default reactive connection status.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class DefaultReactiveConnectionStatus<C> implements ReactiveConnectionStatus<C> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveConnectionStatus.class);
    private static final Mono<Void> EMPTY = Mono.empty();

    private final C connection;
    private final ConnectionDefinition definition;
    private final ReactorConnectionOperations<C> connectionOperations;
    private final boolean isNew;
    @Nullable
    private final Executor executor;
    @Nullable
    private final Scheduler scheduler;

    @Nullable
    private List<ReactiveConnectionSynchronization> connectionSynchronizations;

    public DefaultReactiveConnectionStatus(C connection, ConnectionDefinition definition, ReactorConnectionOperations<C> connectionOperations, boolean isNew) {
        this(connection, definition, connectionOperations, isNew, null);
    }

    /**
     * @param connection           The connection
     * @param definition           The connection definition
     * @param connectionOperations The connection operations
     * @param isNew                Whether the connection was opened for this status
     * @param executor             The executor the connection must be used on, if it is bound to a thread
     * @since 5.3.0
     */
    @Internal
    public DefaultReactiveConnectionStatus(C connection,
                                           ConnectionDefinition definition,
                                           ReactorConnectionOperations<C> connectionOperations,
                                           boolean isNew,
                                           @Nullable Executor executor) {
        this.connection = connection;
        this.definition = definition;
        this.connectionOperations = connectionOperations;
        this.isNew = isNew;
        this.executor = executor;
        this.scheduler = executor == null ? null : Schedulers.fromExecutor(executor);
    }

    /**
     * @return The executor the connection must be used on, or {@code null} if it can be used from any thread
     * @since 5.3.0
     */
    @Internal
    @Nullable
    public Executor getExecutor() {
        return executor;
    }

    /**
     * @return The scheduler of the connection's executor, or {@code null} if it can be used from any thread
     * @since 5.3.0
     */
    @Internal
    @Nullable
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Run the work on the connection's executor, if it has one.
     *
     * @param work The work
     * @param <T>  The result type
     * @return The result
     * @since 5.3.0
     */
    @Internal
    public <T> Mono<T> onConnectionExecutor(Supplier<? extends Mono<T>> work) {
        Mono<T> mono = Mono.defer(work);
        return scheduler == null ? mono : mono.subscribeOn(scheduler);
    }

    /**
     * Run the work on the connection's executor, if it has one.
     *
     * @param work The work
     * @param <T>  The result type
     * @return The result
     * @since 5.3.0
     */
    @Internal
    public <T> Flux<T> onConnectionExecutorFlux(Supplier<? extends Flux<T>> work) {
        Flux<T> flux = Flux.defer(work);
        // Only the subscription needs the connection's thread, not the subscriber's requests
        return scheduler == null ? flux : flux.subscribeOn(scheduler, false);
    }

    public boolean isConnectionOf(ReactorConnectionOperations<C> connectionOperations) {
        return this.connectionOperations == connectionOperations;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public C getConnection() {
        return connection;
    }

    @Override
    public ConnectionDefinition getDefinition() {
        return definition;
    }

    @Override
    public void registerSynchronization(ConnectionSynchronization synchronization) {
        registerReactiveSynchronization(new SynchronousConnectionSynchronization(synchronization));
    }

    @Override
    public void registerReactiveSynchronization(ReactiveConnectionSynchronization synchronization) {
        if (connectionSynchronizations == null) {
            connectionSynchronizations = new ArrayList<>(5);
        }
        connectionSynchronizations.add(synchronization);
        OrderUtil.sort(connectionSynchronizations);
    }

    /**
     * Completes the connection after the work completed. A failure of a callback or of the close is signalled
     * once every callback has run and the connection has been closed.
     *
     * @param closeConnection The close of the connection
     * @return The publisher of the completion
     */
    public Publisher<Void> onComplete(Supplier<Publisher<Void>> closeConnection) {
        return Mono.defer(() -> {
            List<Throwable> failures = new ArrayList<>(1);
            return release(ReactiveConnectionSynchronization::onComplete, closeConnection, failures)
                .then(Mono.<Void>defer(() -> failures.isEmpty() ? EMPTY : Mono.error(combine(failures))));
        });
    }

    /**
     * Completes the connection after the work failed. The failure of the work stays the primary failure:
     * a failure of a callback or of the close is added to it as suppressed.
     *
     * @param workFailure     The failure of the work, signalled by the caller once the completion is done
     * @param closeConnection The close of the connection
     * @return The publisher of the completion
     */
    public Publisher<Void> onError(Throwable workFailure, Supplier<Publisher<Void>> closeConnection) {
        return Mono.defer(() -> {
            List<Throwable> failures = new ArrayList<>(1);
            return release(synchronization -> synchronization.onError(workFailure), closeConnection, failures)
                .then(Mono.<Void>fromRunnable(() -> {
                    for (Throwable failure : failures) {
                        if (failure != workFailure) {
                            workFailure.addSuppressed(failure);
                        }
                    }
                }));
        });
    }

    /**
     * Completes the connection after the work was cancelled. There is no subscriber left to signal
     * a failure of a callback or of the close to, so it is logged.
     *
     * @param closeConnection The close of the connection
     * @return The publisher of the completion
     */
    public Publisher<Void> onCancel(Supplier<Publisher<Void>> closeConnection) {
        return Mono.defer(() -> {
            List<Throwable> failures = new ArrayList<>(1);
            return release(ReactiveConnectionSynchronization::onCancel, closeConnection, failures)
                .then(Mono.<Void>fromRunnable(() -> {
                    if (!failures.isEmpty()) {
                        LOG.warn("Failed to complete the connection of a cancelled operation", combine(failures));
                    }
                }));
        });
    }

    /**
     * Runs the completion callbacks and closes the connection. A failing step doesn't prevent the following ones:
     * its failure is collected.
     */
    private Mono<Void> release(Function<ReactiveConnectionSynchronization, Publisher<Void>> completion,
                               Supplier<Publisher<Void>> closeConnection,
                               List<Throwable> failures) {
        Mono<Void> release = forEachSynchronizations(completion, failures);
        if (isNew) {
            release = release.then(forEachSynchronizations(ReactiveConnectionSynchronization::onClose, failures));
        }
        release = release.then(collectFailure(closeConnection, failures));
        if (isNew) {
            release = release.then(forEachSynchronizations(ReactiveConnectionSynchronization::afterClose, failures));
        }
        return release;
    }

    /**
     * Runs a callback of the synchronizations one after another, in the reverse order.
     */
    private Mono<Void> forEachSynchronizations(Function<ReactiveConnectionSynchronization, Publisher<Void>> callback,
                                               List<Throwable> failures) {
        return Mono.defer(() -> {
            if (connectionSynchronizations == null) {
                return EMPTY;
            }
            // A callback can register another synchronization
            List<ReactiveConnectionSynchronization> synchronizations = new ArrayList<>(connectionSynchronizations);
            Mono<Void> chain = EMPTY;
            ListIterator<ReactiveConnectionSynchronization> listIterator = synchronizations.listIterator(synchronizations.size());
            while (listIterator.hasPrevious()) {
                ReactiveConnectionSynchronization synchronization = listIterator.previous();
                chain = chain.then(collectFailure(() -> callback.apply(synchronization), failures));
            }
            return chain;
        });
    }

    /**
     * Runs a step when it is subscribed, after the previous step has finished. A failure of the step,
     * whether it is thrown when the publisher is created or signalled by the publisher, is collected.
     */
    private static Mono<Void> collectFailure(Supplier<Publisher<Void>> step, List<Throwable> failures) {
        return Mono.defer(() -> Mono.from(step.get())).onErrorResume(failure -> {
            failures.add(failure);
            return EMPTY;
        });
    }

    private static Throwable combine(List<Throwable> failures) {
        if (failures.size() == 1) {
            return failures.get(0);
        }
        IllegalStateException e = new IllegalStateException("Error executing connection synchronizations", failures.get(0));
        for (int i = 1; i < failures.size(); i++) {
            e.addSuppressed(failures.get(i));
        }
        return e;
    }

    /**
     * Adapts a {@link ConnectionSynchronization}, keeping its order.
     *
     * @param synchronization The synchronization
     */
    private record SynchronousConnectionSynchronization(
        ConnectionSynchronization synchronization) implements ReactiveConnectionSynchronization, Ordered {

        @Override
        public Publisher<Void> onComplete() {
            return Mono.defer(() -> {
                synchronization.executionComplete();
                return Mono.empty();
            });
        }

        @Override
        public Publisher<Void> onError(Throwable throwable) {
            // The same as for a synchronous connection, the completion is always invoked
            return onComplete();
        }

        @Override
        public Publisher<Void> onCancel() {
            return onComplete();
        }

        @Override
        public Publisher<Void> onClose() {
            return Mono.defer(() -> {
                synchronization.beforeClosed();
                return Mono.empty();
            });
        }

        @Override
        public Publisher<Void> afterClose() {
            return Mono.defer(() -> {
                synchronization.afterClosed();
                return Mono.empty();
            });
        }

        @Override
        public int getOrder() {
            return synchronization.getOrder();
        }
    }
}
