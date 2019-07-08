package io.micronaut.data.transaction;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Abstract transaction status object that allows access to a backing transaction resource.
 *
 * @author graemerocher
 * @since 1.0.0
 * @param <T>
 */
public interface TransactionStatus<T> {

    /**
     * @return The transaction resource.
     */
    @NonNull T getResource();

    /**
     * Return whether the present transaction is new; otherwise participating
     * in an existing transaction, or potentially not running in an actual
     * transaction in the first place.
     *
     * @return Whether this is a new transaction
     */
    boolean isNewTransaction();

    /**
     * Set the transaction rollback-only. This instructs the transaction manager
     * that the only possible outcome of the transaction may be a rollback, as
     * alternative to throwing an exception which would in turn trigger a rollback.
     */
    void setRollbackOnly();

    /**
     * Return whether the transaction has been marked as rollback-only
     * (either by the application or by the transaction infrastructure).
     *
     * @return Whether rollback has to been set
     */
    boolean isRollbackOnly();

    /**
     * Return whether this transaction is completed, that is,
     * whether it has already been committed or rolled back.
     *
     * @return Whether the transaction completed or not
     */
    boolean isCompleted();

}
