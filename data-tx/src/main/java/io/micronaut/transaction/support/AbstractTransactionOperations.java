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
package io.micronaut.transaction.support;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.IllegalTransactionStateException;
import io.micronaut.transaction.exceptions.NestedTransactionNotSupportedException;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.exceptions.UnexpectedRollbackException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.impl.InternalTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Abstract transaction operations.
 * Partially based on https://github.com/spring-projects/spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/support/AbstractPlatformTransactionManager.java
 *
 * @param <T> The transaction type
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class AbstractTransactionOperations<T extends InternalTransaction<C>, C>
    extends AbstractPropagatedStatusTransactionOperations<T, C> implements TransactionOperations<C>, SynchronousTransactionManager<C> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final ConnectionOperations<C> connectionOperations;
    @Nullable
    protected final SynchronousConnectionManager<C> synchronousConnectionManager;

    public AbstractTransactionOperations(ConnectionOperations<C> connectionOperations,
                                         @Nullable SynchronousConnectionManager<C> synchronousConnectionManager) {
        this.connectionOperations = connectionOperations;
        this.synchronousConnectionManager = synchronousConnectionManager;
    }

    @Override
    public boolean managesTransaction(TransactionStatus<C> transactionStatus) {
        if (transactionStatus instanceof DefaultTransactionStatus<C> status) {
            return status.isTransactionOf(this);
        }
        return false;
    }

    /**
     * Returns connection definition.
     *
     * @param transactionDefinition The transaction definition
     * @return connection definition
     */
    protected ConnectionDefinition getConnectionDefinition(TransactionDefinition transactionDefinition) {
        return transactionDefinition.getConnectionDefinition();
    }

    /**
     * Create transaction status without open transaction.
     *
     * @param connectionStatus The connection status
     * @param definition       The transaction definition
     * @return new transaction status
     */
    protected abstract T createNoTxTransactionStatus(@NonNull ConnectionStatus<C> connectionStatus,
                                                     @NonNull TransactionDefinition definition);

    /**
     * Create transaction status with new transaction.
     *
     * @param connectionStatus The connection status
     * @param definition       The transaction definition
     * @return new transaction status
     */
    protected abstract T createNewTransactionStatus(@NonNull ConnectionStatus<C> connectionStatus,
                                                    @NonNull TransactionDefinition definition);

    /**
     * Create transaction status with existing transaction.
     *
     * @param definition          The transaction definition
     * @param existingTransaction The existing transaction
     * @return new transaction status
     */
    protected abstract T createExistingTransactionStatus(@NonNull TransactionDefinition definition,
                                                         @NonNull T existingTransaction);

    @Override
    public boolean hasConnection() {
        return connectionOperations.findConnectionStatus().isPresent();
    }

    @Override
    protected final <R extends @Nullable Object> R doExecute(@NonNull TransactionDefinition definition, @NonNull TransactionCallback<C, R> callback) {
        if (synchronousConnectionManager == null) {
            return doExecuteWithoutSynchronousConnectionManager(definition, callback);
        }
        return executeTransactional(getTransaction(definition), callback, definition);
    }

    private <R extends @Nullable Object> R doExecuteWithoutSynchronousConnectionManager(TransactionDefinition definition, TransactionCallback<C, R> callback) {
        Optional<T> existingTransactionOptional = findTransactionStatusInternal();
        if (existingTransactionOptional.isEmpty()) {
            return connectionOperations.execute(
                txConnectionDefinition(definition),
                connectionStatus -> executeTransactional(createAndBeginTransaction(definition, connectionStatus), callback, definition)
            );
        }
        T existingTransaction = existingTransactionOptional.get();
        checkNeverTransactionPropagation(definition);
        if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.REQUIRES_NEW ||  definition.getPropagationBehavior() == TransactionDefinition.Propagation.NOT_SUPPORTED) {
            doSuspend(existingTransaction);
            return connectionOperations.execute(
                ConnectionDefinition.REQUIRES_NEW,
                connectionStatus -> {
                    T newTransaction = createAndBeginTransaction(definition, connectionStatus);
                    newTransaction.registerInvocationSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(Status status) {
                            doResume(existingTransaction);
                        }
                    });
                    return executeTransactional(newTransaction, callback, definition);
                }
            );
        }
        T existingTransactionStatus = createExistingTransactionStatus(definition, existingTransaction);
        begin(existingTransactionStatus);
        return executeTransactional(existingTransactionStatus, callback, definition);
    }

    @NonNull
    @Override
    public T getTransaction(TransactionDefinition definition) throws TransactionException {
        boolean debugEnabled = logger.isDebugEnabled();
        if (debugEnabled) {
            logger.debug("Getting transaction for definition [{}]", definition);
        }
        if (synchronousConnectionManager == null) {
            throw new TransactionUsageException("Synchronous connection manager not supported!");
        }
        final T existingTransaction = findTransactionStatusInternal().orElse(null);
        if (existingTransaction == null) {
            if (debugEnabled) {
                logger.debug("Creating a new transaction with definition [{}]", definition);
            }
            ConnectionStatus<C> connectionStatus = connectionOperations.findConnectionStatus().orElse(null);
            if (connectionStatus == null) {
                ConnectionStatus<C> newConnectionStatus = synchronousConnectionManager.getConnection(txConnectionDefinition(definition));
                T transactionStatus = createAndBeginTransaction(definition, newConnectionStatus);
                transactionStatus.registerInvocationSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(Status status) {
                        synchronousConnectionManager.complete(newConnectionStatus);
                    }
                });
                return transactionStatus;
            }
            return createAndBeginTransaction(definition, connectionStatus);
        }
        if (debugEnabled) {
            logger.debug("Found existing transaction [{}]", existingTransaction);
        }
        checkNeverTransactionPropagation(definition);
        if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.REQUIRES_NEW || definition.getPropagationBehavior() == TransactionDefinition.Propagation.NOT_SUPPORTED) {
            doSuspend(existingTransaction);
            ConnectionStatus<C> newConnection = synchronousConnectionManager.getConnection(ConnectionDefinition.REQUIRES_NEW);
            T newTransaction = createAndBeginTransaction(definition, newConnection);
            newTransaction.registerInvocationSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(Status status) {
                    doResume(existingTransaction);
                    synchronousConnectionManager.complete(newConnection);
                }
            });
            return newTransaction;
        }
        T existingTransactionStatus = createExistingTransactionStatus(definition, existingTransaction);
        begin(existingTransactionStatus);
        return existingTransactionStatus;
    }

    /**
     * Begin a new transaction with semantics according to the given transaction
     * definition. Does not have to care about applying the propagation behavior,
     * as this has already been handled by this abstract manager.
     * <p>This method gets called when the transaction manager has decided to actually
     * start a new transaction. Either there wasn't any transaction before, or the
     * previous transaction has been suspended.
     *
     * @param tx The transaction
     */
    protected abstract void doBegin(T tx);

    /**
     * Perform an actual commit of the given transaction.
     * <p>An implementation does not need to check the "new transaction" flag
     * or the rollback-only flag; this will already have been handled before.
     * Usually, a straight commit will be performed on the transaction object
     * contained in the passed-in status.
     *
     * @param tx The transaction
     */
    protected abstract void doCommit(T tx);

    /**
     * Perform an actual rollback of the given transaction.
     * <p>An implementation does not need to check the "new transaction" flag;
     * this will already have been handled before. Usually, a straight rollback
     * will be performed on the transaction object contained in the passed-in status.
     *
     * @param tx The transaction
     */
    protected abstract void doRollback(T tx);

    /**
     * Alternative of the {@link #doBegin(InternalTransaction)} intended for a nested transaction.
     *
     * @param tx The transaction
     */
    protected void doNestedBegin(T tx) {
        throw nestedTxNotSupported();
    }

    /**
     * Alternative of the {@link #doCommit(InternalTransaction)} intended for a nested transaction.
     *
     * @param tx The transaction
     */
    protected void doNestedCommit(T tx) {
        throw nestedTxNotSupported();
    }

    /**
     * Alternative of the {@link #doRollback(InternalTransaction)} intended for a nested transaction.
     *
     * @param tx The transaction
     */
    protected void doNestedRollback(T tx) {
        throw nestedTxNotSupported();
    }

    /**
     * Determine the actual timeout to use for the given definition.
     * Will fall back to this manager's default timeout if the
     * transaction definition doesn't specify a non-default value.
     *
     * @param definition the transaction definition
     * @return the optional timeout to use
     * @see TransactionDefinition#getTimeout()
     */
    protected Optional<Duration> determineTimeout(TransactionDefinition definition) {
        return definition.getTimeout();
    }

    private ConnectionDefinition txConnectionDefinition(TransactionDefinition definition) {
        return getConnectionDefinition(definition);
    }

    private IllegalTransactionStateException newMandatoryTx() {
        return new IllegalTransactionStateException("No existing transaction found for transaction marked with propagation 'mandatory'");
    }

    private NestedTransactionNotSupportedException nestedTxNotSupported() {
        return new NestedTransactionNotSupportedException("Transaction manager does not allow nested transactions");
    }

    /**
     * Do suspend the transaction.
     *
     * @param transaction The transaction
     */
    protected void doSuspend(T transaction) {
    }

    /**
     * Do resume the transaction.
     *
     * @param transaction The transaction
     */
    protected void doResume(T transaction) {
    }

    private <R extends @Nullable Object> R executeTransactional(T transaction, TransactionCallback<C, R> callback, TransactionDefinition definition) {
        R result;
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Executing in a transaction with a definition [{}]", definition);
            }
            result = callback.apply(transaction);
        } catch (Throwable e) {
            if (definition.rollbackOn(e)) {
                rollbackInternal(transaction);
            } else {
                commitInternal(transaction);
            }
            throw e;
        }
        commitInternal(transaction);
        return result;
    }

    private void begin(T transaction) {
        if (transaction.isNewTransaction()) {
            doBegin(transaction);
        } else if (transaction.isNestedTransaction()) {
            doNestedBegin(transaction);
        }
    }

    private void commitInternal(T tx) {
        if (tx.isCompleted()) {
            throw new IllegalTransactionStateException("Transaction is already completed - do not call commit or rollback more than once per transaction");
        }
        if (tx.isLocalRollbackOnly()) {
            rollbackInternal(tx);
            return;
        }
        if (tx.isGlobalRollbackOnly()) {
            rollbackInternal(tx);
            if (logger.isDebugEnabled()) {
                logger.debug("Global transaction is marked as rollback-only but transactional code requested commit");
            }
            throw new UnexpectedRollbackException("Transaction rolled back because it has been marked as rollback-only");
        }
        try {
            boolean beforeCompletionInvoked = false;

            try {
                tx.triggerBeforeCommit();
                tx.triggerBeforeCompletion();

                beforeCompletionInvoked = true;

                if (tx.isNewTransaction()) {
                    doCommit(tx);
                } else if (tx.isNestedTransaction()) {
                    doNestedCommit(tx);
                }

            } catch (UnexpectedRollbackException ex) {
                // can only be caused by doCommit or doNestedCommit
                tx.triggerAfterCompletion(TransactionSynchronization.Status.ROLLED_BACK);
                throw ex;
            } catch (TransactionException ex) {
                // can only be caused by doCommit or doNestedCommit
                if (isRollbackOnCommitFailure()) {
                    doRollbackOnCommitException(tx, ex);
                } else {
                    tx.triggerAfterCompletion(TransactionSynchronization.Status.UNKNOWN);
                }
                throw ex;
            } catch (RuntimeException | Error ex) {
                if (!beforeCompletionInvoked) {
                    tx.triggerBeforeCompletion();
                }
                doRollbackOnCommitException(tx, ex);
                throw ex;
            }

            // Trigger afterCommit callbacks, with an exception thrown there
            // propagated to callers but the transaction still considered as committed.
            try {
                tx.triggerAfterCommit();
            } finally {
                tx.triggerAfterCompletion(TransactionSynchronization.Status.COMMITTED);
            }

        } finally {
            tx.cleanupAfterCompletion();
        }
    }

    private void rollbackInternal(T tx) {
        try {
            try {
                tx.triggerBeforeCompletion();
                if (tx.isNewTransaction()) {
                    doRollback(tx);
                } else if (tx.isNestedTransaction()) {
                    doNestedRollback(tx);
                } else {
                    tx.setRollbackOnly();
                }
            } catch (RuntimeException | Error ex) {
                tx.triggerAfterCompletion(TransactionSynchronization.Status.UNKNOWN);
                throw ex;
            }

            tx.triggerAfterCompletion(TransactionSynchronization.Status.ROLLED_BACK);
        } finally {
            tx.cleanupAfterCompletion();
        }
    }

    private boolean isRollbackOnCommitFailure() {
        return true;
    }

    private void doRollbackOnCommitException(@NonNull T tx, @NonNull Throwable ex) throws TransactionException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Initiating transaction rollback after commit exception", ex);
            }
            // Mirror rollbackInternal's dispatch: nested transactions must roll back
            // to their savepoint (not the entire connection), and non-new/non-nested
            // transactions should only mark rollback-only on the outer transaction.
            if (tx.isNewTransaction()) {
                doRollback(tx);
            } else if (tx.isNestedTransaction()) {
                doNestedRollback(tx);
            } else {
                tx.setRollbackOnly();
            }
        } catch (RuntimeException | Error rbex) {
            logger.error("Commit exception overridden by rollback exception", ex);
            tx.triggerAfterCompletion(TransactionSynchronization.Status.UNKNOWN);
            throw rbex;
        }
        tx.triggerAfterCompletion(TransactionSynchronization.Status.ROLLED_BACK);
    }

    private void checkNeverTransactionPropagation(TransactionDefinition definition) {
        if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.NEVER) {
            throw new TransactionUsageException("Existing transaction found for transaction marked with propagation 'never'");
        }
    }

    private T createAndBeginTransaction(@NonNull TransactionDefinition definition, @NonNull ConnectionStatus<C> connectionStatus) {
        T transaction = createTransaction(definition, connectionStatus);
        begin(transaction);
        return transaction;
    }

    private T createTransaction(@NonNull TransactionDefinition definition, @NonNull ConnectionStatus<C> connectionStatus) {
        return switch (definition.getPropagationBehavior()) {
            case REQUIRED, REQUIRES_NEW, NESTED ->
                createNewTransactionStatus(connectionStatus, definition); // Nested propagation applies only for the existing TX
            case SUPPORTS, NOT_SUPPORTED, NEVER ->
                createNoTxTransactionStatus(connectionStatus, definition);
            case MANDATORY -> throw newMandatoryTx();
        };
    }

    @Override
    public void commit(TransactionStatus<C> status) throws TransactionException {
        if (logger.isDebugEnabled()) {
            logger.debug("Committing transaction status [{}]", status);
        }
        commitInternal((T) status);
    }

    @Override
    public void rollback(TransactionStatus<C> status) throws TransactionException {
        if (logger.isDebugEnabled()) {
            logger.debug("Rolling back transaction status [{}]", status);
        }
        rollbackInternal((T) status);
    }
}
