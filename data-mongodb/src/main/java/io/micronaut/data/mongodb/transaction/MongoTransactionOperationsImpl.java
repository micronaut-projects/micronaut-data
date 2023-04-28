package io.micronaut.data.mongodb.transaction;

import com.mongodb.TransactionOptions;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.connection.manager.synchronous.SynchronousConnectionManager;
import io.micronaut.data.mongodb.conf.RequiresSyncMongo;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.support.AbstractTransactionOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Internal
@RequiresSyncMongo
@EachBean(MongoClient.class)
final class MongoTransactionOperationsImpl extends AbstractTransactionOperations<MongoTransactionStatus, ClientSession> implements MongoTransactionOperations {

    MongoTransactionOperationsImpl(@Parameter ConnectionOperations<ClientSession> connectionOperations,
                                   @Parameter @Nullable SynchronousConnectionManager<ClientSession> synchronousConnectionManager) {
        super(connectionOperations, synchronousConnectionManager);
    }

    @Override
    @NonNull
    public ClientSession getConnection() {
        return connectionOperations.findConnectionStatus()
            .flatMap(status -> status.getConnection().hasActiveTransaction() ? Optional.of(status.getConnection()) : Optional.empty())
            .orElseThrow(() -> new NoTransactionException("No active MongoDB client session!"));
    }

    @Override
    protected void doBegin(MongoTransactionStatus tx) {
        TransactionOptions.Builder txOptionsBuilder = TransactionOptions.builder();
        Duration timeout = determineTimeout(tx.getTransactionDefinition());
        if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
            txOptionsBuilder.maxCommitTime(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        tx.getConnection().startTransaction(txOptionsBuilder.build());
    }

    @Override
    protected void doCommit(MongoTransactionStatus tx) {
        tx.getConnection().commitTransaction();
    }

    @Override
    protected void doRollback(MongoTransactionStatus tx) {
        tx.getConnection().abortTransaction();
    }

    @Override
    protected MongoTransactionStatus createNoTxTransactionStatus(@NonNull ConnectionStatus<ClientSession> connectionStatus,
                                                                 @NonNull TransactionDefinition definition) {
        return MongoTransactionStatus.noTx(connectionStatus, definition);
    }

    @Override
    protected MongoTransactionStatus createNewTransactionStatus(@NonNull ConnectionStatus<ClientSession> connectionStatus,
                                                                @NonNull TransactionDefinition definition) {
        return MongoTransactionStatus.newTx(connectionStatus, definition);
    }

    @Override
    protected MongoTransactionStatus createExistingTransactionStatus(@NonNull ConnectionStatus<ClientSession> connectionStatus,
                                                                     @NonNull TransactionDefinition definition,
                                                                     @NonNull MongoTransactionStatus existingTransaction) {
        return MongoTransactionStatus.existingTx(connectionStatus, existingTransaction);
    }
}
