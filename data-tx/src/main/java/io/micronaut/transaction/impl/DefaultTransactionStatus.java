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

import java.util.function.Function;

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
    private final Function<DefaultTransactionStatus<C>, Object> transactionSupplier;

    private DefaultTransactionStatus(ConnectionStatus<C> connectionStatus,
                                    TransactionDefinition definition) {
        this(connectionStatus, definition, null);
    }

    private DefaultTransactionStatus(ConnectionStatus<C> connectionStatus,
                                    TransactionDefinition definition,
                                    Function<DefaultTransactionStatus<C>, Object> transactionSupplier) {
        this.connectionStatus = connectionStatus;
        this.definition = definition;
        this.transactionSupplier = transactionSupplier;
    }

    public static <C> DefaultTransactionStatus<C> newTx(ConnectionStatus<C> connectionStatus,
                                                        TransactionDefinition definition) {
        return newTx(connectionStatus, definition, null);
    }

    public static <C> DefaultTransactionStatus<C> newTx(ConnectionStatus<C> connectionStatus,
                                                        TransactionDefinition definition,
                                                        @Nullable
                                                        Function<DefaultTransactionStatus<C>, Object> transactionSupplier) {
        return new NewTransactionStatus<>(connectionStatus, definition, transactionSupplier);
    }

    public static <C> DefaultTransactionStatus<C> noTx(ConnectionStatus<C> connectionStatus,
                                                       TransactionDefinition definition) {
        return noTx(connectionStatus, definition, null);
    }

    public static <C> DefaultTransactionStatus<C> noTx(ConnectionStatus<C> connectionStatus,
                                                       TransactionDefinition definition,
                                                       @Nullable
                                                       Function<DefaultTransactionStatus<C>, Object> transactionSupplier) {
        return new NoTxTransactionStatus<>(connectionStatus, definition, transactionSupplier);
    }

    public static <C> DefaultTransactionStatus<C> existingTx(ConnectionStatus<C> connectionStatus, DefaultTransactionStatus<C> existingTransaction) {
        return new ExistingTransactionStatus<>(connectionStatus, existingTransaction, existingTransaction.transactionSupplier);
    }

    @Override
    public Object getTransaction() {
        return transactionSupplier == null ? null : transactionSupplier.apply(this);
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
                                    TransactionDefinition definition,
                                    @Nullable
                                    Function<DefaultTransactionStatus<C>, Object> transactionSupplier) {
            super(connectionStatus, definition, transactionSupplier);
        }

        @Override
        public boolean isNewTransaction() {
            return true;
        }

    }

    private static final class NoTxTransactionStatus<C> extends DefaultTransactionStatus<C> {

        public NoTxTransactionStatus(ConnectionStatus<C> connectionStatus,
                                     TransactionDefinition definition,
                                     @Nullable
                                     Function<DefaultTransactionStatus<C>, Object> transactionSupplier) {
            super(connectionStatus, definition, transactionSupplier);
        }

        @Override
        public boolean isNewTransaction() {
            return false;
        }

    }

    private static final class ExistingTransactionStatus<C> extends DefaultTransactionStatus<C> {

        private final DefaultTransactionStatus<C> existingTransaction;

        public ExistingTransactionStatus(ConnectionStatus<C> connectionStatus,
                                         DefaultTransactionStatus<C> existingTransaction,
                                         @Nullable
                                         Function<DefaultTransactionStatus<C>, Object> transactionSupplier) {
            super(connectionStatus, existingTransaction.getTransactionDefinition(), transactionSupplier);
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
