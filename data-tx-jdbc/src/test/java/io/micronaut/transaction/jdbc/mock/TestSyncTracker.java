package io.micronaut.transaction.jdbc.mock;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Requires(env = "broken-conn")
public class TestSyncTracker {
    public final AtomicInteger executionComplete = new AtomicInteger();
    public final AtomicInteger beforeClosed = new AtomicInteger();
    public final AtomicInteger afterClosed = new AtomicInteger();

    public void reset() {
        executionComplete.set(0);
        beforeClosed.set(0);
        afterClosed.set(0);
    }
}
