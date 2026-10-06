/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.connection.support;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.reactive.DefaultReactiveConnectionStatus;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The reactive MongoDB connection operations implementation.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class AbstractReactorConnectionOperations<C> implements ReactorConnectionOperations<C> {

    /**
     * A reused connection is closed by the scope that opened it.
     */
    private static final Supplier<Publisher<Void>> NO_CLOSE = Mono::empty;

    /**
     * Open a new connection.
     *
     * @param definition The connection definition
     * @return new connection publisher
     */
    @NonNull
    protected abstract Publisher<C> openConnection(@NonNull ConnectionDefinition definition);

    /**
     * Close the connection.
     *
     * @param connection The connection
     * @param definition The connection definition
     * @return closed publisher
     */
    @NonNull
    protected abstract Publisher<Void> closeConnection(@NonNull C connection, @NonNull ConnectionDefinition definition);

    /**
     * Returns the executor a connection that was just opened must be used on, for a connection bound to a thread.
     * It is invoked when the connection is emitted by {@link #openConnection(ConnectionDefinition)}, so an implementation
     * can capture the opening thread. Operations on the connection, including closing it, run on the executor.
     *
     * @param connection The connection
     * @param definition The connection definition
     * @return The executor, or {@code null} if the connection can be used from any thread
     * @since 5.3.0
     */
    @Nullable
    protected Executor connectionExecutor(@NonNull C connection, @NonNull ConnectionDefinition definition) {
        return null;
    }

    @Override
    public boolean managesConnection(ConnectionStatus<C> connectionStatus) {
        if (connectionStatus instanceof DefaultReactiveConnectionStatus<C> reactiveConnectionStatus) {
            return reactiveConnectionStatus.isConnectionOf(this);
        }
        return false;
    }

    @Override
    public final Optional<ConnectionStatus<C>> findConnectionStatus(@NonNull ContextView contextView) {
        return ReactorPropagation.findAllContextElements(contextView, ConnectionStatus.class)
            .filter(e -> managesConnection(e))
            .map(status -> (ConnectionStatus<C>) status)
            .findFirst();
    }

    @NonNull
    @Override
    public <T> Flux<T> withConnectionFlux(@NonNull ConnectionDefinition definition,
                                          @NonNull Function<ConnectionStatus<C>, Flux<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Flux.deferContextual(contextView -> {
            ConnectionStatus<C> existing = findConnectionStatus(contextView).orElse(null);
            if (existing != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY ->
                        existingConnectionFlux(definition, callback, existing);
                    case REQUIRES_NEW -> openConnectionFlux(definition, callback);
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> openConnectionFlux(definition, callback);
                case MANDATORY -> throw noConnectionFound();
            };
        });
    }

    private <T> Flux<T> existingConnectionFlux(ConnectionDefinition definition, Function<ConnectionStatus<C>, Flux<T>> callback, ConnectionStatus<C> existing) {
        // Complete the status of the reused connection, so its synchronizations run when the callback ends
        return Flux.usingWhen(
            Mono.fromSupplier(() -> existingConnectionStatus(definition, existing)),
            connectionStatus -> applyCallbackFlux(callback, connectionStatus),
            connectionStatus -> connectionStatus.onComplete(NO_CLOSE),
            (connectionStatus, throwable) -> connectionStatus.onError(throwable, NO_CLOSE),
            connectionStatus -> connectionStatus.onCancel(NO_CLOSE)
        );
    }

    private <T> Flux<T> openConnectionFlux(ConnectionDefinition definition, Function<ConnectionStatus<C>, Flux<T>> callback) {
        return Flux.usingWhen(
            Mono.from(openConnection(definition)).map(connection -> newConnectionStatus(connection, definition)),
            connectionStatus -> applyCallbackFlux(callback, connectionStatus).contextWrite(ctx -> addClientSession(ctx, connectionStatus)),
            connectionStatus -> connectionStatus.onComplete(closer(connectionStatus)),
            (connectionStatus, throwable) -> connectionStatus.onError(throwable, closer(connectionStatus)),
            connectionStatus -> connectionStatus.onCancel(closer(connectionStatus))
        );
    }

    @NonNull
    @Override
    public <T> Mono<T> withConnectionMono(@NonNull ConnectionDefinition definition,
                                          @NonNull Function<ConnectionStatus<C>, Mono<T>> callback) {
        Objects.requireNonNull(callback, "Callback cannot be null");
        return Mono.deferContextual(contextView -> {
            ConnectionStatus<C> existing = findConnectionStatus(contextView).orElse(null);
            if (existing != null) {
                return switch (definition.getPropagationBehavior()) {
                    case REQUIRED, MANDATORY ->
                        existingConnectionMono(definition, callback, existing);
                    case REQUIRES_NEW -> openConnectionMono(definition, callback);
                };
            }
            return switch (definition.getPropagationBehavior()) {
                case REQUIRED, REQUIRES_NEW -> openConnectionMono(definition, callback);
                case MANDATORY -> throw noConnectionFound();
            };
        });
    }

    private <T> Mono<T> existingConnectionMono(ConnectionDefinition definition, Function<ConnectionStatus<C>, Mono<T>> callback, ConnectionStatus<C> existing) {
        // Complete the status of the reused connection, so its synchronizations run when the callback ends
        return Mono.usingWhen(
            Mono.fromSupplier(() -> existingConnectionStatus(definition, existing)),
            connectionStatus -> applyCallbackMono(callback, connectionStatus),
            connectionStatus -> connectionStatus.onComplete(NO_CLOSE),
            (connectionStatus, throwable) -> connectionStatus.onError(throwable, NO_CLOSE),
            connectionStatus -> connectionStatus.onCancel(NO_CLOSE)
        );
    }

    private DefaultReactiveConnectionStatus<C> existingConnectionStatus(ConnectionDefinition definition, ConnectionStatus<C> existing) {
        return new DefaultReactiveConnectionStatus<>(existing.getConnection(), definition, this, false, executorOf(existing));
    }

    private <T> Mono<T> openConnectionMono(ConnectionDefinition definition, Function<ConnectionStatus<C>, Mono<T>> callback) {
        return Mono.usingWhen(
            Mono.from(openConnection(definition)).map(connection -> newConnectionStatus(connection, definition)),
            connectionStatus -> applyCallbackMono(callback, connectionStatus).contextWrite(ctx -> addClientSession(ctx, connectionStatus)),
            connectionStatus -> connectionStatus.onComplete(closer(connectionStatus)),
            (connectionStatus, throwable) -> connectionStatus.onError(throwable, closer(connectionStatus)),
            connectionStatus -> connectionStatus.onCancel(closer(connectionStatus))
        );
    }

    private DefaultReactiveConnectionStatus<C> newConnectionStatus(C connection, ConnectionDefinition definition) {
        return new DefaultReactiveConnectionStatus<>(connection, definition, this, true, connectionExecutor(connection, definition));
    }

    private Supplier<Publisher<Void>> closer(DefaultReactiveConnectionStatus<C> connectionStatus) {
        return () -> connectionStatus.onConnectionExecutor(() -> Mono.from(closeConnection(connectionStatus.getConnection(), connectionStatus.getDefinition())));
    }

    @Nullable
    private static <C> Executor executorOf(ConnectionStatus<C> connectionStatus) {
        return connectionStatus instanceof DefaultReactiveConnectionStatus<C> status ? status.getExecutor() : null;
    }

    private NoConnectionException noConnectionFound() {
        return new NoConnectionException("No existing connection found for connection marked with propagation 'mandatory'");
    }

    @NonNull
    private Context addClientSession(@NonNull Context context, @NonNull ConnectionStatus<C> status) {
        return ReactorPropagation.addContextElement(
            context,
            status
        );
    }

    private <T> Flux<T> applyCallbackFlux(Function<ConnectionStatus<C>, Flux<T>> callback, DefaultReactiveConnectionStatus<C> connectionStatus) {
        try {
            return callback.apply(connectionStatus);
        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    private <T> Mono<T> applyCallbackMono(Function<ConnectionStatus<C>, Mono<T>> callback, DefaultReactiveConnectionStatus<C> connectionStatus) {
        try {
            return callback.apply(connectionStatus);
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

}
