package io.micronaut.transaction;

import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.support.AbstractReactorReactiveConnectionOperations;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class ReactiveConnManager extends AbstractReactorReactiveConnectionOperations<String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReactiveTxManager.class);

    private int connectionCount = 0;
    private final List<String> connectionsLog = new ArrayList<>();

    public List<String> getConnectionsLog() {
        return connectionsLog;
    }

    private String newConnection() {
        return "CONNECTION_" + ++connectionCount;
    }

    @Override
    protected Publisher<String> openConnection(ConnectionDefinition definition) {
        String c = newConnection();
        LOGGER.info("Open connection: {}", c);
        connectionsLog.add("OPEN " + c);
        return Mono.just(c);
    }

    @Override
    protected Publisher<Void> closeConnection(String connection, ConnectionDefinition definition) {
        LOGGER.info("Close connection: {}", connection);
        connectionsLog.add("OPEN " + connection);
        return Mono.empty();
    }
}
