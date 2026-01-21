package io.micronaut.transaction.jdbc.mock;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.support.ConnectionCustomizer;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.util.function.Function;

@Singleton
@Requires(env = "broken-conn")
public class TestConnectionCustomizer implements ConnectionCustomizer<Connection> {

    private final TestSyncTracker tracker;

    public TestConnectionCustomizer(TestSyncTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public <R> Function<ConnectionStatus<Connection>, R> intercept(Function<ConnectionStatus<Connection>, R> operation) {
        return (ConnectionStatus<Connection> status) -> {
            // Register a tracker that records all callbacks
            status.registerSynchronization(new SyncTrackerSynchronization(tracker, 0));
            // Register a synchronization that throws during executionComplete to simulate failure
            status.registerSynchronization(new ThrowingExecutionCompleteSynchronization(1));
            return operation.apply(status);
        };
    }
}
