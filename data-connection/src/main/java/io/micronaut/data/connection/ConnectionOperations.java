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
package io.micronaut.data.connection;

import io.micronaut.core.annotation.Blocking;
import org.jspecify.annotations.NonNull;
import io.micronaut.data.connection.exceptions.NoConnectionException;

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
     * Executes the callback with a connection supplied by the caller as the current connection.
     *
     * <p>Repositories, connection-scoped operations and transactions started inside the callback use the
     * supplied connection, as they would use a connection opened by these operations. Use it when another
     * library owns a connection that Micronaut Data should take part in, such as the database connection of
     * an Oracle AQ JMS session or of an XA resource. A transaction started inside the callback commits or
     * rolls back the supplied connection; wrap the connection to suppress that when the owner commits.</p>
     *
     * <p>The connection is not set up (for example, read-only mode is left as it is) and is never closed:
     * the caller remains responsible for it.</p>
     *
     * @param connection The connection
     * @param definition The connection definition describing it
     * @param callback   The call back
     * @param <R>        The result
     * @return The result
     * @since 5.3.0
     */
    default <R> R executeWithConnection(@NonNull C connection,
                                        @NonNull ConnectionDefinition definition,
                                        @NonNull Function<ConnectionStatus<C>, R> callback) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support externally supplied connections");
    }

    /**
     * Execute a read-only connection within the context of the function.
     * NOTE: The connection manager might not support the read only connection!
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

    /**
     * Determine whether the given connection status refers to a connection
     * managed by this {@link ConnectionOperations} instance.
     *
     * @param connectionStatus The connection status to verify
     * @return true if the connection is managed (i.e. created/supplied) by this operations instance
     * @since 5.0
     */
    boolean managesConnection(@NonNull ConnectionStatus<C> connectionStatus);
}
