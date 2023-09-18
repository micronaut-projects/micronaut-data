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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.TransactionDefinition;

/**
 * The default transaction status.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract sealed class DefaultTransactionStatus<C> extends AbstractInternalTransaction<C> implements InternalTransaction<C> {

    protected final ConnectionStatus<C> connectionStatus;
    private final TransactionDefinition definition;
    @Nullable
    private Object transaction;
    @Nullable
    private Object savepoint;

    private DefaultTransactionStatus(ConnectionStatus<C> connectionStatus,
                                     TransactionDefinition definition) {
        this.connectionStatus = connectionStatus;
        this.definition = definition;
    }

    public static <C> DefaultTransactionStatus<C> newTx(ConnectionStatus<C> connectionStatus,
                                                        TransactionDefinition definition) {
        return new NewTransactionStatus<>(connectionStatus, definition);
    }

    public static <C> DefaultTransactionStatus<C> noTx(ConnectionStatus<C> connectionStatus,
                                                       TransactionDefinition definition) {
        return new NoTxTransactionStatus<>(connectionStatus, definition);
    }

    public static <C> DefaultTransactionStatus<C> existingTx(ConnectionStatus<C> connectionStatus, DefaultTransactionStatus<C> existingTransaction) {
        return new ExistingTransactionStatus<>(connectionStatus, existingTransaction);
    }

    @Override
    public boolean isNestedTransaction() {
        return definition.getPropagationBehavior() == TransactionDefinition.Propagation.NESTED;
    }

    /**
     * Sets the transaction representation object.
     *
     * @param transaction The transaction object
     */
    public void setTransaction(Object transaction) {
        this.transaction = transaction;
    }

    /**
     * Sets the savepoint for nested the transaction.
     * @param savepoint The savepoint
     * @since 4.1.0
     */
    public void setSavepoint(@NonNull Object savepoint) {
        this.savepoint = savepoint;
    }

    /**
     * @return The savepoint
     * @since 4.1.0
     */
    @Nullable
    public Object getSavepoint() {
        return savepoint;
    }

    @Override
    @Nullable
    public Object getTransaction() {
        return transaction;
    }

    @Override
    @NonNull
    public C getConnection() {
        return connectionStatus.getConnection();
    }

    @Override
    public ConnectionStatus<C> getConnectionStatus() {
        return connectionStatus;
    }

    @Override
    public TransactionDefinition getTransactionDefinition() {
        return definition;
    }

    private static final class NewTransactionStatus<C> extends DefaultTransactionStatus<C> {

        public NewTransactionStatus(ConnectionStatus<C> connectionStatus,
                                    TransactionDefinition definition) {
            super(connectionStatus, definition);
        }

        @Override
        public boolean isNewTransaction() {
            return true;
        }

    }

    private static final class NoTxTransactionStatus<C> extends DefaultTransactionStatus<C> {

        public NoTxTransactionStatus(ConnectionStatus<C> connectionStatus,
                                     TransactionDefinition definition) {
            super(connectionStatus, definition);
        }

        @Override
        public boolean isNewTransaction() {
            return false;
        }

    }

    private static final class ExistingTransactionStatus<C> extends DefaultTransactionStatus<C> {

        private final DefaultTransactionStatus<C> existingTransaction;

        public ExistingTransactionStatus(ConnectionStatus<C> connectionStatus,
                                         DefaultTransactionStatus<C> existingTransaction) {
            super(connectionStatus, existingTransaction.getTransactionDefinition());
            this.existingTransaction = existingTransaction;
        }

        @Override
        public boolean isNewTransaction() {
            return false;
        }

        @Override
        public void setRollbackOnly() {
            super.setRollbackOnly();
            existingTransaction.setGlobalRollbackOnly();
        }
    }
}
