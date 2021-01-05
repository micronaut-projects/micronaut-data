package io.micronaut.data.spring.tx;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jdbc.spring.DataSourceTransactionManagerFactory;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Disables the transaction aware listener provided by DataSourceTransactionManagerFactory.
 *
 * @author graemerocher
 * @since 2.2.2
 */
@Singleton
@Internal
@Replaces(factory = DataSourceTransactionManagerFactory.class, bean = BeanCreatedEventListener.class)
final class TransactionAwareDataSourceListenerReplacement implements BeanCreatedEventListener<DataSource> {
    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        return event.getBean();
    }
}
