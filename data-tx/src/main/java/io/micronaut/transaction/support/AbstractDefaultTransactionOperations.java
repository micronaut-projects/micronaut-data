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
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.connection.support.DefaultConnectionStatus;
import io.micronaut.transaction.ExternalTransactionOperations.ExternalTransaction;
import io.micronaut.transaction.ExternalTransactionOperations.ExternalTransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.IllegalTransactionStateException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.exceptions.UnexpectedRollbackException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;


/**
 * Abstract default transaction operations.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class AbstractDefaultTransactionOperations<C> extends AbstractTransactionOperations<DefaultTransactionStatus<C>, C> {

    public AbstractDefaultTransactionOperations(ConnectionOperations<C> connectionOperations,
                                                @Nullable SynchronousConnectionManager<C> synchronousConnectionManager) {
        super(connectionOperations, synchronousConnectionManager);
    }

    @Override
    protected DefaultTransactionStatus<C> createNewTransactionStatus(ConnectionStatus<C> connectionStatus, TransactionDefinition definition) {
        return DefaultTransactionStatus.newTx(connectionStatus, definition, this);
    }

    @Override
    protected DefaultTransactionStatus<C> createExistingTransactionStatus(TransactionDefinition definition, DefaultTransactionStatus<C> existingTransaction) {
        return DefaultTransactionStatus.existingTx(existingTransaction.getConnectionStatus(), definition, existingTransaction, this);
    }

    @Override
    protected DefaultTransactionStatus<C> createNoTxTransactionStatus(ConnectionStatus<C> connectionStatus, TransactionDefinition definition) {
        return DefaultTransactionStatus.noTx(connectionStatus, definition, this);
    }

    /**
     * Binds an externally owned connection as an existing transaction for the duration of the
     * callback. Only subclasses that implement
     * {@link io.micronaut.transaction.ExternalTransactionOperations} expose it; other transaction
     * managers are unaffected.
     *
     * @param connection The externally owned connection
     * @param callback The callback
     * @param <R> The result type
     * @return The callback result
     * @since 5.3.0
     */
    protected <R extends @Nullable Object> R doBindExternal(@NonNull C connection,
                                                            @NonNull ExternalTransactionCallback<C, R> callback) {
        Objects.requireNonNull(connection, "Connection cannot be null");
        Objects.requireNonNull(callback, "Callback cannot be null");
        if (findTransactionStatusInternal().isPresent()) {
            throw new TransactionUsageException("Cannot bind an external connection: a transaction is already active for this transaction manager");
        }
        if (connectionOperations.findConnectionStatus().isPresent()) {
            throw new TransactionUsageException("Cannot bind an external connection: a connection is already active for this transaction manager");
        }
        // Not new: the connection operations never close it
        DefaultConnectionStatus<C> connectionStatus = new DefaultConnectionStatus<>(connection, ConnectionDefinition.DEFAULT, false, connectionOperations);
        // Created by this manager but never begun, committed or rolled back by it:
        // participants see an existing transaction, the owner completes it
        DefaultTransactionStatus<C> transactionStatus = DefaultTransactionStatus.newTx(connectionStatus, TransactionDefinition.DEFAULT, this);
        DefaultExternalTransaction<C> externalTransaction = new DefaultExternalTransaction<>(transactionStatus);
        if (logger.isDebugEnabled()) {
            logger.debug("Binding external connection [{}]", connection);
        }
        try {
            R result = transactionStatus.propagate(() -> {
                try {
                    return callback.call(externalTransaction);
                } catch (Exception e) {
                    return ExceptionUtil.sneakyThrow(e);
                } finally {
                    connectionStatus.complete();
                }
            });
            if (!transactionStatus.isCompleted()) {
                throw new IllegalTransactionStateException("The external transaction owner did not report the outcome with afterCompletion()");
            }
            return result;
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Unbound external connection [{}]", connection);
            }
        }
    }

    /**
     * The default bound external transaction.
     *
     * @param <C> The connection type
     */
    private static final class DefaultExternalTransaction<C> implements ExternalTransaction<C> {

        private final DefaultTransactionStatus<C> status;
        private boolean beforeCompletionInvoked;

        private DefaultExternalTransaction(DefaultTransactionStatus<C> status) {
            this.status = status;
        }

        @Override
        public TransactionStatus<C> getStatus() {
            return status;
        }

        @Override
        public void beforeCommit() {
            checkNotCompleted();
            if (status.isRollbackOnly()) {
                throw new UnexpectedRollbackException("Transaction rolled back because it has been marked as rollback-only");
            }
            status.triggerBeforeCommit();
            beforeCompletionInvoked = true;
            status.triggerBeforeCompletion();
        }

        @Override
        public void afterCompletion(TransactionSynchronization.@NonNull Status outcome) {
            Objects.requireNonNull(outcome, "Outcome cannot be null");
            checkNotCompleted();
            try {
                if (outcome == TransactionSynchronization.Status.COMMITTED) {
                    try {
                        status.triggerAfterCommit();
                    } finally {
                        status.triggerAfterCompletion(outcome);
                    }
                } else {
                    if (!beforeCompletionInvoked) {
                        beforeCompletionInvoked = true;
                        status.triggerBeforeCompletion();
                    }
                    status.triggerAfterCompletion(outcome);
                }
            } finally {
                status.cleanupAfterCompletion();
            }
        }

        private void checkNotCompleted() {
            if (status.isCompleted()) {
                throw new IllegalTransactionStateException("External transaction is already completed");
            }
        }
    }

}
