package io.micronaut.transaction.jdbc;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Transaction aware data source implementation.
 *
 * @author graemerocher
 * @since 1.0.1
 */
@Singleton
@Requires(missingClasses = "org.springframework.jdbc.datasource.DataSourceTransactionManager")
public class TransactionAwareDataSource implements BeanCreatedEventListener<DataSource> {
    private final BeanLocator beanLocator;

    private Connection transactionAwareConnection;
    private String qualifier;


    /**
     * Create a new DelegatingDataSource.
     *
     * @param beanLocator The bean locator
     */
    public TransactionAwareDataSource(
            BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        final BeanIdentifier beanIdentifier = event.getBeanIdentifier();
        String name = beanIdentifier.getName();
        if (name.equalsIgnoreCase("primary")) {
            name = "default";
        }
        this.qualifier = name;
        return new DataSourceProxy(event.getBean());
    }

    private Connection getTransactionAwareConnection() {
        if (transactionAwareConnection == null) {
            transactionAwareConnection
                    = beanLocator.getBean(Connection.class, Qualifiers.byName(qualifier));

        }
        return transactionAwareConnection;
    }

    /**
     * The transaction aware proxy implementation.
     *
     * @author graemerocher
     * @since 1.0.1
     */
    private final class DataSourceProxy extends DelegatingDataSource {


        /**
         * Create a new DelegatingDataSource.
         *
         * @param targetDataSource the target DataSource
         */
        DataSourceProxy(@NonNull DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return getTransactionAwareConnection();
        }
    }
}
