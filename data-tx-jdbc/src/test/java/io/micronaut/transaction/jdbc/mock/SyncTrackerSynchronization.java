package io.micronaut.transaction.jdbc.mock;

import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.core.order.Ordered;

public final class SyncTrackerSynchronization implements ConnectionSynchronization, Ordered {
    private final TestSyncTracker tracker;
    private final int order;

    public SyncTrackerSynchronization(TestSyncTracker tracker, int order) {
        this.tracker = tracker;
        this.order = order;
    }

    @Override
    public void executionComplete() {
        tracker.executionComplete.incrementAndGet();
    }

    @Override
    public void beforeClosed() {
        tracker.beforeClosed.incrementAndGet();
    }

    @Override
    public void afterClosed() {
        tracker.afterClosed.incrementAndGet();
    }

    @Override
    public int getOrder() {
        return order;
    }
}
