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
package io.micronaut.transaction.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException;
import io.micronaut.transaction.support.TransactionResourceCommit;
import io.micronaut.transaction.support.TransactionSynchronization;
import org.jspecify.annotations.NonNull;

/**
 * The internal transaction representation.
 *
 * @param <T> The transaction type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public interface InternalTransaction<T> extends TransactionStatus<T> {

    /**
     * Check if the current TX is nested.
     * @return true if is nested transaction
     * @since 4.1.0
     */
    boolean isNestedTransaction();

    /**
     * Determine the rollback-only flag via checking this TransactionStatus.
     * <p>Will only return "true" if the application called {@code setRollbackOnly}
     * on this TransactionStatus object.
     *
     * @return Whether is local rollback
     */
    boolean isLocalRollbackOnly();

    /**
     * Template method for determining the global rollback-only flag of the
     * underlying transaction, if any.
     * <p>This implementation always returns {@code false}.
     *
     * @return Whether is global rollback
     */
    boolean isGlobalRollbackOnly();

    default void suspend() {
        throw new TransactionSuspensionNotSupportedException(
            "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }

    default void resume() {
        throw new TransactionSuspensionNotSupportedException(
            "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }

    void triggerBeforeCommit();

    void triggerAfterCommit();

    void triggerBeforeCompletion();

    /**
     * Registers vendor-specific work for the resource commit boundary.
     *
     * @param resourceCommit The resource commit callback
     */
    void registerResourceCommit(TransactionResourceCommit resourceCommit);

    /**
     * Runs a registered resource commit action.
     *
     * @return {@code true} when a resource commit action was registered and executed
     */
    boolean triggerResourceCommit();

    void triggerAfterCompletion(TransactionSynchronization.Status status);

    /**
     * Final cleanup after the transaction completed, always invoked even if an earlier
     * completion step failed. Runs the connection synchronizations bound to this transaction,
     * see {@link #bindConnectionSynchronizationsToTransaction()}.
     */
    void cleanupAfterCompletion();

    /**
     * Registers a synchronization restoring the connection state changed by this transaction.
     * By default, the synchronization is registered on the connection status; if the transaction
     * reuses a connection owned by an outer scope, it is executed at {@link #cleanupAfterCompletion()}.
     *
     * @param synchronization The synchronization
     * @since 5.3.0
     */
    default void registerConnectionSynchronization(@NonNull ConnectionSynchronization synchronization) {
        getConnectionStatus().registerSynchronization(synchronization);
    }

    /**
     * Binds the synchronizations registered by {@link #registerConnectionSynchronization(ConnectionSynchronization)}
     * to this transaction. Used when the transaction is started on a connection owned by an outer scope.
     *
     * @since 5.3.0
     */
    default void bindConnectionSynchronizationsToTransaction() {
    }

    /**
     * The variation of {@link #registerSynchronization(TransactionSynchronization)} that is always executed on the current TX invocation.
     * The ordinary {@link #registerSynchronization(TransactionSynchronization)} will always bound the synchronization to the TX in progress.
     * @param synchronization The synchronization
     */
    void registerInvocationSynchronization(@NonNull TransactionSynchronization synchronization);
}
