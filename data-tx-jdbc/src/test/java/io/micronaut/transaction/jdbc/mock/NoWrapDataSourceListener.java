package io.micronaut.transaction.jdbc.mock;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.connection.jdbc.advice.ContextualAwareDataSource;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * Test replacement for ContextualAwareDataSource that disables wrapping DataSource
 * with the transaction-aware proxy, so tests can inject the concrete DataSource
 * implementation (MockDataSource) directly.
 */
@Singleton
@Replaces(ContextualAwareDataSource.class)
@Requires(env = "broken-conn")
public final class NoWrapDataSourceListener implements BeanCreatedEventListener<DataSource> {
    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        // Do not wrap, return the original DataSource bean instance
        return event.getBean();
    }
}
