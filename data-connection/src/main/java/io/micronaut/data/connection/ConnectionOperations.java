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
     * Executes the callback with a connection supplied by the caller as the current connection, with transactions
     * started inside the callback controlled by Micronaut Data. Equivalent to
     * {@link #executeWithConnection(Object, ConnectionDefinition, boolean, Function)} with
     * {@code ownerControlsTransaction} set to {@code false}.
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
        return executeWithConnection(connection, definition, false, callback);
    }

    /**
     * Executes the callback with a connection supplied by the caller as the current connection.
     *
     * <p>Repositories, connection-scoped operations and transactions started inside the callback use the
     * supplied connection, as they would use a connection opened by these operations, even when the caller is
     * inside a transaction on another connection: a transaction started inside the callback never joins that
     * transaction. Use it when another library owns a connection that Micronaut Data should take part in, such
     * as the database connection of an Oracle AQ JMS session or of an XA resource.</p>
     *
     * <p>When {@code ownerControlsTransaction} is {@code true}, the connection must not be in auto-commit mode:
     * with auto-commit on there is no owner transaction to join, so every statement commits as it runs and the
     * owner cannot roll it back. The owner of the connection controls its
     * transaction, as with an AQ session, an OKafka producer or an XA resource, and transactions started inside
     * the callback join it. They don't change the auto-commit mode or the isolation level, and they don't commit
     * or roll back the connection. A {@code NESTED} transaction uses a savepoint, and {@code REQUIRES_NEW} opens
     * a separate connection. When joined work rolls back, the exception propagates, or this method throws a
     * {@link io.micronaut.data.connection.exceptions.ConnectionException} if the exception was handled inside the
     * callback, so that the owner rolls back its transaction; transactions joined after such a rollback fail
     * with an {@code UnexpectedRollbackException}. Micronaut Data does not see the owner's commit, so transaction
     * synchronizations, such as {@code @TransactionalEventListener} methods and {@code afterCommit} callbacks,
     * cannot be registered on a joined transaction and fail with a {@code TransactionUsageException}. Hibernate
     * transactions cannot join the owner's transaction either, because Hibernate writes need a Hibernate
     * transaction of their own, so they also fail with a {@code TransactionUsageException}: use JDBC
     * repositories on such a connection.</p>
     *
     * <p>When {@code ownerControlsTransaction} is {@code false}, transactions started inside the callback begin,
     * commit and roll back on the supplied connection, and restore its auto-commit mode afterwards.</p>
     *
     * <p>Connection operations that cannot adopt a connection, such as the Spring ones, throw
     * {@link UnsupportedOperationException}. The JDBC and Hibernate connection operations support it; for
     * Hibernate, the work runs on a session opened on the supplied connection.</p>
     *
     * <p>The connection is not set up (for example, read-only mode is left as it is), no request boundaries are
     * marked on it and it is never closed: the caller remains responsible for it, including Oracle request
     * boundaries and DRCP release.</p>
     *
     * @param connection               The connection
     * @param definition               The connection definition describing it
     * @param ownerControlsTransaction Whether the owner of the connection controls its transaction, so that
     *                                 transactions inside the callback join it
     * @param callback                 The call back
     * @param <R>                      The result
     * @return The result
     * @since 5.3.0
     */
    default <R> R executeWithConnection(@NonNull C connection,
                                        @NonNull ConnectionDefinition definition,
                                        boolean ownerControlsTransaction,
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
