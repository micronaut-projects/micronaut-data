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
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.support.TransactionSynchronization;

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
    private final TransactionOperations<C> transactionOperations;
    @Nullable
    private Object transaction;
    @Nullable
    private Object savepoint;

    private DefaultTransactionStatus(ConnectionStatus<C> connectionStatus,
                                     TransactionDefinition definition,
                                     TransactionOperations<C> transactionOperations) {
        this.connectionStatus = connectionStatus;
        this.definition = definition;
        this.transactionOperations = transactionOperations;
    }

    public static <C> DefaultTransactionStatus<C> newTx(ConnectionStatus<C> connectionStatus,
                                                        TransactionDefinition definition,
                                                        TransactionOperations<C> transactionOperations) {
        return new NewTransactionStatus<>(connectionStatus, definition, transactionOperations);
    }

    public static <C> DefaultTransactionStatus<C> noTx(ConnectionStatus<C> connectionStatus,
                                                       TransactionDefinition definition,
                                                       TransactionOperations<C> transactionOperations) {
        return new NoTxTransactionStatus<>(connectionStatus, definition, transactionOperations);
    }

    public static <C> DefaultTransactionStatus<C> existingTx(ConnectionStatus<C> connectionStatus,
                                                             DefaultTransactionStatus<C> existingTransaction,
                                                             TransactionOperations<C> transactionOperations) {
        return new ExistingTransactionStatus<>(connectionStatus, existingTransaction, transactionOperations);
    }

    public boolean isTransactionOf(TransactionOperations<C> transactionOperations) {
        return this.transactionOperations ==  transactionOperations;
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
    public @NotNull C getConnection() {
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

    @Override
    public String toString() {
        return "DefaultTransactionStatus{" +
            "connectionStatus=" + connectionStatus +
            ", definition=" + definition +
            ", transaction=" + transaction +
            ", savepoint=" + savepoint +
            ", synchronizations=" + synchronizations +
            '}';
    }

    private static final class NewTransactionStatus<C> extends DefaultTransactionStatus<C> {

        private NewTransactionStatus(ConnectionStatus<C> connectionStatus,
                                    TransactionDefinition definition,
                                    TransactionOperations<C> transactionOperations) {
            super(connectionStatus, definition, transactionOperations);
        }

        @Override
        public boolean isNewTransaction() {
            return true;
        }

        @Override
        public String toString() {
            return "NewTransactionStatus{" +
                "transaction=" + getTransaction() +
                ", connectionStatus=" + connectionStatus +
                ", definition=" + getTransactionDefinition() +
                ", savepoint=" + getSavepoint() +
                ", synchronizations=" + synchronizations +
                '}';
        }
    }

    private static final class NoTxTransactionStatus<C> extends DefaultTransactionStatus<C> {

        private NoTxTransactionStatus(ConnectionStatus<C> connectionStatus,
                                     TransactionDefinition definition,
                                     TransactionOperations<C> transactionOperations) {
            super(connectionStatus, definition, transactionOperations);
        }

        @Override
        public boolean isNewTransaction() {
            return false;
        }

        @Override
        public String toString() {
            return "NoTxTransactionStatus{}";
        }
    }

    private static final class ExistingTransactionStatus<C> extends DefaultTransactionStatus<C> {

        private final DefaultTransactionStatus<C> existingTransaction;

        private ExistingTransactionStatus(ConnectionStatus<C> connectionStatus,
                                         DefaultTransactionStatus<C> existingTransaction,
                                         TransactionOperations<C> transactionOperations) {
            super(connectionStatus, existingTransaction.getTransactionDefinition(), transactionOperations);
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

        @Override
        public void registerSynchronization(TransactionSynchronization synchronization) {
            // The synchronization should be bound to the current TX
            existingTransaction.registerSynchronization(synchronization);
        }

        @Override
        public String toString() {
            return "ExistingTransactionStatus{" +
                "existingTransaction=" + existingTransaction +
                '}';
        }
    }
}
