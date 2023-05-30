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
package io.micronaut.transaction.jdbc;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.impl.AbstractInternalTransaction;
import io.micronaut.transaction.impl.InternalTransaction;

import java.sql.Connection;


@Internal
abstract sealed class DataSourceTransactionStatus extends AbstractInternalTransaction<Connection> implements InternalTransaction<Connection> {

    protected final ConnectionStatus<Connection> connectionStatus;
    private final TransactionDefinition definition;

    public DataSourceTransactionStatus(ConnectionStatus<Connection> connectionStatus, TransactionDefinition definition) {
        this.connectionStatus = connectionStatus;
        this.definition = definition;
    }

    public static DataSourceTransactionStatus newTx(ConnectionStatus<Connection> connectionStatus,
                                                    TransactionDefinition definition) {
        return new NewDataSourceTransactionStatus(connectionStatus, definition);
    }

    public static DataSourceTransactionStatus noTx(ConnectionStatus<Connection> connectionStatus,
                                                   TransactionDefinition definition) {
        return new NoTxDataSourceTransactionStatus(connectionStatus, definition);
    }

    public static DataSourceTransactionStatus existingTx(ConnectionStatus<Connection> connectionStatus,
                                                         DataSourceTransactionStatus existingTransaction) {
        return new ExistingDataSourceTransactionStatus(connectionStatus, existingTransaction);
    }

    @Override
    public Object getTransaction() {
        return null;
    }

    @Override
    @NonNull
    public Connection getConnection() {
        return connectionStatus.getConnection();
    }

    public ConnectionStatus<Connection> getConnectionStatus() {
        return connectionStatus;
    }

    public TransactionDefinition getTransactionDefinition() {
        return definition;
    }

    private static final class NewDataSourceTransactionStatus extends DataSourceTransactionStatus {

        public NewDataSourceTransactionStatus(ConnectionStatus<Connection> connectionStatus,
                                              TransactionDefinition definition) {
            super(connectionStatus, definition);
        }

        @Override
        public boolean isNewTransaction() {
            return true;
        }

    }

    private static final class NoTxDataSourceTransactionStatus extends DataSourceTransactionStatus {

        public NoTxDataSourceTransactionStatus(ConnectionStatus<Connection> connectionStatus,
                                               TransactionDefinition definition) {
            super(connectionStatus, definition);
        }

        @Override
        public boolean isNewTransaction() {
            return false;
        }

    }

    private static final class ExistingDataSourceTransactionStatus extends DataSourceTransactionStatus {

        private final DataSourceTransactionStatus existingTransaction;

        public ExistingDataSourceTransactionStatus(ConnectionStatus<Connection> connectionStatus,
                                                   DataSourceTransactionStatus existingTransaction) {
            super(connectionStatus, existingTransaction.getTransactionDefinition());
            this.existingTransaction = existingTransaction;
        }

        @Override
        public boolean isNewTransaction() {
            return false;
        }

        @Override
        public boolean isReadOnly() {
            return existingTransaction.isReadOnly();
        }

        @Override
        public void setRollbackOnly() {
            existingTransaction.setRollbackOnly();
        }
    }
}
