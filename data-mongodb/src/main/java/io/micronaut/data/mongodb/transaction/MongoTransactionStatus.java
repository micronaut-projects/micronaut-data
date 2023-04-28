package io.micronaut.data.mongodb.transaction;

import com.mongodb.client.ClientSession;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.impl.AbstractInternalTransaction;
import io.micronaut.transaction.impl.InternalTransaction;

@Internal
abstract sealed class MongoTransactionStatus extends AbstractInternalTransaction<ClientSession> implements InternalTransaction<ClientSession> {

    protected final ConnectionStatus<ClientSession> connectionStatus;
    private final TransactionDefinition definition;

    public MongoTransactionStatus(ConnectionStatus<ClientSession> connectionStatus, TransactionDefinition definition) {
        this.connectionStatus = connectionStatus;
        this.definition = definition;
    }

    public static MongoTransactionStatus newTx(ConnectionStatus<ClientSession> connectionStatus, TransactionDefinition definition) {
        return new NewHibernateTransactionStatus(connectionStatus, definition);
    }

    public static MongoTransactionStatus noTx(ConnectionStatus<ClientSession> connectionStatus, TransactionDefinition definition) {
        return new NoTxMongoTransactionStatus(connectionStatus, definition);
    }

    public static MongoTransactionStatus existingTx(ConnectionStatus<ClientSession> connectionStatus, MongoTransactionStatus existingTransaction) {
        return new ExistingMongoTransactionStatus(connectionStatus, existingTransaction);
    }

    @Override
    public Object getTransaction() {
        return null;
    }

    @Override
    @NonNull
    public ClientSession getConnection() {
        return connectionStatus.getConnection();
    }

    public ConnectionStatus<ClientSession> getConnectionStatus() {
        return connectionStatus;
    }

    public TransactionDefinition getTransactionDefinition() {
        return definition;
    }

    private static final class NewHibernateTransactionStatus extends MongoTransactionStatus {

        public NewHibernateTransactionStatus(ConnectionStatus<ClientSession> connectionStatus, TransactionDefinition definition) {
            super(connectionStatus, definition);
        }

        @Override
        public boolean isNewTransaction() {
            return true;
        }

    }

    private static final class NoTxMongoTransactionStatus extends MongoTransactionStatus {

        public NoTxMongoTransactionStatus(ConnectionStatus<ClientSession> connectionStatus, TransactionDefinition definition) {
            super(connectionStatus, definition);
        }

        @Override
        public boolean isNewTransaction() {
            return false;
        }

    }

    private static final class ExistingMongoTransactionStatus extends MongoTransactionStatus {

        private final MongoTransactionStatus existingTransaction;

        public ExistingMongoTransactionStatus(ConnectionStatus<ClientSession> connectionStatus, MongoTransactionStatus existingTransaction) {
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
