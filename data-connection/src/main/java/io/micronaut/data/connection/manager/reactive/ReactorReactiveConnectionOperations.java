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
package io.micronaut.data.connection.manager.reactive;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Optional;
import java.util.function.Function;

/**
 * Special version of {@link ReactiveConnectionOperations} that is exposing Reactor publishers.
 *
 * @param <C> The connection
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
public interface ReactorReactiveConnectionOperations<C> extends ReactiveConnectionOperations<C> {

    /**
     * Obtains the current required connection.
     *
     * @param contextView The reactor's context view
     * @return The connection or exception if the connection doesn't exist
     */
    @NonNull
    default ConnectionStatus<C> getConnectionStatus(@NonNull ContextView contextView) {
        return findConnectionStatus(contextView).orElseThrow(NoConnectionException::new);
    }

    /**
     * Obtains the current connection.
     *
     * @param contextView The reactor's context view
     * @return The optional connection
     */
    Optional<ConnectionStatus<C>> findConnectionStatus(@NonNull ContextView contextView);

    /**
     * Execute the given handler with a new transaction.
     *
     * @param definition The definition
     * @param handler    The handler
     * @param <T>        The emitted type
     * @return A publisher that emits the result type
     */
    @NonNull
    default <T> Flux<T> withConnectionFlux(@NonNull ConnectionDefinition definition, @NonNull Function<ConnectionStatus<C>, Flux<T>> handler) {
        return Flux.from(
            withConnection(definition, handler::apply)
        );
    }

    /**
     * Execute the given handler with a new connection.
     *
     * @param handler The handler
     * @param <T>     The emitted type
     * @return A publisher that emits the result type
     */
    @NonNull
    default <T> Flux<T> withConnectionFlux(@NonNull Function<ConnectionStatus<C>, Flux<T>> handler) {
        return withConnectionFlux(ConnectionDefinition.DEFAULT, handler);
    }

    /**
     * Execute the given handler with a new connection.
     *
     * @param definition The definition
     * @param handler    The handler
     * @param <T>        The emitted type
     * @return A publisher that emits the result type
     */
    @NonNull
    default <T> Mono<T> withConnectionMono(@NonNull ConnectionDefinition definition, @NonNull Function<ConnectionStatus<C>, Mono<T>> handler) {
        return Mono.from(
            withConnection(definition, handler::apply)
        );
    }

    /**
     * Execute the given handler with a new connection.
     *
     * @param handler The handler
     * @param <T>     The emitted type
     * @return A publisher that emits the result type
     */
    default <T> Mono<T> withConnectionMono(@NonNull Function<ConnectionStatus<C>, Mono<T>> handler) {
        return withConnectionMono(ConnectionDefinition.DEFAULT, handler);
    }

    @NonNull
    @Override
    default <T> Publisher<T> withConnection(@NonNull ConnectionDefinition definition, @NonNull Function<ConnectionStatus<C>, Publisher<T>> handler) {
        return withConnectionFlux(definition, connection -> Flux.from(handler.apply(connection)));
    }
}
