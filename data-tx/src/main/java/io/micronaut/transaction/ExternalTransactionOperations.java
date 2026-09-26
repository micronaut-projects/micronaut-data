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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.transaction.support.TransactionSynchronization;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Binds a connection whose transaction is owned by another component as an existing transaction.
 *
 * <p>The typical owner is a messaging session that also performs database work on the same
 * connection, for example an Oracle AQ JMS session that exposes its JDBC connection. While the
 * connection is bound, repository operations and {@code @Transactional} code managed by this
 * transaction manager join the external transaction:</p>
 *
 * <ul>
 *     <li>{@code REQUIRED}, {@code MANDATORY} and {@code SUPPORTS} participate and never commit;</li>
 *     <li>a failed participant marks the external transaction as rollback-only;</li>
 *     <li>{@code NESTED} uses savepoints on the bound connection;</li>
 *     <li>{@code REQUIRES_NEW} suspends the external transaction and uses a new connection.</li>
 * </ul>
 *
 * <p>This API never commits or rolls back. The transaction manager never commits, rolls back or
 * closes the bound connection, and never changes its auto-commit, read-only or isolation
 * settings. The owner commits or rolls back itself, inside the callback, and reports the
 * outcome.</p>
 *
 * <p>Binding is scoped to a callback on the current thread. The bound transaction must not be
 * captured by asynchronous or reactive work.</p>
 *
 * @param <C> The connection type
 * @author radovanradic
 * @since 5.3.0
 */
@Experimental
public interface ExternalTransactionOperations<C> {

    /**
     * Binds an externally owned connection as an existing transaction for the duration of the
     * callback, on the current thread.
     *
     * <p>The owner completes the transaction inside the callback: it calls
     * {@link ExternalTransaction#beforeCommit()} before committing, commits or rolls back itself,
     * and reports the outcome with
     * {@link ExternalTransaction#afterCompletion(TransactionSynchronization.Status)}. The
     * connection is unbound when the callback returns or throws.</p>
     *
     * @param connection The externally owned connection
     * @param callback The callback
     * @param <R> The result type
     * @return The callback result
     * @throws io.micronaut.transaction.exceptions.TransactionUsageException if a connection or
     * transaction of this transaction manager is already active on the current thread
     * @throws io.micronaut.transaction.exceptions.IllegalTransactionStateException if the callback
     * returns normally without reporting the outcome
     */
    <R extends @Nullable Object> R bindExternal(@NonNull C connection,
                                                @NonNull ExternalTransactionCallback<C, R> callback);

    /**
     * A bound external transaction.
     *
     * @param <C> The connection type
     */
    interface ExternalTransaction<C> {

        /**
         * @return The transaction status of the external transaction
         */
        @NonNull
        TransactionStatus<C> getStatus();

        /**
         * Runs the before-commit and before-completion synchronizations.
         *
         * @throws io.micronaut.transaction.exceptions.UnexpectedRollbackException if the
         * transaction has been marked as rollback-only; the owner must then roll back
         */
        void beforeCommit();

        /**
         * Reports the outcome of the owner's commit or rollback and runs the after-completion
         * synchronizations.
         *
         * <p>Report {@link TransactionSynchronization.Status#UNKNOWN} when the outcome cannot be
         * determined, for example when the owner's commit failed and the following rollback also
         * failed.</p>
         *
         * @param status {@code COMMITTED}, {@code ROLLED_BACK} or {@code UNKNOWN}
         */
        void afterCompletion(TransactionSynchronization.@NonNull Status status);
    }

    /**
     * A callback executed while an external connection is bound.
     *
     * @param <C> The connection type
     * @param <R> The result type
     */
    @FunctionalInterface
    interface ExternalTransactionCallback<C, R extends @Nullable Object> {

        /**
         * @param transaction The bound external transaction
         * @return The result
         * @throws Exception if the work fails
         */
        R call(@NonNull ExternalTransaction<C> transaction) throws Exception;
    }
}
