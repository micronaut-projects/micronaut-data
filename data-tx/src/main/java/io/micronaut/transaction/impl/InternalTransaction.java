package io.micronaut.transaction.impl;

import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException;
import io.micronaut.transaction.support.TransactionSynchronization;

public interface InternalTransaction<T> extends TransactionStatus<T> {

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

    void triggerAfterCompletion(TransactionSynchronization.Status status);

    void releaseHeldSavepoint();

    void cleanupAfterCompletion();
}
