package io.micronaut.transaction.jdbc.mock;

import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.core.order.Ordered;

public final class ThrowingExecutionCompleteSynchronization implements ConnectionSynchronization, Ordered {
    private final int order;

    public ThrowingExecutionCompleteSynchronization(int order) {
        this.order = order;
    }

    @Override
    public void executionComplete() {
        // Simulate rollback/reset failure during executionComplete
        throw new RuntimeException("Simulated sync failure at executionComplete");
    }

    @Override
    public int getOrder() {
        return order;
    }
}
