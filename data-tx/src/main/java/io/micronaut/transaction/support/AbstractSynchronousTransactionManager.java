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
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.IllegalTransactionStateException;
import io.micronaut.transaction.exceptions.InvalidTimeoutException;
import io.micronaut.transaction.exceptions.NestedTransactionNotSupportedException;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.exceptions.TransactionSuspensionNotSupportedException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.exceptions.UnexpectedRollbackException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

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
@SuppressWarnings("serial")
public abstract class AbstractSynchronousTransactionManager<T> implements SynchronousTransactionManager<T>, Serializable {

    /**
     * Transaction synchronization behaviour.
     */
    enum Synchronization {
        /**
         * Always activate transaction synchronization, even for "empty" transactions
         * that result from PROPAGATION_SUPPORTS with no existing backend transaction.
         * @see io.micronaut.transaction.TransactionDefinition.Propagation#SUPPORTS
         * @see io.micronaut.transaction.TransactionDefinition.Propagation#NOT_SUPPORTED
         * @see io.micronaut.transaction.TransactionDefinition.Propagation#NEVER
         */
        ALWAYS,
        /**
         * Activate transaction synchronization only for actual transactions,
         * that is, not for empty ones that result from PROPAGATION_SUPPORTS with
         * no existing backend transaction.
         * @see io.micronaut.transaction.TransactionDefinition.Propagation#REQUIRED
         * @see io.micronaut.transaction.TransactionDefinition.Propagation#MANDATORY
         * @see io.micronaut.transaction.TransactionDefinition.Propagation#REQUIRES_NEW
         */
        ON_ACTUAL_TRANSACTION,
        /**
         * Never active transaction synchronization, not even for actual transactions.
         */
        NEVER
    }

    protected transient Logger logger = LoggerFactory.getLogger(getClass());

    private Synchronization transactionSynchronization = Synchronization.ALWAYS;

    private Duration defaultTimeout = TransactionDefinition.TIMEOUT_DEFAULT;

    private boolean nestedTransactionAllowed = false;

    private boolean validateExistingTransaction = false;

    private boolean globalRollbackOnParticipationFailure = true;

    private boolean failEarlyOnGlobalRollbackOnly = false;

    private boolean rollbackOnCommitFailure = false;

    @Override
    public <R> R execute(@NonNull TransactionDefinition definition, @NonNull TransactionCallback<T, R> callback) {
        Objects.requireNonNull(definition, "Definition should not be null");
        Objects.requireNonNull(callback, "Callback should not be null");
        TransactionStatus<T> status = getTransaction(definition);
        R result;
        try {
            result = callback.call(status);
        } catch (RuntimeException | Error ex) {
            // Transactional code threw application exception -> rollback
            rollbackOnException(status, ex);
            throw ex;
        } catch (Throwable ex) {
            // Transactional code threw unexpected exception -> rollback
            rollbackOnException(status, ex);
            throw new UndeclaredThrowableException(ex, "TransactionCallback threw undeclared checked exception");
        }
        commit(status);
        return result;
    }

    @Override
    public <R> R executeRead(@NonNull TransactionCallback<T, R> callback)  {
        Objects.requireNonNull(callback, "Callback should not be null");
        TransactionStatus<T> status = getTransaction(TransactionDefinition.READ_ONLY);
        R result;
        try {
            result = callback.call(status);
        } catch (RuntimeException | Error ex) {
            // Transactional code threw application exception -> rollback
            rollbackOnException(status, ex);
            throw ex;
        } catch (Throwable ex) {
            // Transactional code threw unexpected exception -> rollback
            rollbackOnException(status, ex);
            throw new UndeclaredThrowableException(ex, "TransactionCallback threw undeclared checked exception");
        }
        commit(status);
        return result;
    }

    @Override
    public <R> R executeWrite(@NonNull TransactionCallback<T, R> callback) {
        Objects.requireNonNull(callback, "Callback should not be null");
        TransactionStatus<T> status = getTransaction(TransactionDefinition.DEFAULT);
        R result;
        try {
            result = callback.call(status);
        } catch (RuntimeException | Error ex) {
            // Transactional code threw application exception -> rollback
            rollbackOnException(status, ex);
            throw ex;
        } catch (Throwable ex) {
            // Transactional code threw unexpected exception -> rollback
            rollbackOnException(status, ex);
            throw new UndeclaredThrowableException(ex, "TransactionCallback threw undeclared checked exception");
        }
        commit(status);
        return result;
    }

    /**
     * Set when this transaction manager should activate the thread-bound
     * transaction synchronization support. Default is "always".
     * <p>Note that transaction synchronization isn't supported for
     * multiple concurrent transactions by different transaction managers.
     * Only one transaction manager is allowed to activate it at any time.
     * @see Synchronization#ALWAYS
     * @see Synchronization#ON_ACTUAL_TRANSACTION
     * @see Synchronization#NEVER
     * @see TransactionSynchronizationManager
     * @see TransactionSynchronization
     * @param transactionSynchronization the synchronization to use
     */
    public final void setTransactionSynchronization(@NonNull Synchronization transactionSynchronization) {
        if (transactionSynchronization != null) {
            this.transactionSynchronization = transactionSynchronization;
        }
    }

    /**
     * Return if this transaction manager should activate the thread-bound
     * transaction synchronization support.
     * @return The current synchronization
     */
    public final @NonNull Synchronization getTransactionSynchronization() {
        return this.transactionSynchronization;
    }

    /**
     * Specify the default timeout that this transaction manager should apply
     * if there is no timeout specified at the transaction level, in seconds.
     * <p>Default is the underlying transaction infrastructure's default timeout,
     * e.g. typically 30 seconds in case of a JTA provider, indicated by the
     * {@code TransactionDefinition.TIMEOUT_DEFAULT} value.
     * @see TransactionDefinition#TIMEOUT_DEFAULT
     * @param defaultTimeout The default timeout
     */
    public final void setDefaultTimeout(@NonNull Duration defaultTimeout) {
        if (defaultTimeout == null || defaultTimeout.isNegative()) {
            throw new InvalidTimeoutException("Invalid default timeout", defaultTimeout);
        }
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Return the default timeout that this transaction manager should apply
     * if there is no timeout specified at the transaction level, in seconds.
     * <p>Returns {@code TransactionDefinition.TIMEOUT_DEFAULT} to indicate
     * the underlying transaction infrastructure's default timeout.
     *
     * @return The default timeout
     */
    public final @NonNull Duration getDefaultTimeout() {
        return this.defaultTimeout;
    }

    /**
     * Set whether nested transactions are allowed. Default is "false".
     * <p>Typically initialized with an appropriate default by the
     * concrete transaction manager subclass.
     *
     * @param nestedTransactionAllowed  Whether a nested transaction is allowed
     */
    public final void setNestedTransactionAllowed(boolean nestedTransactionAllowed) {
        this.nestedTransactionAllowed = nestedTransactionAllowed;
    }

    /**
     * @return Return whether nested transactions are allowed.
     */
    public final boolean isNestedTransactionAllowed() {
        return this.nestedTransactionAllowed;
    }

    /**
     * Set whether existing transactions should be validated before participating
     * in them.
     * <p>When participating in an existing transaction (e.g. with
     * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
     * transaction), this outer transaction's characteristics will apply even
     * to the inner transaction scope. Validation will detect incompatible
     * isolation level and read-only settings on the inner transaction definition
     * and reject participation accordingly through throwing a corresponding exception.
     * <p>Default is "false", leniently ignoring inner transaction settings,
     * simply overriding them with the outer transaction's characteristics.
     * Switch this flag to "true" in order to enforce strict validation.
     * @since 2.5.1
     * @param validateExistingTransaction Whether to validate an existing transaction
     */
    public final void setValidateExistingTransaction(boolean validateExistingTransaction) {
        this.validateExistingTransaction = validateExistingTransaction;
    }

    /**
     * Return whether existing transactions should be validated before participating
     * in them.
     * @since 2.5.1
     * @return Whether to validate existing transactions
     */
    public final boolean isValidateExistingTransaction() {
        return this.validateExistingTransaction;
    }

    /**
     * Set whether to globally mark an existing transaction as rollback-only
     * after a participating transaction failed.
     * <p>Default is "true": If a participating transaction (e.g. with
     * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
     * transaction) fails, the transaction will be globally marked as rollback-only.
     * The only possible outcome of such a transaction is a rollback: The
     * transaction originator <i>cannot</i> make the transaction commit anymore.
     * <p>Switch this to "false" to let the transaction originator make the rollback
     * decision. If a participating transaction fails with an exception, the caller
     * can still decide to continue with a different path within the transaction.
     * However, note that this will only work as long as all participating resources
     * are capable of continuing towards a transaction commit even after a data access
     * failure: This is generally not the case for a Hibernate Session, for example;
     * neither is it for a sequence of JDBC insert/update/delete operations.
     * <p><b>Note:</b>This flag only applies to an explicit rollback attempt for a
     * subtransaction, typically caused by an exception thrown by a data access operation
     * (where TransactionInterceptor will trigger a {@code PlatformTransactionManager.rollback()}
     * call according to a rollback rule). If the flag is off, the caller can handle the exception
     * and decide on a rollback, independent of the rollback rules of the subtransaction.
     * This flag does, however, <i>not</i> apply to explicit {@code setRollbackOnly}
     * calls on a {@code TransactionStatus}, which will always cause an eventual
     * global rollback (as it might not throw an exception after the rollback-only call).
     * <p>The recommended solution for handling failure of a subtransaction
     * is a "nested transaction", where the global transaction can be rolled
     * back to a savepoint taken at the beginning of the subtransaction.
     * PROPAGATION_NESTED provides exactly those semantics; however, it will
     * only work when nested transaction support is available. This is the case
     * with DataSourceTransactionManager, but not with JtaTransactionManager.
     * @see #setNestedTransactionAllowed
     * @param globalRollbackOnParticipationFailure Whether to globally mark transaction as rollback only
     */
    public final void setGlobalRollbackOnParticipationFailure(boolean globalRollbackOnParticipationFailure) {
        this.globalRollbackOnParticipationFailure = globalRollbackOnParticipationFailure;
    }

    /**
     * @return Return whether to globally mark an existing transaction as rollback-only
     * after a participating transaction failed.
     */
    public final boolean isGlobalRollbackOnParticipationFailure() {
        return this.globalRollbackOnParticipationFailure;
    }

    /**
     * Set whether to fail early in case of the transaction being globally marked
     * as rollback-only.
     * <p>Default is "false", only causing an UnexpectedRollbackException at the
     * outermost transaction boundary. Switch this flag on to cause an
     * UnexpectedRollbackException as early as the global rollback-only marker
     * has been first detected, even from within an inner transaction boundary.
     * @since 2.0
     * @see io.micronaut.transaction.exceptions.UnexpectedRollbackException
     * @param failEarlyOnGlobalRollbackOnly Sets whether to fail early on global rollback
     */
    public final void setFailEarlyOnGlobalRollbackOnly(boolean failEarlyOnGlobalRollbackOnly) {
        this.failEarlyOnGlobalRollbackOnly = failEarlyOnGlobalRollbackOnly;
    }

    /**
     * @return Return whether to fail early in case of the transaction being globally marked
     * as rollback-only.
     * @since 2.0
     */
    public final boolean isFailEarlyOnGlobalRollbackOnly() {
        return this.failEarlyOnGlobalRollbackOnly;
    }

    /**
     * Set whether {@code doRollback} should be performed on failure of the
     * {@code doCommit} call. Typically not necessary and thus to be avoided,
     * as it can potentially override the commit exception with a subsequent
     * rollback exception.
     * <p>Default is "false".
     * @see #doCommit
     * @see #doRollback
     * @param rollbackOnCommitFailure Sets whether to rollback on commit failure
     */
    public final void setRollbackOnCommitFailure(boolean rollbackOnCommitFailure) {
        this.rollbackOnCommitFailure = rollbackOnCommitFailure;
    }

    /**
     * @return Return whether {@code doRollback} should be performed on failure of the
     * {@code doCommit} call.
     */
    public final boolean isRollbackOnCommitFailure() {
        return this.rollbackOnCommitFailure;
    }


    //---------------------------------------------------------------------
    // Implementation of PlatformTransactionManager
    //---------------------------------------------------------------------

    /**
     * This implementation handles propagation behavior. Delegates to
     * {@code doGetTransaction}, {@code isExistingTransaction}
     * and {@code doBegin}.
     * @see #doGetTransaction
     * @see #isExistingTransaction
     * @see #doBegin
     */
    @Override
    @NonNull
    public final TransactionStatus<T> getTransaction(@Nullable TransactionDefinition definition)
            throws TransactionException {

        // Use defaults if no transaction definition given.
        definition = (definition != null ? definition : TransactionDefinition.DEFAULT);

        Object transaction = doGetTransaction();
        boolean debugEnabled = logger.isDebugEnabled();

        if (isExistingTransaction(transaction)) {
            // Existing transaction found -> check propagation behavior to find out how to behave.
            return handleExistingTransaction(definition, transaction, debugEnabled);
        }

        // Check definition settings for new transaction.
        if (definition.getTimeout().compareTo(TransactionDefinition.TIMEOUT_DEFAULT) < 0) {
            throw new InvalidTimeoutException("Invalid transaction timeout", definition.getTimeout());
        }

        // No existing transaction found -> check propagation behavior to find out how to proceed.
        TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
        switch (propagationBehavior) {
            case MANDATORY:
                throw new IllegalTransactionStateException(
                        "No existing transaction found for transaction marked with propagation 'mandatory'");
            case REQUIRED:
            case REQUIRES_NEW:
            case NESTED:
                SuspendedResourcesHolder suspendedResources = suspend(null);
                if (debugEnabled) {
                    logger.debug("Creating new transaction with name [" + definition.getName() + "]: " + definition);
                }
                try {
                    boolean newSynchronization = (getTransactionSynchronization() != Synchronization.NEVER);
                    DefaultTransactionStatus status = newTransactionStatus(
                            definition, transaction, true, newSynchronization, debugEnabled, suspendedResources);
                    doBegin(transaction, definition);
                    prepareSynchronization(status, definition);
                    return status;
                } catch (RuntimeException | Error ex) {
                    resume(null, suspendedResources);
                    throw ex;
                }
            default:
                // Create "empty" transaction: no actual transaction, but potentially synchronization.
                if (definition.getIsolationLevel() != TransactionDefinition.Isolation.DEFAULT && logger.isWarnEnabled()) {
                    logger.warn("Custom isolation level specified but no actual transaction initiated; " +
                            "isolation level will effectively be ignored: " + definition);
                }
                boolean newSynchronization = (getTransactionSynchronization() == Synchronization.ALWAYS);
                return prepareTransactionStatus(definition, null, true, newSynchronization, debugEnabled, null);
        }
    }

    /**
     * Create a TransactionStatus for an existing transaction.
     */
    private TransactionStatus handleExistingTransaction(
            TransactionDefinition definition, Object transaction, boolean debugEnabled)
            throws TransactionException {

        TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
        switch (propagationBehavior) {
            case NEVER:
                throw new IllegalTransactionStateException(
                        "Existing transaction found for transaction marked with propagation 'never'");
            case NOT_SUPPORTED:
                if (debugEnabled) {
                    logger.debug("Suspending current transaction");
                }
                Object suspendedResources = suspend(transaction);
                boolean newSynchronization = (getTransactionSynchronization() == Synchronization.ALWAYS);
                return prepareTransactionStatus(
                        definition,
                        null,
                        false,
                        newSynchronization,
                        debugEnabled,
                        suspendedResources
                );
            case REQUIRES_NEW:
                if (debugEnabled) {
                    logger.debug("Suspending current transaction, creating new transaction with name [" +
                            definition.getName() + "]");
                }
                SuspendedResourcesHolder requiresNewSuspendedResources = suspend(transaction);
                try {
                    boolean requiresNewIsNewSynchronization = (getTransactionSynchronization() != Synchronization.NEVER);
                    DefaultTransactionStatus status = newTransactionStatus(
                            definition, transaction, true, requiresNewIsNewSynchronization, debugEnabled, requiresNewSuspendedResources);
                    doBegin(transaction, definition);
                    prepareSynchronization(status, definition);
                    return status;
                } catch (RuntimeException | Error beginEx) {
                    resumeAfterBeginException(transaction, requiresNewSuspendedResources, beginEx);
                    throw beginEx;
                }
            case NESTED:
                if (!isNestedTransactionAllowed()) {
                    throw new NestedTransactionNotSupportedException(
                            "Transaction manager does not allow nested transactions by default - " +
                                    "specify 'nestedTransactionAllowed' property with value 'true'");
                }
                if (debugEnabled) {
                    logger.debug("Creating nested transaction with name [" + definition.getName() + "]");
                }
                if (useSavepointForNestedTransaction()) {
                    // Create savepoint within existing Spring-managed transaction,
                    // through the SavepointManager API implemented by TransactionStatus.
                    // Usually uses JDBC 3.0 savepoints. Never activates Spring synchronization.
                    DefaultTransactionStatus status =
                            prepareTransactionStatus(definition, transaction, false, false, debugEnabled, null);
                    status.createAndHoldSavepoint();
                    return status;
                } else {
                    // Nested transaction through nested begin and commit/rollback calls.
                    // Usually only for JTA: Spring synchronization might get activated here
                    // in case of a pre-existing JTA transaction.
                    boolean nestedNewSynchronization = (getTransactionSynchronization() != Synchronization.NEVER);
                    DefaultTransactionStatus status = newTransactionStatus(
                            definition, transaction, true, nestedNewSynchronization, debugEnabled, null);
                    doBegin(transaction, definition);
                    prepareSynchronization(status, definition);
                    return status;
                }
            default:
                // Assumably PROPAGATION_SUPPORTS or PROPAGATION_REQUIRED.
                if (debugEnabled) {
                    logger.debug("Participating in existing transaction");
                }
                if (isValidateExistingTransaction()) {
                    if (definition.getIsolationLevel() != TransactionDefinition.Isolation.DEFAULT) {
                        TransactionDefinition.Isolation currentIsolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
                        if (currentIsolationLevel == null || currentIsolationLevel != definition.getIsolationLevel()) {
                            throw new IllegalTransactionStateException("Participating transaction with definition [" +
                                    definition + "] specifies isolation level which is incompatible with existing transaction: " +
                                    (currentIsolationLevel != null ?
                                            currentIsolationLevel.getCode() :
                                            "(unknown)"));
                        }
                    }
                    if (!definition.isReadOnly()) {
                        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                            throw new IllegalTransactionStateException("Participating transaction with definition [" +
                                    definition + "] is not marked as read-only but existing transaction is");
                        }
                    }
                }
                boolean defaultNewSynchronization = (getTransactionSynchronization() != Synchronization.NEVER);
                return prepareTransactionStatus(definition, transaction, false, defaultNewSynchronization, debugEnabled, null);
        }
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
    protected final DefaultTransactionStatus prepareTransactionStatus(
            TransactionDefinition definition,
            @Nullable Object transaction,
            boolean newTransaction,
            boolean newSynchronization,
            boolean debug,
            @Nullable Object suspendedResources) {

        DefaultTransactionStatus status = newTransactionStatus(
                definition, transaction, newTransaction, newSynchronization, debug, suspendedResources);
        prepareSynchronization(status, definition);
        return status;
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
    protected DefaultTransactionStatus newTransactionStatus(
            TransactionDefinition definition,
            @Nullable Object transaction,
            boolean newTransaction,
            boolean newSynchronization,
            boolean debug,
            @Nullable Object suspendedResources) {

        boolean actualNewSynchronization = newSynchronization &&
                !TransactionSynchronizationManager.isSynchronizationActive();
        return new DefaultTransactionStatus<>(
                transaction, () -> getConnection(transaction), newTransaction, actualNewSynchronization,
                definition.isReadOnly(), debug, suspendedResources);
    }

    /**
     * The connection for the given transaction object.
     * @param transaction The transaction
     * @return The connection.
     */
    @Nullable
    protected abstract T getConnection(Object transaction);

    /**
     * Initialize transaction synchronization as appropriate.
     *
     * @param status The status
     * @param definition The definition
     */
    protected void prepareSynchronization(
            @NonNull DefaultTransactionStatus status,
            @NonNull TransactionDefinition definition) {
        if (status.isNewSynchronization()) {
            TransactionSynchronizationManager.setActualTransactionActive(status.hasTransaction());
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
                    definition.getIsolationLevel() != TransactionDefinition.Isolation.DEFAULT ?
                            definition.getIsolationLevel() : null);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
            TransactionSynchronizationManager.setCurrentTransactionName(definition.getName());
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    /**
     * Determine the actual timeout to use for the given definition.
     * Will fall back to this manager's default timeout if the
     * transaction definition doesn't specify a non-default value.
     * @param definition the transaction definition
     * @return the actual timeout to use
     * @see TransactionDefinition#getTimeout()
     * @see #setDefaultTimeout
     */
    protected Duration determineTimeout(TransactionDefinition definition) {
        if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
            return definition.getTimeout();
        }
        return getDefaultTimeout();
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            List<TransactionSynchronization> suspendedSynchronizations = doSuspendSynchronization();
            try {
                Object suspendedResources = null;
                if (transaction != null) {
                    suspendedResources = doSuspend(transaction);
                }
                String name = TransactionSynchronizationManager.getCurrentTransactionName();
                TransactionSynchronizationManager.setCurrentTransactionName(null);
                boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
                TransactionDefinition.Isolation isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
                boolean wasActive = TransactionSynchronizationManager.isActualTransactionActive();
                TransactionSynchronizationManager.setActualTransactionActive(false);
                return new SuspendedResourcesHolder(
                        suspendedResources, suspendedSynchronizations, name, readOnly, isolationLevel, wasActive);
            } catch (RuntimeException | Error ex) {
                // doSuspend failed - original transaction is still active...
                doResumeSynchronization(suspendedSynchronizations);
                throw ex;
            }
        } else if (transaction != null) {
            // Transaction active but no synchronization active.
            Object suspendedResources = doSuspend(transaction);
            return new SuspendedResourcesHolder(suspendedResources);
        } else {
            // Neither transaction nor synchronization active.
            return null;
        }
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

        if (resourcesHolder != null) {
            Object suspendedResources = resourcesHolder.suspendedResources;
            if (suspendedResources != null) {
                doResume(transaction, suspendedResources);
            }
            List<TransactionSynchronization> suspendedSynchronizations = resourcesHolder.suspendedSynchronizations;
            if (suspendedSynchronizations != null) {
                TransactionSynchronizationManager.setActualTransactionActive(resourcesHolder.wasActive);
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(resourcesHolder.isolationLevel);
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(resourcesHolder.readOnly);
                TransactionSynchronizationManager.setCurrentTransactionName(resourcesHolder.name);
                doResumeSynchronization(suspendedSynchronizations);
            }
        }
    }

    /**
     * Resume outer transaction after inner transaction begin failed.
     */
    private void resumeAfterBeginException(
            Object transaction, @Nullable SuspendedResourcesHolder suspendedResources, Throwable beginEx) {

        try {
            resume(transaction, suspendedResources);
        } catch (RuntimeException | Error resumeEx) {
            String exMessage = "Inner transaction begin exception overridden by outer transaction resume exception";
            logger.error(exMessage, beginEx);
            throw resumeEx;
        }
    }

    /**
     * Suspend all current synchronizations and deactivate transaction
     * synchronization for the current thread.
     * @return the List of suspended TransactionSynchronization objects
     */
    private List<TransactionSynchronization> doSuspendSynchronization() {
        List<TransactionSynchronization> suspendedSynchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization synchronization : suspendedSynchronizations) {
            synchronization.suspend();
        }
        TransactionSynchronizationManager.clearSynchronization();
        return suspendedSynchronizations;
    }

    /**
     * Reactivate transaction synchronization for the current thread
     * and resume all given synchronizations.
     * @param suspendedSynchronizations a List of TransactionSynchronization objects
     */
    private void doResumeSynchronization(List<TransactionSynchronization> suspendedSynchronizations) {
        TransactionSynchronizationManager.initSynchronization();
        for (TransactionSynchronization synchronization : suspendedSynchronizations) {
            synchronization.resume();
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }
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
    public final void commit(TransactionStatus status) throws TransactionException {
        if (status.isCompleted()) {
            throw new IllegalTransactionStateException(
                    "Transaction is already completed - do not call commit or rollback more than once per transaction");
        }

        DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
        if (defStatus.isLocalRollbackOnly()) {
            if (defStatus.isDebug()) {
                logger.debug("Transactional code has requested rollback");
            }
            processRollback(defStatus, false);
            return;
        }

        if (!shouldCommitOnGlobalRollbackOnly() && defStatus.isGlobalRollbackOnly()) {
            if (defStatus.isDebug()) {
                logger.debug("Global transaction is marked as rollback-only but transactional code requested commit");
            }
            processRollback(defStatus, true);
            return;
        }

        processCommit(defStatus);
    }

    /**
     * Process an actual commit.
     * Rollback-only flags have already been checked and applied.
     * @param status object representing the transaction
     * @throws TransactionException in case of commit failure
     */
    private void processCommit(DefaultTransactionStatus status) throws TransactionException {
        try {
            boolean beforeCompletionInvoked = false;

            try {
                boolean unexpectedRollback = false;
                prepareForCommit(status);
                triggerBeforeCommit(status);
                triggerBeforeCompletion(status);
                beforeCompletionInvoked = true;

                if (status.hasSavepoint()) {
                    if (status.isDebug()) {
                        logger.debug("Releasing transaction savepoint");
                    }
                    unexpectedRollback = status.isGlobalRollbackOnly();
                    status.releaseHeldSavepoint();
                } else if (status.isNewTransaction()) {
                    if (status.isDebug()) {
                        logger.debug("Initiating transaction commit");
                    }
                    unexpectedRollback = status.isGlobalRollbackOnly();
                    doCommit(status);
                } else if (isFailEarlyOnGlobalRollbackOnly()) {
                    unexpectedRollback = status.isGlobalRollbackOnly();
                }

                // Throw UnexpectedRollbackException if we have a global rollback-only
                // marker but still didn't get a corresponding exception from commit.
                if (unexpectedRollback) {
                    throw new UnexpectedRollbackException(
                            "Transaction silently rolled back because it has been marked as rollback-only");
                }
            } catch (UnexpectedRollbackException ex) {
                // can only be caused by doCommit
                triggerAfterCompletion(status, TransactionSynchronization.Status.ROLLED_BACK);
                throw ex;
            } catch (TransactionException ex) {
                // can only be caused by doCommit
                if (isRollbackOnCommitFailure()) {
                    doRollbackOnCommitException(status, ex);
                } else {
                    triggerAfterCompletion(status, TransactionSynchronization.Status.UNKNOWN);
                }
                throw ex;
            } catch (RuntimeException | Error ex) {
                if (!beforeCompletionInvoked) {
                    triggerBeforeCompletion(status);
                }
                doRollbackOnCommitException(status, ex);
                throw ex;
            }

            // Trigger afterCommit callbacks, with an exception thrown there
            // propagated to callers but the transaction still considered as committed.
            try {
                triggerAfterCommit(status);
            } finally {
                triggerAfterCompletion(status, TransactionSynchronization.Status.COMMITTED);
            }

        } finally {
            cleanupAfterCompletion(status);
        }
    }

    /**
     * This implementation of rollback handles participating in existing
     * transactions. Delegates to {@code doRollback} and
     * {@code doSetRollbackOnly}.
     * @see #doRollback
     * @see #doSetRollbackOnly
     */
    @Override
    public final void rollback(TransactionStatus status) throws TransactionException {
        if (status.isCompleted()) {
            throw new IllegalTransactionStateException(
                    "Transaction is already completed - do not call commit or rollback more than once per transaction");
        }

        DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
        processRollback(defStatus, false);
    }

    /**
     * Process an actual rollback.
     * The completed flag has already been checked.
     * @param status object representing the transaction
     * @throws TransactionException in case of rollback failure
     */
    private void processRollback(DefaultTransactionStatus status, boolean unexpected) {
        try {
            boolean unexpectedRollback = unexpected;

            try {
                triggerBeforeCompletion(status);

                if (status.hasSavepoint()) {
                    if (status.isDebug()) {
                        logger.debug("Rolling back transaction to savepoint");
                    }
                    status.rollbackToHeldSavepoint();
                } else if (status.isNewTransaction()) {
                    if (status.isDebug()) {
                        logger.debug("Initiating transaction rollback");
                    }
                    doRollback(status);
                } else {
                    // Participating in larger transaction
                    if (status.hasTransaction()) {
                        if (status.isLocalRollbackOnly() || isGlobalRollbackOnParticipationFailure()) {
                            if (status.isDebug()) {
                                logger.debug("Participating transaction failed - marking existing transaction as rollback-only");
                            }
                            doSetRollbackOnly(status);
                        } else {
                            if (status.isDebug()) {
                                logger.debug("Participating transaction failed - letting transaction originator decide on rollback");
                            }
                        }
                    } else {
                        logger.debug("Should roll back transaction but cannot - no transaction available");
                    }
                    // Unexpected rollback only matters here if we're asked to fail early
                    if (!isFailEarlyOnGlobalRollbackOnly()) {
                        unexpectedRollback = false;
                    }
                }
            } catch (RuntimeException | Error ex) {
                triggerAfterCompletion(status, TransactionSynchronization.Status.UNKNOWN);
                throw ex;
            }

            triggerAfterCompletion(status, TransactionSynchronization.Status.ROLLED_BACK);

            // Raise UnexpectedRollbackException if we had a global rollback-only marker
            if (unexpectedRollback) {
                throw new UnexpectedRollbackException(
                        "Transaction rolled back because it has been marked as rollback-only");
            }
        } finally {
            cleanupAfterCompletion(status);
        }
    }

    /**
     * Invoke {@code doRollback}, handling rollback exceptions properly.
     * @param status object representing the transaction
     * @param ex the thrown application exception or error
     * @throws TransactionException in case of rollback failure
     * @see #doRollback
     */
    private void doRollbackOnCommitException(DefaultTransactionStatus status, Throwable ex) throws TransactionException {
        try {
            if (status.isNewTransaction()) {
                if (status.isDebug()) {
                    logger.debug("Initiating transaction rollback after commit exception", ex);
                }
                doRollback(status);
            } else if (status.hasTransaction() && isGlobalRollbackOnParticipationFailure()) {
                if (status.isDebug()) {
                    logger.debug("Marking existing transaction as rollback-only after commit exception", ex);
                }
                doSetRollbackOnly(status);
            }
        } catch (RuntimeException | Error rbex) {
            logger.error("Commit exception overridden by rollback exception", ex);
            triggerAfterCompletion(status, TransactionSynchronization.Status.UNKNOWN);
            throw rbex;
        }
        triggerAfterCompletion(status, TransactionSynchronization.Status.ROLLED_BACK);
    }

    /**
     * Trigger {@code beforeCommit} callbacks.
     * @param status object representing the transaction
     */
    protected final void triggerBeforeCommit(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            if (status.isDebug()) {
                logger.trace("Triggering beforeCommit synchronization");
            }
            TransactionSynchronizationUtils.triggerBeforeCommit(status.isReadOnly());
        }
    }

    /**
     * Trigger {@code beforeCompletion} callbacks.
     * @param status object representing the transaction
     */
    protected final void triggerBeforeCompletion(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            if (status.isDebug()) {
                logger.trace("Triggering beforeCompletion synchronization");
            }
            TransactionSynchronizationUtils.triggerBeforeCompletion();
        }
    }

    /**
     * Trigger {@code afterCommit} callbacks.
     * @param status object representing the transaction
     */
    private void triggerAfterCommit(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            if (status.isDebug()) {
                logger.trace("Triggering afterCommit synchronization");
            }
            TransactionSynchronizationUtils.triggerAfterCommit();
        }
    }

    /**
     * Trigger {@code afterCompletion} callbacks.
     * @param status object representing the transaction
     * @param completionStatus completion status according to TransactionSynchronization constants
     */
    private void triggerAfterCompletion(DefaultTransactionStatus status, TransactionSynchronization.Status completionStatus) {
        if (status.isNewSynchronization()) {
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationManager.clearSynchronization();
            if (!status.hasTransaction() || status.isNewTransaction()) {
                if (status.isDebug()) {
                    logger.trace("Triggering afterCompletion synchronization");
                }
                // No transaction or new transaction for the current scope ->
                // invoke the afterCompletion callbacks immediately
                invokeAfterCompletion(synchronizations, completionStatus);
            } else if (!synchronizations.isEmpty()) {
                // Existing transaction that we participate in, controlled outside
                // of the scope of this Spring transaction manager -> try to register
                // an afterCompletion callback with the existing (JTA) transaction.
                registerAfterCompletionWithExistingTransaction(status.getTransaction(), synchronizations);
            }
        }
    }

    /**
     * Actually invoke the {@code afterCompletion} methods of the
     * given Spring TransactionSynchronization objects.
     * <p>To be called by this abstract manager itself, or by special implementations
     * of the {@code registerAfterCompletionWithExistingTransaction} callback.
     * @param synchronizations a List of TransactionSynchronization objects
     * @param completionStatus the completion status according to the
     * constants in the TransactionSynchronization interface
     * @see #registerAfterCompletionWithExistingTransaction(Object, java.util.List)
     * @see io.micronaut.transaction.support.TransactionSynchronization.Status#COMMITTED
     * @see io.micronaut.transaction.support.TransactionSynchronization.Status#ROLLED_BACK
     * @see io.micronaut.transaction.support.TransactionSynchronization.Status#UNKNOWN
     */
    protected final void invokeAfterCompletion(List<TransactionSynchronization> synchronizations, TransactionSynchronization.Status completionStatus) {
        TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, completionStatus);
    }

    /**
     * Clean up after completion, clearing synchronization if necessary,
     * and invoking doCleanupAfterCompletion.
     * @param status object representing the transaction
     * @see #doCleanupAfterCompletion
     */
    private void cleanupAfterCompletion(DefaultTransactionStatus status) {
        status.setCompleted();
        if (status.isNewSynchronization()) {
            TransactionSynchronizationManager.clear();
        }
        if (status.isNewTransaction()) {
            doCleanupAfterCompletion(status.getTransaction());
        }
        if (status.getSuspendedResources() != null) {
            if (status.isDebug()) {
                logger.debug("Resuming suspended transaction after completion of inner transaction");
            }
            Object transaction = (status.hasTransaction() ? status.getTransaction() : null);
            resume(transaction, (SuspendedResourcesHolder) status.getSuspendedResources());
        }
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
     * @throws CannotCreateTransactionException
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

    /**
     * Return whether to use a savepoint for a nested transaction.
     * <p>Default is {@code true}, which causes delegation to DefaultTransactionStatus
     * for creating and holding a savepoint. If the transaction object does not implement
     * the SavepointManager interface, a NestedTransactionNotSupportedException will be
     * thrown. Else, the SavepointManager will be asked to create a new savepoint to
     * demarcate the start of the nested transaction.
     * <p>Subclasses can override this to return {@code false}, causing a further
     * call to {@code doBegin} - within the context of an already existing transaction.
     * The {@code doBegin} implementation needs to handle this accordingly in such
     * a scenario. This is appropriate for JTA, for example.
     * @see DefaultTransactionStatus#createAndHoldSavepoint
     * @see DefaultTransactionStatus#rollbackToHeldSavepoint
     * @see DefaultTransactionStatus#releaseHeldSavepoint
     * @see #doBegin
     * @return Whether to use save points for nested transactions
     */
    protected boolean useSavepointForNestedTransaction() {
        return true;
    }

    /**
     * Begin a new transaction with semantics according to the given transaction
     * definition. Does not have to care about applying the propagation behavior,
     * as this has already been handled by this abstract manager.
     * <p>This method gets called when the transaction manager has decided to actually
     * start a new transaction. Either there wasn't any transaction before, or the
     * previous transaction has been suspended.
     * <p>A special scenario is a nested transaction without savepoint: If
     * {@code useSavepointForNestedTransaction()} returns "false", this method
     * will be called to start a nested transaction when necessary. In such a context,
     * there will be an active transaction: The implementation of this method has
     * to detect this and start an appropriate nested transaction.
     * @param transaction transaction object returned by {@code doGetTransaction}
     * @param definition a TransactionDefinition instance, describing propagation
     * behavior, isolation level, read-only flag, timeout, and transaction name
     * @throws TransactionException in case of creation or system errors
     * @throws NestedTransactionNotSupportedException
     * if the underlying transaction does not support nesting
     */
    protected abstract void doBegin(@NonNull Object transaction, TransactionDefinition definition)
            throws TransactionException;

    /**
     * Suspend the resources of the current transaction.
     * Transaction synchronization will already have been suspended.
     * <p>The default implementation throws a TransactionSuspensionNotSupportedException,
     * assuming that transaction suspension is generally not supported.
     * @param transaction transaction object returned by {@code doGetTransaction}
     * @return an object that holds suspended resources
     * (will be kept unexamined for passing it into doResume)
     * @throws TransactionSuspensionNotSupportedException
     * if suspending is not supported by the transaction manager implementation
     * @throws TransactionException in case of system errors
     * @see #doResume
     */
    protected @Nullable Object doSuspend(@NonNull Object transaction) throws TransactionException {
        throw new TransactionSuspensionNotSupportedException(
                "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }

    /**
     * Resume the resources of the current transaction.
     * Transaction synchronization will be resumed afterwards.
     * <p>The default implementation throws a TransactionSuspensionNotSupportedException,
     * assuming that transaction suspension is generally not supported.
     * @param transaction transaction object returned by {@code doGetTransaction}
     * @param suspendedResources the object that holds suspended resources,
     * as returned by doSuspend
     * @throws TransactionSuspensionNotSupportedException
     * if resuming is not supported by the transaction manager implementation
     * @throws TransactionException in case of system errors
     * @see #doSuspend
     */
    protected void doResume(@Nullable Object transaction, @NonNull Object suspendedResources) throws TransactionException {
        throw new TransactionSuspensionNotSupportedException(
                "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }

    /**
     * Return whether to call {@code doCommit} on a transaction that has been
     * marked as rollback-only in a global fashion.
     * <p>Does not apply if an application locally sets the transaction to rollback-only
     * via the TransactionStatus, but only to the transaction itself being marked as
     * rollback-only by the transaction coordinator.
     * <p>Default is "false": Local transaction strategies usually don't hold the rollback-only
     * marker in the transaction itself, therefore they can't handle rollback-only transactions
     * as part of transaction commit. Hence, AbstractPlatformTransactionManager will trigger
     * a rollback in that case, throwing an UnexpectedRollbackException afterwards.
     * <p>Override this to return "true" if the concrete transaction manager expects a
     * {@code doCommit} call even for a rollback-only transaction, allowing for
     * special handling there. This will, for example, be the case for JTA, where
     * {@code UserTransaction.commit} will check the read-only flag itself and
     * throw a corresponding RollbackException, which might include the specific reason
     * (such as a transaction timeout).
     * <p>If this method returns "true" but the {@code doCommit} implementation does not
     * throw an exception, this transaction manager will throw an UnexpectedRollbackException
     * itself. This should not be the typical case; it is mainly checked to cover misbehaving
     * JTA providers that silently roll back even when the rollback has not been requested
     * by the calling code.
     * @see #doCommit
     * @see DefaultTransactionStatus#isGlobalRollbackOnly()
     * @see DefaultTransactionStatus#isLocalRollbackOnly()
     * @see TransactionStatus#setRollbackOnly()
     * @see UnexpectedRollbackException
     * @return Whether to call {@code doCommit} on a transaction that has been marked as rollback-only in a global fashion.
     */
    protected boolean shouldCommitOnGlobalRollbackOnly() {
        return false;
    }

    /**
     * Make preparations for commit, to be performed before the
     * {@code beforeCommit} synchronization callbacks occur.
     * <p>Note that exceptions will get propagated to the commit caller
     * and cause a rollback of the transaction.
     * @param status the status representation of the transaction
     * @throws RuntimeException in case of errors; will be <b>propagated to the caller</b>
     * (note: do not throw TransactionException subclasses here!)
     */
    protected void prepareForCommit(DefaultTransactionStatus status) {
    }

    /**
     * Perform an actual commit of the given transaction.
     * <p>An implementation does not need to check the "new transaction" flag
     * or the rollback-only flag; this will already have been handled before.
     * Usually, a straight commit will be performed on the transaction object
     * contained in the passed-in status.
     * @param status the status representation of the transaction
     * @throws TransactionException in case of commit or system errors
     * @see DefaultTransactionStatus#getTransaction
     */
    protected abstract void doCommit(DefaultTransactionStatus status) throws TransactionException;

    /**
     * Perform an actual rollback of the given transaction.
     * <p>An implementation does not need to check the "new transaction" flag;
     * this will already have been handled before. Usually, a straight rollback
     * will be performed on the transaction object contained in the passed-in status.
     * @param status the status representation of the transaction
     * @throws TransactionException in case of system errors
     * @see DefaultTransactionStatus#getTransaction
     */
    protected abstract void doRollback(DefaultTransactionStatus status) throws TransactionException;

    /**
     * Set the given transaction rollback-only. Only called on rollback
     * if the current transaction participates in an existing one.
     * <p>The default implementation throws an IllegalTransactionStateException,
     * assuming that participating in existing transactions is generally not
     * supported. Subclasses are of course encouraged to provide such support.
     * @param status the status representation of the transaction
     * @throws TransactionException in case of system errors
     */
    protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
        throw new IllegalTransactionStateException(
                "Participating in existing transactions is not supported - when 'isExistingTransaction' " +
                        "returns true, appropriate 'doSetRollbackOnly' behavior must be provided");
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
    protected void registerAfterCompletionWithExistingTransaction(
            Object transaction, List<TransactionSynchronization> synchronizations) throws TransactionException {

        logger.debug("Cannot register Spring after-completion synchronization with existing transaction - " +
                "processing Spring after-completion callbacks immediately, with outcome status 'unknown'");
        invokeAfterCompletion(synchronizations, TransactionSynchronization.Status.UNKNOWN);
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
     * Perform a rollback, handling rollback exceptions properly.
     * @param status object representing the transaction
     * @param ex the thrown application exception or error
     */
    private void rollbackOnException(TransactionStatus status, Throwable ex) throws
             TransactionException {

        logger.debug("Initiating transaction rollback on application exception", ex);
        try {
            rollback(status);
        } catch (TransactionSystemException ex2) {
            logger.error("Application exception overridden by rollback exception", ex);
            ex2.initApplicationException(ex);
            throw ex2;
        } catch (RuntimeException | Error ex2) {
            logger.error("Application exception overridden by rollback exception", ex);
            throw ex2;
        }
    }

    /**
     * Holder for suspended resources.
     * Used internally by {@code suspend} and {@code resume}.
     */
    protected static final class SuspendedResourcesHolder {

        @Nullable
        private final Object suspendedResources;

        @Nullable
        private List<TransactionSynchronization> suspendedSynchronizations;

        @Nullable
        private String name;

        private boolean readOnly;

        @Nullable
        private TransactionDefinition.Isolation isolationLevel;

        private boolean wasActive;

        private SuspendedResourcesHolder(Object suspendedResources) {
            this.suspendedResources = suspendedResources;
        }

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
