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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.spring.tx.AbstractSpringTransactionOperations;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.exceptions.NoTransactionException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Default implementation of {@link TransactionOperations} that uses Spring managed transactions.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachBean(DataSourceTransactionManager.class)
@Internal
@Requires(classes = DataSourceTransactionManager.class, condition = SpringJdbcTransactionManagerCondition.class)
public final class SpringJdbcTransactionOperations extends AbstractSpringTransactionOperations {

    private final DataSource dataSource;

    /**
     * Default constructor.
     *
     * @param transactionManager The transaction manager
     */
    SpringJdbcTransactionOperations(DataSourceTransactionManager transactionManager) {
        super(transactionManager);
        this.dataSource = transactionManager.getDataSource();
    }

    public DataSource getDataSource() {
        return dataSource;
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

}
