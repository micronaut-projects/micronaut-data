/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.transaction.support;


import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.SavepointManager;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.NestedTransactionNotSupportedException;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.exceptions.TransactionUsageException;

/**
 * Abstract base implementation of the
 * {@link io.micronaut.transaction.TransactionStatus} interface.
 *
 * <p>Pre-implements the handling of local rollback-only and completed flags, and
 * delegation to an underlying {@link io.micronaut.transaction.SavepointManager}.
 * Also offers the option of a holding a savepoint within the transaction.
 *
 * <p>Does not assume any specific internal transaction handling, such as an
 * underlying transaction object, and no transaction synchronization mechanism.
 *
 * @author Juergen Hoeller
 * @since 1.2.3
 * @see #setRollbackOnly()
 * @see #isRollbackOnly()
 * @see #setCompleted()
 * @see #isCompleted()
 * @see #getSavepointManager()
 * @see DefaultTransactionStatus
 * @param <T> The connection type
 */
public abstract class AbstractTransactionStatus<T> implements TransactionStatus<T> {

    private boolean rollbackOnly = false;

    private boolean completed = false;

    @Nullable
    private Object savepoint;


    //---------------------------------------------------------------------
    // Implementation of TransactionExecution
    //---------------------------------------------------------------------

    @Override
    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    /**
     * Determine the rollback-only flag via checking both the local rollback-only flag
     * of this TransactionStatus and the global rollback-only flag of the underlying
     * transaction, if any.
     * @see #isLocalRollbackOnly()
     * @see #isGlobalRollbackOnly()
     */
    @Override
    public boolean isRollbackOnly() {
        return (isLocalRollbackOnly() || isGlobalRollbackOnly());
    }

    /**
     * Determine the rollback-only flag via checking this TransactionStatus.
     * <p>Will only return "true" if the application called {@code setRollbackOnly}
     * on this TransactionStatus object.
     *
     * @return Whether is local rollback
     */
    public boolean isLocalRollbackOnly() {
        return this.rollbackOnly;
    }

    /**
     * Template method for determining the global rollback-only flag of the
     * underlying transaction, if any.
     * <p>This implementation always returns {@code false}.
     *
     * @return Whether is global rollback
     */
    public boolean isGlobalRollbackOnly() {
        return false;
    }

    /**
     * Mark this transaction as completed, that is, committed or rolled back.
     */
    public void setCompleted() {
        this.completed = true;
    }

    @Override
    public boolean isCompleted() {
        return this.completed;
    }


    //---------------------------------------------------------------------
    // Handling of current savepoint state
    //---------------------------------------------------------------------

    @Override
    public boolean hasSavepoint() {
        return (this.savepoint != null);
    }

    /**
     * Set a savepoint for this transaction. Useful for PROPAGATION_NESTED.
     * @see io.micronaut.transaction.TransactionDefinition.Propagation#NESTED
     * @param savepoint  The save point
     */
    protected void setSavepoint(@Nullable Object savepoint) {
        this.savepoint = savepoint;
    }

    /**
     * @return Get the savepoint for this transaction, if any.
     */
    @Nullable
    protected Object getSavepoint() {
        return this.savepoint;
    }

    /**
     * Create a savepoint and hold it for the transaction.
     * @throws NestedTransactionNotSupportedException
     * if the underlying transaction does not support savepoints
     * @throws TransactionException if an error occurs creating the save point
     */
    public void createAndHoldSavepoint() throws TransactionException {
        setSavepoint(getSavepointManager().createSavepoint());
    }

    /**
     * Roll back to the savepoint that is held for the transaction
     * and release the savepoint right afterwards.
     * @throws TransactionException if an error occurs rolling back to savepoint
     */
    public void rollbackToHeldSavepoint() throws TransactionException {
        Object savepoint = getSavepoint();
        if (savepoint == null) {
            throw new TransactionUsageException(
                    "Cannot roll back to savepoint - no savepoint associated with current transaction");
        }
        getSavepointManager().rollbackToSavepoint(savepoint);
        getSavepointManager().releaseSavepoint(savepoint);
        setSavepoint(null);
    }

    /**
     * Release the savepoint that is held for the transaction.
     * @throws TransactionException if an error occurs releasing the save point
     */
    public void releaseHeldSavepoint() throws TransactionException {
        Object savepoint = getSavepoint();
        if (savepoint == null) {
            throw new TransactionUsageException(
                    "Cannot release savepoint - no savepoint associated with current transaction");
        }
        getSavepointManager().releaseSavepoint(savepoint);
        setSavepoint(null);
    }


    //---------------------------------------------------------------------
    // Implementation of SavepointManager
    //---------------------------------------------------------------------

    /**
     * This implementation delegates to a SavepointManager for the
     * underlying transaction, if possible.
     * @see #getSavepointManager()
     * @see SavepointManager#createSavepoint()
     */
    @Override
    public Object createSavepoint() throws TransactionException {
        return getSavepointManager().createSavepoint();
    }

    /**
     * This implementation delegates to a SavepointManager for the
     * underlying transaction, if possible.
     * @see #getSavepointManager()
     * @see SavepointManager#rollbackToSavepoint(Object)
     */
    @Override
    public void rollbackToSavepoint(Object savepoint) throws TransactionException {
        getSavepointManager().rollbackToSavepoint(savepoint);
    }

    /**
     * This implementation delegates to a SavepointManager for the
     * underlying transaction, if possible.
     * @see #getSavepointManager()
     * @see SavepointManager#releaseSavepoint(Object)
     */
    @Override
    public void releaseSavepoint(Object savepoint) throws TransactionException {
        getSavepointManager().releaseSavepoint(savepoint);
    }

    /**
     * Return a SavepointManager for the underlying transaction, if possible.
     * <p>Default implementation always throws a NestedTransactionNotSupportedException.
     * @throws NestedTransactionNotSupportedException
     * if the underlying transaction does not support savepoints
     * @return The save point manager
     */
    protected @NonNull SavepointManager getSavepointManager() {
        throw new NestedTransactionNotSupportedException("This transaction does not support savepoints");
    }


    //---------------------------------------------------------------------
    // Flushing support
    //---------------------------------------------------------------------

    /**
     * This implementations is empty, considering flush as a no-op.
     */
    @Override
    public void flush() {
    }

}
