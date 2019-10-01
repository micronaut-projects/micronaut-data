/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.transaction.support;


import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.transaction.SavepointManager;
import io.micronaut.transaction.exceptions.NestedTransactionNotSupportedException;

import java.util.function.Supplier;

/**
 * Default implementation of the {@link io.micronaut.transaction.TransactionStatus}
 * interface, used by {@link AbstractSynchronousTransactionManager}. Based on the concept
 * of an underlying "transaction object".
 *
 * <p>Holds all status information that {@link AbstractSynchronousTransactionManager}
 * needs internally, including a generic transaction object determined by the
 * concrete transaction manager implementation.
 *
 * <p>Supports delegating savepoint-related methods to a transaction object
 * that implements the {@link SavepointManager} interface.
 *
 * <p><b>NOTE:</b> This is <i>not</i> intended for use with other PlatformTransactionManager
 * implementations, in particular not for mock transaction managers in testing environments.
 * {@link io.micronaut.transaction.TransactionStatus} interface instead.
 *
 * @author Juergen Hoeller
 * @since 19.01.2004
 * @see AbstractSynchronousTransactionManager
 * @see SavepointManager
 * @see #getTransaction
 * @see #createSavepoint
 * @see #rollbackToSavepoint
 * @see #releaseSavepoint
 * @param <T> The transaction object type
 */
public class DefaultTransactionStatus<T> extends AbstractTransactionStatus<T> {

    @Nullable
    private final Object transaction;

    private final boolean newTransaction;

    private final boolean newSynchronization;

    private final boolean readOnly;

    private final boolean debug;

    @Nullable
    private final Object suspendedResources;

    private final Supplier<T> connectionSupplier;

    /**
     * Create a new {@code DefaultTransactionStatus} instance.
     * @param transaction underlying transaction object that can hold state
     * for the internal transaction implementation
     * @param newTransaction if the transaction is new, otherwise participating
     * in an existing transaction
     * @param newSynchronization if a new transaction synchronization has been
     * opened for the given transaction
     * @param readOnly whether the transaction is marked as read-only
     * @param debug should debug logging be enabled for the handling of this transaction?
     * Caching it in here can prevent repeated calls to ask the logging system whether
     * debug logging should be enabled.
     * @param suspendedResources a holder for resources that have been suspended
     * for this transaction, if any
     */
    public DefaultTransactionStatus(
            @Nullable Object transaction, @NonNull Supplier<T> connectionSupplier, boolean newTransaction, boolean newSynchronization,
            boolean readOnly, boolean debug, @Nullable Object suspendedResources) {

        this.transaction = transaction;
        this.connectionSupplier = connectionSupplier;
        this.newTransaction = newTransaction;
        this.newSynchronization = newSynchronization;
        this.readOnly = readOnly;
        this.debug = debug;
        this.suspendedResources = suspendedResources;
    }

    @NonNull
    @Override
    public T getConnection() {
        return connectionSupplier.get();
    }

    /**
     * Return the underlying transaction object.
     * @throws IllegalStateException if no transaction is active
     * @return The underlying transaction
     */
    public Object getTransaction() {
        if (this.transaction == null) {
            throw new IllegalStateException("No transaction active");
        }
        return this.transaction;
    }

    /**
     * Return whether there is an actual transaction active.
     *
     * @return Does the status have a transaction
     */
    public boolean hasTransaction() {
        return (this.transaction != null);
    }

    @Override
    public boolean isNewTransaction() {
        return (hasTransaction() && this.newTransaction);
    }

    /**
     * Return if a new transaction synchronization has been opened
     * for this transaction.
     *
     * @return Is it a new synchronization
     */
    public boolean isNewSynchronization() {
        return this.newSynchronization;
    }

    /**
     * Return if this transaction is defined as read-only transaction.
     *
     * @return Is the transaction read only
     */
    public boolean isReadOnly() {
        return this.readOnly;
    }

    /**
     * Return whether the progress of this transaction is debugged. This is used by
     * {@link AbstractSynchronousTransactionManager} as an optimization, to prevent repeated
     * calls to {@code logger.isDebugEnabled()}. Not really intended for client code.
     *
     * @return Is debug enabled
     */
    public boolean isDebug() {
        return this.debug;
    }

    /**
     * Return the holder for resources that have been suspended for this transaction,
     * if any.
     * @return The suspended resources
     */
    @Nullable
    public Object getSuspendedResources() {
        return this.suspendedResources;
    }


    //---------------------------------------------------------------------
    // Enable functionality through underlying transaction object
    //---------------------------------------------------------------------

    /**
     * Determine the rollback-only flag via checking the transaction object, provided
     * that the latter implements the {@link SmartTransactionObject} interface.
     * <p>Will return {@code true} if the global transaction itself has been marked
     * rollback-only by the transaction coordinator, for example in case of a timeout.
     * @see SmartTransactionObject#isRollbackOnly()
     */
    @Override
    public boolean isGlobalRollbackOnly() {
        return ((this.transaction instanceof SmartTransactionObject) &&
                ((SmartTransactionObject) this.transaction).isRollbackOnly());
    }

    /**
     * This implementation exposes the {@link SavepointManager} interface
     * of the underlying transaction object, if any.
     * @throws NestedTransactionNotSupportedException if savepoints are not supported
     * @see #isTransactionSavepointManager()
     */
    @Override
    protected SavepointManager getSavepointManager() {
        Object transaction = this.transaction;
        if (!(transaction instanceof SavepointManager)) {
            throw new NestedTransactionNotSupportedException(
                    "Transaction object [" + this.transaction + "] does not support savepoints");
        }
        return (SavepointManager) transaction;
    }

    /**
     * Return whether the underlying transaction implements the {@link SavepointManager}
     * interface and therefore supports savepoints.
     * @see #getTransaction()
     * @see #getSavepointManager()
     * @return Is the transaction a save point manager
     */
    public boolean isTransactionSavepointManager() {
        return (this.transaction instanceof SavepointManager);
    }

    /**
     * Delegate the flushing to the transaction object, provided that the latter
     * implements the {@link SmartTransactionObject} interface.
     * @see SmartTransactionObject#flush()
     */
    @Override
    public void flush() {
        if (this.transaction instanceof SmartTransactionObject) {
            ((SmartTransactionObject) this.transaction).flush();
        }
    }

}
