package io.micronaut.data.jdbc.runtime.spring;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.transaction.TransactionCallback;
import io.micronaut.data.transaction.TransactionOperations;
import io.micronaut.data.transaction.TransactionStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Default implementation of {@link TransactionOperations} that uses Spring managed transactions.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachBean(DataSourceTransactionManager.class)
@Internal
@Requires(classes = DataSourceTransactionManager.class)
public class SpringJdbcTransactionOperations implements TransactionOperations<Connection> {

    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final DataSource dataSource;

    /**
     * Default constructor.
     * @param transactionManager The transaction manager
     */
    protected SpringJdbcTransactionOperations(
            DataSourceTransactionManager transactionManager) {
        this.dataSource = transactionManager.getDataSource();
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setReadOnly(true);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager, transactionDefinition);
    }

    @Nullable
    @Override
    public <R> R executeWrite(@NonNull TransactionCallback<Connection, R> callback) {
        //noinspection Duplicates
        return writeTransactionTemplate.execute((status) -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                return callback.apply(new JdbcTransactionStatus(connection, status));
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        });
    }

    @Nullable
    @Override
    public <R> R executeRead(@NonNull TransactionCallback<Connection, R> callback) {
        return readTransactionTemplate.execute((status) -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                return callback.apply(new JdbcTransactionStatus(connection, status));
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        });
    }

    @NonNull
    @Override
    public Connection getConnection() {
        return DataSourceUtils.getConnection(dataSource);
    }

    /**
     * Internal transaction status.
     */
    private final class JdbcTransactionStatus implements TransactionStatus<Connection> {

        private final Connection connection;
        private final org.springframework.transaction.TransactionStatus springStatus;

        JdbcTransactionStatus(Connection connection, org.springframework.transaction.TransactionStatus springStatus) {
            this.connection = connection;
            this.springStatus = springStatus;
        }

        @NonNull
        @Override
        public Connection getResource() {
            return connection;
        }

        @Override
        public boolean isNewTransaction() {
            return springStatus.isNewTransaction();
        }

        @Override
        public void setRollbackOnly() {
            springStatus.setRollbackOnly();
        }

        @Override
        public boolean isRollbackOnly() {
            return springStatus.isRollbackOnly();
        }

        @Override
        public boolean isCompleted() {
            return springStatus.isCompleted();
        }
    }
}
