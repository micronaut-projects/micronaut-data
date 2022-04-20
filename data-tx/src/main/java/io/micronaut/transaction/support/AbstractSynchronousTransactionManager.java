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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;

/**
 * NOTICE: This is a fork of Spring's {@code AbstractPlatformTransactionManager} modernizing it
 * to use enums, Slf4j and decoupling from Spring.
 *
 * Abstract base class that implements standard transaction workflow,
 * serving as basis for concrete platform transaction managers.
 *
 * <p>This base class provides the following workflow handling:
 * <ul>
 * <li>determines if there is an existing transaction;
 * <li>applies the appropriate propagation behavior;
 * <li>suspends and resumes transactions if necessary;
 * <li>checks the rollback-only flag on commit;
 * <li>applies the appropriate modification on rollback
 * (actual rollback or setting rollback-only);
 * <li>triggers registered synchronization callbacks
 * (if transaction synchronization is active).
 * </ul>
 *
 * <p>Subclasses have to implement specific template methods for specific
 * states of a transaction, e.g.: begin, suspend, resume, commit, rollback.
 * The most important of them are abstract and must be provided by a concrete
 * implementation; for the rest, defaults are provided, so overriding is optional.
 *
 * <p>Transaction synchronization is a generic mechanism for registering callbacks
 * that get invoked at transaction completion time. This is mainly used internally
 * by the data access support classes for JDBC, Hibernate, JPA, etc when running
 * within a JTA transaction: They register resources that are opened within the
 * transaction for closing at transaction completion time, allowing e.g. for reuse
 * of the same Hibernate Session within the transaction. The same mechanism can
 * also be leveraged for custom synchronization needs in an application.
 *
 * <p>The state of this class is serializable, to allow for serializing the
 * transaction strategy along with proxies that carry a transaction interceptor.
 * It is up to subclasses if they wish to make their state to be serializable too.
 * They should implement the {@code java.io.Serializable} marker interface in
 * that case, and potentially a private {@code readObject()} method (according
 * to Java serialization rules) if they need to restore any transient state.
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 28.03.2003
 * @see #setTransactionSynchronization
 * @see TransactionSynchronizationManager
 * @param <T> The resource type
 */
@Internal
public abstract class AbstractSynchronousTransactionManager<T> extends AbstractSynchronousStateTransactionManager<T>
        implements SynchronousTransactionManager<T>, Serializable {

    /**
     * Get the transaction state key that should be used to store the state.
     * @return The key
     */
    @NotNull
    protected Object getStateKey() {
        return TransactionSynchronizationManager.DEFAULT_STATE_KEY;
    }

    /**
     * Return required current transaction state.
     *
     * @return The state
     */
    @NonNull
    protected SynchronousTransactionState getState() {
        SynchronousTransactionState synchronousTransactionState = TransactionSynchronizationManager.getSynchronousTransactionState(getStateKey());
        if (synchronousTransactionState == null) {
            throw new IllegalStateException("Transaction state is not initialized!");
        }
        return synchronousTransactionState;
    }

    /**
     * Find or create a new state.
     *
     * @return The state
     */
    @NonNull
    protected SynchronousTransactionState findOrCreateState() {
        return TransactionSynchronizationManager.getSynchronousTransactionStateOrCreate(getStateKey(), DefaultSynchronousTransactionState::new);
    }

    /**
     * Destroy the state.
     * @param state The state
     */
    @Override
    protected void doDestroyState(SynchronousTransactionState state) {
        TransactionSynchronizationManager.unbindSynchronousTransactionState(getStateKey());
    }

    @Override
    public <R> R execute(@NonNull TransactionDefinition definition, @NonNull TransactionCallback<T, R> callback) {
        return execute(findOrCreateState(), definition, callback);
    }

    @Override
    public <R> R executeRead(@NonNull TransactionCallback<T, R> callback)  {
        return executeRead(findOrCreateState(), callback);
    }

    @Override
    public <R> R executeWrite(@NonNull TransactionCallback<T, R> callback) {
        return executeWrite(findOrCreateState(), callback);
    }

    @Override
    @NonNull
    public final TransactionStatus<T> getTransaction(@Nullable TransactionDefinition definition)
            throws TransactionException {
        return getTransaction(findOrCreateState(), definition);
    }

    /**
     * Create a new TransactionStatus for the given arguments,
     * also initializing transaction synchronization as appropriate.
     * @see #newTransactionStatus
     * @see #prepareTransactionStatus
     * @param definition The definition
     * @param transaction The transaction object
     * @param newTransaction Is this is a new transaction
     * @param newSynchronization Is this a new synchronization
     * @param debug Is debug enabled
     * @param suspendedResources Any suspended resources
     * @return The status
     */
    protected final DefaultTransactionStatus<T> prepareTransactionStatus(
            TransactionDefinition definition,
            @Nullable Object transaction,
            boolean newTransaction,
            boolean newSynchronization,
            boolean debug,
            @Nullable Object suspendedResources) {

        return prepareTransactionStatus(getState(), definition, transaction, newTransaction, newSynchronization, debug, suspendedResources);
    }

    @Override
    protected DefaultTransactionStatus<T> newTransactionStatus(SynchronousTransactionState state,
                                                            TransactionDefinition definition,
                                                            Object transaction,
                                                            boolean newTransaction,
                                                            boolean newSynchronization,
                                                            boolean debug,
                                                            Object suspendedResources) {
        return newTransactionStatus(definition, transaction, newTransaction, newSynchronization, debug, suspendedResources);
    }

    /**
     * Create a TransactionStatus instance for the given arguments.
     *
     * @param definition The definition
     * @param transaction The transaction object
     * @param newTransaction Is this is a new transaction
     * @param newSynchronization Is this a new synchronization
     * @param debug Is debug enabled
     * @param suspendedResources Any suspended resources
     * @return The status
     */
    protected DefaultTransactionStatus<T> newTransactionStatus(
            TransactionDefinition definition,
            @Nullable Object transaction,
            boolean newTransaction,
            boolean newSynchronization,
            boolean debug,
            @Nullable Object suspendedResources) {
        return super.newTransactionStatus(getState(), definition, transaction, newTransaction, newSynchronization, debug, suspendedResources);
    }

    @Override
    protected T getConnection(SynchronousTransactionState state, Object transaction) {
        return getConnection(transaction);
    }

    /**
     * The connection for the given transaction object.
     * @param transaction The transaction
     * @return The connection.
     */
    @Nullable
    protected abstract T getConnection(Object transaction);

    @Override
    protected void prepareSynchronization(SynchronousTransactionState state, DefaultTransactionStatus<T> status, TransactionDefinition definition) {
        prepareSynchronization(status, definition);
    }

    /**
     * Initialize transaction synchronization as appropriate.
     *
     * @param status The status
     * @param definition The definition
     */
    protected void prepareSynchronization(@NonNull DefaultTransactionStatus<T> status, @NonNull TransactionDefinition definition) {
        super.prepareSynchronization(getState(), status, definition);
    }

    /**
     * Suspend the given transaction. Suspends transaction synchronization first,
     * then delegates to the {@code doSuspend} template method.
     * @param transaction the current transaction object
     * (or {@code null} to just suspend active synchronizations, if any)
     * @return an object that holds suspended resources
     * (or {@code null} if neither transaction nor synchronization active)
     * @see #doSuspend
     * @see #resume
     * @throws TransactionException Thrown if an error occurs suspending the transaction
     */
    @Nullable
    protected final SuspendedResourcesHolder suspend(@Nullable Object transaction) throws TransactionException {
        AbstractSynchronousStateTransactionManager.SuspendedResourcesHolder holder = suspend(getState(), transaction);
        if (holder == null) {
            return null;
        }
        return new SuspendedResourcesHolder(holder.suspendedResources,
                holder.suspendedSynchronizations,
                holder.name,
                holder.readOnly,
                holder.isolationLevel,
                holder.wasActive);
    }

    /**
     * Resume the given transaction. Delegates to the {@code doResume}
     * template method first, then resuming transaction synchronization.
     * @param transaction the current transaction object
     * @param resourcesHolder the object that holds suspended resources,
     * as returned by {@code suspend} (or {@code null} to just
     * resume synchronizations, if any)
     * @see #doResume
     * @see #suspend
     * @throws TransactionException Thrown if an error occurs resuming the transaction
     */
    protected final void resume(@Nullable Object transaction, @Nullable SuspendedResourcesHolder resourcesHolder)
            throws TransactionException {
        resume(getState(), transaction, resourcesHolder == null ? null : new AbstractSynchronousStateTransactionManager.SuspendedResourcesHolder(resourcesHolder.suspendedResources,
                resourcesHolder.suspendedSynchronizations,
                resourcesHolder.name,
                resourcesHolder.readOnly,
                resourcesHolder.isolationLevel,
                resourcesHolder.wasActive));
    }

    /**
     * This implementation of commit handles participating in existing
     * transactions and programmatic rollback requests.
     * Delegates to {@code isRollbackOnly}, {@code doCommit}
     * and {@code rollback}.
     * @see TransactionStatus#isRollbackOnly()
     * @see #doCommit
     * @see #rollback
     */
    @Override
    public final void commit(TransactionStatus<T> status) throws TransactionException {
        commit(getState(), status);
    }

    /**
     * This implementation of rollback handles participating in existing
     * transactions. Delegates to {@code doRollback} and
     * {@code doSetRollbackOnly}.
     * @see #doRollback
     * @see #doSetRollbackOnly
     */
    @Override
    public final void rollback(TransactionStatus<T> status) throws TransactionException {
        rollback(getState(), status);
    }

    /**
     * Trigger {@code beforeCommit} callbacks.
     * @param status object representing the transaction
     */
    protected final void triggerBeforeCommit(DefaultTransactionStatus<T> status) {
        triggerBeforeCommit(getState(), status);
    }

    /**
     * Trigger {@code beforeCompletion} callbacks.
     * @param status object representing the transaction
     */
    protected final void triggerBeforeCompletion(DefaultTransactionStatus<T> status) {
        triggerBeforeCompletion(getState(), status);
    }

    @Override
    protected Object doGetTransaction(@NonNull SynchronousTransactionState state) throws TransactionException {
        return doGetTransaction();
    }

    @Override
    protected boolean isExistingTransaction(@NonNull SynchronousTransactionState state, @NonNull Object transaction) throws TransactionException {
        return isExistingTransaction(transaction);
    }

    /**
     * Actually invoke the {@code afterCompletion} methods of the
     * given Spring TransactionSynchronization objects.
     * <p>To be called by this abstract manager itself, or by special implementations
     * of the {@code registerAfterCompletionWithExistingTransaction} callback.
     *
     * @param synchronizations a List of TransactionSynchronization objects
     * @param completionStatus the completion status according to the
     *                         constants in the TransactionSynchronization interface
     * @see #registerAfterCompletionWithExistingTransaction(SynchronousTransactionState, Object, List)
     * @see TransactionSynchronization.Status#COMMITTED
     * @see TransactionSynchronization.Status#ROLLED_BACK
     * @see TransactionSynchronization.Status#UNKNOWN
     */
    protected final void invokeAfterCompletion(List<TransactionSynchronization> synchronizations,
                                               TransactionSynchronization.Status completionStatus) {
        invokeAfterCompletion(getState(), synchronizations, completionStatus);
    }

    //---------------------------------------------------------------------
    // Template methods to be implemented in subclasses
    //---------------------------------------------------------------------

    /**
     * Return a transaction object for the current transaction state.
     * <p>The returned object will usually be specific to the concrete transaction
     * manager implementation, carrying corresponding transaction state in a
     * modifiable fashion. This object will be passed into the other template
     * methods (e.g. doBegin and doCommit), either directly or as part of a
     * DefaultTransactionStatus instance.
     * <p>The returned object should contain information about any existing
     * transaction, that is, a transaction that has already started before the
     * current {@code getTransaction} call on the transaction manager.
     * Consequently, a {@code doGetTransaction} implementation will usually
     * look for an existing transaction and store corresponding state in the
     * returned transaction object.
     * @return the current transaction object
     * @throws io.micronaut.transaction.exceptions.CannotCreateTransactionException
     * if transaction support is not available
     * @throws TransactionException in case of lookup or system errors
     * @see #doBegin
     * @see #doCommit
     * @see #doRollback
     * @see DefaultTransactionStatus#getTransaction
     */
    @NonNull
    protected abstract Object doGetTransaction() throws TransactionException;

    /**
     * Check if the given transaction object indicates an existing transaction
     * (that is, a transaction which has already started).
     * <p>The result will be evaluated according to the specified propagation
     * behavior for the new transaction. An existing transaction might get
     * suspended (in case of PROPAGATION_REQUIRES_NEW), or the new transaction
     * might participate in the existing one (in case of PROPAGATION_REQUIRED).
     * <p>The default implementation returns {@code false}, assuming that
     * participating in existing transactions is generally not supported.
     * Subclasses are of course encouraged to provide such support.
     * @param transaction transaction object returned by doGetTransaction
     * @return if there is an existing transaction
     * @throws TransactionException in case of system errors
     * @see #doGetTransaction
     */
    protected boolean isExistingTransaction(@NonNull Object transaction) throws TransactionException {
        return false;
    }

    @Override
    protected void registerAfterCompletionWithExistingTransaction(SynchronousTransactionState state,
                                                                  Object transaction,
                                                                  List<TransactionSynchronization> synchronizations) throws TransactionException {
        registerAfterCompletionWithExistingTransaction(transaction, synchronizations);
    }

    /**
     * Register the given list of transaction synchronizations with the existing transaction.
     * <p>Invoked when the control of the Spring transaction manager and thus all Spring
     * transaction synchronizations end, without the transaction being completed yet. This
     * is for example the case when participating in an existing JTA or EJB CMT transaction.
     * <p>The default implementation simply invokes the {@code afterCompletion} methods
     * immediately, passing in "STATUS_UNKNOWN". This is the best we can do if there's no
     * chance to determine the actual outcome of the outer transaction.
     * @param transaction transaction object returned by {@code doGetTransaction}
     * @param synchronizations a List of TransactionSynchronization objects
     * @throws TransactionException in case of system errors
     * @see #invokeAfterCompletion(List, TransactionSynchronization.Status)
     * @see TransactionSynchronization#afterCompletion(TransactionSynchronization.Status)
     * @see io.micronaut.transaction.support.TransactionSynchronization.Status#UNKNOWN
     */
    protected void registerAfterCompletionWithExistingTransaction(Object transaction,
                                                                  List<TransactionSynchronization> synchronizations) throws TransactionException {
        super.registerAfterCompletionWithExistingTransaction(getState(), transaction, synchronizations);
    }

    @Override
    protected void doCleanupAfterCompletion(SynchronousTransactionState state, Object transaction) {
        doCleanupAfterCompletion(transaction);
    }

    /**
     * Cleanup resources after transaction completion.
     * <p>Called after {@code doCommit} and {@code doRollback} execution,
     * on any outcome. The default implementation does nothing.
     * <p>Should not throw any exceptions but just issue warnings on errors.
     * @param transaction transaction object returned by {@code doGetTransaction}
     */
    protected void doCleanupAfterCompletion(Object transaction) {
    }

    //---------------------------------------------------------------------
    // Serialization support
    //---------------------------------------------------------------------

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Rely on default serialization; just initialize state after deserialization.
        ois.defaultReadObject();

        // Initialize transient fields.
        this.logger = LoggerFactory.getLogger(getClass());
    }

    /**
     * Holder for suspended resources.
     * Used internally by {@code suspend} and {@code resume}.
     */
    protected static final class SuspendedResourcesHolder {

        @Nullable
        private final Object suspendedResources;

        @Nullable
        private final List<TransactionSynchronization> suspendedSynchronizations;

        @Nullable
        private final String name;

        private final boolean readOnly;

        @Nullable
        private final TransactionDefinition.Isolation isolationLevel;

        private final boolean wasActive;

        private SuspendedResourcesHolder(
                @Nullable Object suspendedResources, List<TransactionSynchronization> suspendedSynchronizations,
                @Nullable String name, boolean readOnly, @Nullable TransactionDefinition.Isolation isolationLevel, boolean wasActive) {

            this.suspendedResources = suspendedResources;
            this.suspendedSynchronizations = suspendedSynchronizations;
            this.name = name;
            this.readOnly = readOnly;
            this.isolationLevel = isolationLevel;
            this.wasActive = wasActive;
        }
    }

}
