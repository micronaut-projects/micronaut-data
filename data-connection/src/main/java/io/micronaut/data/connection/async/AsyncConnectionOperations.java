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
package io.micronaut.data.connection.async;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.exceptions.NoConnectionException;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * An interface for async connection manager.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface AsyncConnectionOperations<C> {

    /**
     * Obtains the current required connection.
     *
     * @return The connection or exception if the connection doesn't exist
     */
    @NonNull
    default ConnectionStatus<C> getConnectionStatus() {
        return findConnectionStatus().orElseThrow(NoConnectionException::new);
    }

    /**
     * Obtains the current connection.
     *
     * @return The optional connection
     */
    Optional<ConnectionStatus<C>> findConnectionStatus();

    /**
     * Execute the given handler with a new connection.
     *
     * @param definition The definition
     * @param handler    The handler
     * @param <T>        The emitted type
     * @return A publisher that emits the result type
     */
    @NonNull
    <T> CompletionStage<T> withConnection(@NonNull ConnectionDefinition definition,
                                          @NonNull Function<ConnectionStatus<C>, CompletionStage<T>> handler);

    /**
     * Execute the given handler with a new connection.
     *
     * @param handler The handler
     * @param <T>     The emitted type
     * @return A publisher that emits the result type
     */
    default @NonNull <T> CompletionStage<T> withConnection(@NonNull Function<ConnectionStatus<C>, CompletionStage<T>> handler) {
        return withConnection(ConnectionDefinition.DEFAULT, handler);
    }
}
