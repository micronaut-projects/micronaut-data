package io.micronaut.data.jdbc;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations;
import io.micronaut.data.tck.tests.AbstractTransactionSpec;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;

import java.sql.Connection;

abstract class AbstractJdbcTransactionTest extends AbstractTransactionSpec {

    @Override
    protected TransactionOperations getTransactionOperations() {
        return applicationContext().getBean(DataSourceTransactionManager.class);
    }

    @Override
    protected ConnectionOperations getConnectionOperations() {
        return applicationContext().getBean(DefaultDataSourceConnectionOperations.class);
    }

    @Override
    protected Runnable getNoTxCheck() {
        DefaultDataSourceConnectionOperations connectionOperations = applicationContext().getBean(DefaultDataSourceConnectionOperations.class);
        return () -> {
            var status = connectionOperations.findConnectionStatus();
            if (status.isEmpty()) {
                return;
            }
            Connection connection = (Connection) status.get().getConnection();
            try {
                assert connection.getAutoCommit();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private ApplicationContext applicationContext() {
        try {
            var field = AbstractTransactionSpec.class.getDeclaredField("context");
            field.setAccessible(true);
            return (ApplicationContext) field.get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access AbstractTransactionSpec context", e);
        }
    }
}
