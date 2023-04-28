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
package io.micronaut.data.connection.manager.synchronous;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.exceptions.NoConnectionException;
import io.micronaut.data.connection.manager.ConnectionDefinition;

import java.util.Optional;
import java.util.function.Function;

/***
 * The synchronous connection operations interface.
 *
 * @param <C> The connection type.
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Blocking
public interface ConnectionOperations<C> {

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
     * Execute a connection within the context of the function.
     *
     * @param definition The connection definition
     * @param callback   The call back
     * @param <R>        The result
     * @return The result
     */
    <R> R execute(@NonNull ConnectionDefinition definition, @NonNull Function<ConnectionStatus<C>, R> callback);

    /**
     * Execute a read-only connection within the context of the function.
     *
     * @param callback The call back
     * @param <R>      The result
     * @return The result
     */
    default <R> R executeRead(@NonNull Function<ConnectionStatus<C>, R> callback) {
        return execute(ConnectionDefinition.READ_ONLY, callback);
    }

    /**
     * Execute a write supported connection within the context of the function.
     *
     * @param callback The call back
     * @param <R>      The result
     * @return The result
     */
    default <R> R executeWrite(@NonNull Function<ConnectionStatus<C>, R> callback) {
        return execute(ConnectionDefinition.DEFAULT, callback);
    }
}
