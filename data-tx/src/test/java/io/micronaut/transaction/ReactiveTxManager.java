package io.micronaut.transaction;

import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.support.AbstractReactorTransactionOperations;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

@Singleton
public class ReactiveTxManager extends AbstractReactorTransactionOperations<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveTxManager.class);

    private final OpLogger opLogger;

    protected ReactiveTxManager(ReactorConnectionOperations<String> connectionOperations, OpLogger opLogger) {
        super(connectionOperations);
        this.opLogger = opLogger;
    }

    @Override
    protected Publisher<Void> beginTransaction(ConnectionStatus<String> connectionStatus, TransactionDefinition transactionDefinition) {
        LOGGER.info("Opening transaction for connection: {}", connectionStatus.getConnection());
        opLogger.add("BEGIN TX " + connectionStatus.getConnection());
        return Mono.empty();
    }

    @Override
    protected Publisher<Void> commitTransaction(ConnectionStatus<String> connectionStatus, TransactionDefinition transactionDefinition) {
        LOGGER.info("Commit transaction for connection: {}", connectionStatus.getConnection());
        opLogger.add("COMMIT TX " + connectionStatus.getConnection());
        return Mono.empty();
    }

    @Override
    protected Publisher<Void> rollbackTransaction(ConnectionStatus<String> connectionStatus, TransactionDefinition transactionDefinition) {
        LOGGER.info("Rollback transaction for connection: {}", connectionStatus.getConnection());
        opLogger.add("ROLLBACK TX " + connectionStatus.getConnection());
        return Mono.empty();
    }

    @Override
    protected Publisher<Void> doCancel(DefaultReactiveTransactionStatus<String> connectionStatus) {
        LOGGER.info("Cancel transaction for connection: {}", connectionStatus.getConnection());
        opLogger.add("CANCEL TX " + connectionStatus.getConnection());
        return Mono.empty();
    }
}
