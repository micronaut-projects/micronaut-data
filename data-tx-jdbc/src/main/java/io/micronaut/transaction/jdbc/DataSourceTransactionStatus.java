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
