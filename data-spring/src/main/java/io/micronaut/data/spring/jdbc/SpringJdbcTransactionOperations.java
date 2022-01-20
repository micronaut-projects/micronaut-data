/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.spring.jdbc;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.UndeclaredThrowableException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

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
    private final DataSourceTransactionManager transactionManager;

    /**
     * Default constructor.
     * @param transactionManager The transaction manager
     */
    protected SpringJdbcTransactionOperations(
            DataSourceTransactionManager transactionManager) {
        this.dataSource = transactionManager.getDataSource();
        this.transactionManager = transactionManager;
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setReadOnly(true);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager, transactionDefinition);
    }

    @Override
    public <R> R executeRead(@NonNull TransactionCallback<Connection, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        return readTransactionTemplate.execute(status -> {
                    try {
                        return callback.call(new JdbcTransactionStatus(status));
                    } catch (RuntimeException | Error ex) {
                        throw ex;
                    } catch (Exception e) {
                        throw new UndeclaredThrowableException(e, "TransactionCallback threw undeclared checked exception");
                    }
                }
        );
    }

    @Override
    public <R> R executeWrite(@NonNull TransactionCallback<Connection, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        return writeTransactionTemplate.execute(status -> {
                    try {
                        return callback.call(new JdbcTransactionStatus(status));
                    } catch (RuntimeException | Error ex) {
                        throw ex;
                    } catch (Exception e) {
                        throw new UndeclaredThrowableException(e, "TransactionCallback threw undeclared checked exception");
                    }
                }
        );
    }

    @Override
    public <R> R execute(@NonNull TransactionDefinition definition, @NonNull TransactionCallback<Connection, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        ArgumentUtils.requireNonNull("definition", definition);

        final DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setReadOnly(definition.isReadOnly());
        def.setIsolationLevel(definition.getIsolationLevel().getCode());
        def.setPropagationBehavior(definition.getPropagationBehavior().ordinal());
        def.setName(definition.getName());
        final Duration timeout = definition.getTimeout();
        if (!timeout.isNegative()) {
            def.setTimeout((int) timeout.getSeconds());
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager, def);
        return template.execute(status -> {
                    try {
                        return callback.call(new JdbcTransactionStatus(status));
                    } catch (RuntimeException | Error ex) {
                        throw ex;
                    } catch (Exception e) {
                        throw new UndeclaredThrowableException(e, "TransactionCallback threw undeclared checked exception");
                    }
                }
        );
    }

    @NonNull
    @Override
    public Connection getConnection() {
        try {
            Connection connection = DataSourceUtils.doGetConnection(dataSource);
            if (DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                return connection;
            } else {
                connection.close();
                throw new NoTransactionException("No transaction declared. Define @Transactional on the surrounding method prior to calling getConnection()");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error retrieving JDBC connection: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean hasConnection() {
        ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        return conHolder != null && conHolder.getConnectionHandle() != null;
    }

    /**
     * Internal transaction status.
     */
    private final class JdbcTransactionStatus implements TransactionStatus<Connection> {

        private final org.springframework.transaction.TransactionStatus springStatus;

        JdbcTransactionStatus(org.springframework.transaction.TransactionStatus springStatus) {
            this.springStatus = springStatus;
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

        @Override
        public boolean hasSavepoint() {
            return springStatus.hasSavepoint();
        }

        @Override
        public void flush() {
            springStatus.flush();
        }

        @NonNull
        @Override
        public Object getTransaction() {
            return springStatus;
        }

        @NonNull
        @Override
        public Connection getConnection() {
            return SpringJdbcTransactionOperations.this.getConnection();
        }

        @Override
        public Object createSavepoint() throws TransactionException {
            return springStatus.createSavepoint();
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) throws TransactionException {
            springStatus.rollbackToSavepoint(savepoint);
        }

        @Override
        public void releaseSavepoint(Object savepoint) throws TransactionException {
            springStatus.releaseSavepoint(savepoint);
        }
    }
}
