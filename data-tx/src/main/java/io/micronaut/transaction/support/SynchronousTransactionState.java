/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionState;

import java.util.List;

/**
 * The synchronous transaction state.
 * Extracted from {@link TransactionSynchronizationManager} to allow not thread-local state.
 *
 * @author Denis Stepanov
 * @since 3.4.0
 */
@Internal
public interface SynchronousTransactionState extends TransactionState {

    /**
     * Return if transaction synchronization is active for the current state.
     * Can be called before register to avoid unnecessary instance creation.
     *
     * @return True if a synchronization is active
     * @see #registerSynchronization
     */
    boolean isSynchronizationActive();

    /**
     * Activate transaction synchronization for the current state.
     * Called by a transaction manager on transaction begin.
     *
     * @throws IllegalStateException if synchronization is already active
     */
    void initSynchronization() throws IllegalStateException;

    /**
     * Register a new transaction synchronization for the current state.
     * Typically called by resource management code.
     * <p>Note that synchronizations can implement the
     * {@link io.micronaut.core.order.Ordered} interface.
     * They will be executed in an order according to their order value (if any).
     *
     * @param synchronization the synchronization object to register
     * @throws IllegalStateException if transaction synchronization is not active
     * @see io.micronaut.core.order.Ordered
     */
    void registerSynchronization(@NonNull TransactionSynchronization synchronization);

    /**
     * Return an unmodifiable snapshot list of all registered synchronizations
     * for the current state.
     *
     * @return unmodifiable List of TransactionSynchronization instances
     * @throws IllegalStateException if synchronization is not active
     * @see TransactionSynchronization
     */
    @NonNull
    List<TransactionSynchronization> getSynchronizations() throws IllegalStateException;

    /**
     * Deactivate transaction synchronization for the current state.
     * Called by the transaction manager on transaction cleanup.
     *
     * @throws IllegalStateException if synchronization is not active
     */
    void clearSynchronization() throws IllegalStateException;

    //-------------------------------------------------------------------------
    // Exposure of transaction characteristics
    //-------------------------------------------------------------------------

    /**
     * Expose the name of the current transaction, if any.
     * Called by the transaction manager on transaction begin and on cleanup.
     *
     * @param name the name of the transaction, or {@code null} to reset it
     * @see io.micronaut.transaction.TransactionDefinition#getName()
     */
    void setTransactionName(@Nullable String name);

    /**
     * Return the name of the current transaction, or {@code null} if none set.
     * To be called by resource management code for optimizations per use case,
     * for example to optimize fetch strategies for specific named transactions.
     *
     * @return The current transaction name
     * @see io.micronaut.transaction.TransactionDefinition#getName()
     */
    @Nullable
    String getTransactionName();

    /**
     * Expose a read-only flag for the current transaction.
     * Called by the transaction manager on transaction begin and on cleanup.
     *
     * @param readOnly {@code true} to mark the current transaction
     *                 as read-only; {@code false} to reset such a read-only marker
     * @see io.micronaut.transaction.TransactionDefinition#isReadOnly()
     */
    void setTransactionReadOnly(boolean readOnly);

    /**
     * Return whether the current transaction is marked as read-only.
     * To be called by resource management code when preparing a newly
     * created resource (for example, a Hibernate Session).
     * <p>Note that transaction synchronizations receive the read-only flag
     * as argument for the {@code beforeCommit} callback, to be able
     * to suppress change detection on commit. The present method is meant
     * to be used for earlier read-only checks, for example to set the
     * flush mode of a Hibernate Session to "FlushMode.NEVER" upfront.
     *
     * @return Whether the transaction is read only
     * @see io.micronaut.transaction.TransactionDefinition#isReadOnly()
     * @see TransactionSynchronization#beforeCommit(boolean)
     */
    boolean isTransactionReadOnly();

    /**
     * Expose an isolation level for the current transaction.
     * Called by the transaction manager on transaction begin and on cleanup.
     *
     * @param isolationLevel the isolation level to expose, according to the
     *                       JDBC Connection constants (equivalent to the corresponding
     *                       TransactionDefinition constants), or {@code null} to reset it
     * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
     * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
     * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
     * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
     * @see io.micronaut.transaction.TransactionDefinition.Isolation#READ_UNCOMMITTED
     * @see io.micronaut.transaction.TransactionDefinition.Isolation#READ_COMMITTED
     * @see io.micronaut.transaction.TransactionDefinition.Isolation#REPEATABLE_READ
     * @see io.micronaut.transaction.TransactionDefinition.Isolation#SERIALIZABLE
     * @see io.micronaut.transaction.TransactionDefinition#getIsolationLevel()
     */
    void setTransactionIsolationLevel(@Nullable TransactionDefinition.Isolation isolationLevel);

    /**
     * Return the isolation level for the current transaction, if any.
     * To be called by resource management code when preparing a newly
     * created resource (for example, a JDBC Connection).
     *
     * @return the currently exposed isolation level, according to the
     * JDBC Connection constants (equivalent to the corresponding
     * TransactionDefinition constants), or {@code null} if none
     * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
     * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
     * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
     * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
     * @see io.micronaut.transaction.TransactionDefinition.Isolation#READ_UNCOMMITTED
     * @see io.micronaut.transaction.TransactionDefinition.Isolation#READ_COMMITTED
     * @see io.micronaut.transaction.TransactionDefinition.Isolation#REPEATABLE_READ
     * @see io.micronaut.transaction.TransactionDefinition.Isolation#SERIALIZABLE
     * @see io.micronaut.transaction.TransactionDefinition#getIsolationLevel()
     */
    @Nullable
    TransactionDefinition.Isolation getTransactionIsolationLevel();

    /**
     * Expose whether there currently is an actual transaction active.
     * Called by the transaction manager on transaction begin and on cleanup.
     *
     * @param active {@code true} to mark the current state as being associated
     *               with an actual transaction; {@code false} to reset that marker
     */
    void setActualTransactionActive(boolean active);

    /**
     * Return whether there currently is an actual transaction active.
     * This indicates whether the current state is associated with an actual
     * transaction rather than just with active transaction synchronization.
     * <p>To be called by resource management code that wants to discriminate
     * between active transaction synchronization (with or without backing
     * resource transaction; also on PROPAGATION_SUPPORTS) and an actual
     * transaction being active (with backing resource transaction;
     * on PROPAGATION_REQUIRED, PROPAGATION_REQUIRES_NEW, etc).
     *
     * @return Whether a transaction is active
     * @see #isSynchronizationActive()
     */
    boolean isActualTransactionActive();

    /**
     * Clear the entire transaction synchronization state:
     * registered synchronizations as well as the various transaction characteristics.
     *
     * @see #clearSynchronization()
     * @see #setTransactionName
     * @see #setTransactionReadOnly
     * @see #setTransactionIsolationLevel
     * @see #setActualTransactionActive
     */
    void clear();

}
