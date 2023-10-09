package io.micronaut.transaction;

import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.support.AbstractReactorConnectionOperations;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@Singleton
public class ReactiveConnManager extends AbstractReactorConnectionOperations<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveTxManager.class);

    private int connectionCount = 0;
    private final OpLogger opLogger;

    public ReactiveConnManager(OpLogger opLogger) {
        this.opLogger = opLogger;
    }

    private String newConnection() {
        return "CONNECTION_" + ++connectionCount;
    }

    @Override
    protected Publisher<String> openConnection(ConnectionDefinition definition) {
        String c = newConnection();
        LOGGER.info("Open connection: {}", c);
        opLogger.add("OPEN " + c);
        return Mono.just(c);
    }

    @Override
    protected Publisher<Void> closeConnection(String connection, ConnectionDefinition definition) {
        LOGGER.info("Close connection: {}", connection);
        opLogger.add("CLOSE " + connection);
        return Mono.empty();
    }
}
