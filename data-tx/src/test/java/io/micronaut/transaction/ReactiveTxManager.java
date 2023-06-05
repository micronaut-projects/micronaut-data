package io.micronaut.transaction;

import io.micronaut.data.connection.manager.reactive.ReactorReactiveConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.transaction.support.AbstractReactorReactiveTransactionOperations;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class ReactiveTxManager extends AbstractReactorReactiveTransactionOperations<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveTxManager.class);

    private final List<String> transactionsLog = new ArrayList<>();

    protected ReactiveTxManager(ReactorReactiveConnectionOperations<String> connectionOperations) {
        super(connectionOperations);
    }

    public List<String> getTransactionsLog() {
        return transactionsLog;
    }

    @Override
    protected Publisher<Void> beginTransaction(ConnectionStatus<String> connectionStatus, TransactionDefinition transactionDefinition) {
        LOGGER.info("Opening transaction for connection: {}", connectionStatus.getConnection());
        transactionsLog.add("OPEN TX " + connectionStatus.getConnection());
        return Mono.empty();
    }

    @Override
    protected Publisher<Void> commitTransaction(ConnectionStatus<String> connectionStatus, TransactionDefinition transactionDefinition) {
        LOGGER.info("Commit transaction for connection: {}", connectionStatus.getConnection());
        transactionsLog.add("COMMIT TX " + connectionStatus.getConnection());
        return Mono.empty();
    }

    @Override
    protected Publisher<Void> rollbackTransaction(ConnectionStatus<String> connectionStatus, TransactionDefinition transactionDefinition) {
        LOGGER.info("Rollback transaction for connection: {}", connectionStatus.getConnection());
        transactionsLog.add("ROLLBACK TX " + connectionStatus.getConnection());
        return Mono.empty();
    }

    @Override
    protected Publisher<Void> doCancel(DefaultReactiveTransactionStatus<String> connectionStatus) {
        LOGGER.info("Cancel transaction for connection: {}", connectionStatus.getConnection());
        transactionsLog.add("CANCEL TX " + connectionStatus.getConnection());
        return Mono.empty();
    }
}
