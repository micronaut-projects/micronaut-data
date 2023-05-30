/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.connection.jdbc.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.jdbc.JdbcConnectionDefinition;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.connection.jdbc.exceptions.CannotGetJdbcConnectionException;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.connection.manager.synchronous.ConnectionSynchronization;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import io.micronaut.data.connection.support.JdbcConnectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EachBean(DataSource.class)
public final class DataSourceConnectionOperations extends AbstractConnectionOperations<Connection> {

    private final DataSource dataSource;

    public DataSourceConnectionOperations(DataSource dataSource) {
        this.dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
    }

    @Override
    protected Connection openConnection(ConnectionDefinition definition) {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", e);
        }
    }

    @Override
    protected void setupConnection(ConnectionStatus<Connection> connectionStatus) {
        Connection connection = connectionStatus.getConnection();
        ConnectionDefinition definition = connectionStatus.getDefinition();

        List<Runnable> onComplete = new ArrayList<>(5);

        definition.isReadOnly()
            .ifPresent(readOnly -> JdbcConnectionUtils.applyReadOnly(logger, connection, readOnly, onComplete));

        if (definition instanceof JdbcConnectionDefinition jdbcConnectionDefinition) {
            jdbcConnectionDefinition.autoCommit()
                .ifPresent(autoCommit -> JdbcConnectionUtils.applyAutoCommit(logger, connection, autoCommit, onComplete));
        }

        definition.getIsolationLevel().ifPresent(transactionIsolation -> {
            if (transactionIsolation == ConnectionDefinition.TransactionIsolation.DEFAULT) {
                return;
            }
            int txIsolationLevel = switch (transactionIsolation) {
                case DEFAULT -> throw new IllegalStateException("DEFAULT is not allowed");
                case READ_UNCOMMITTED -> Connection.TRANSACTION_READ_UNCOMMITTED;
                case READ_COMMITTED -> Connection.TRANSACTION_READ_COMMITTED;
                case REPEATABLE_READ -> Connection.TRANSACTION_REPEATABLE_READ;
                case SERIALIZABLE -> Connection.TRANSACTION_SERIALIZABLE;
            };
            JdbcConnectionUtils.applyTransactionIsolation(logger, connection, txIsolationLevel, onComplete);
        });

        if (!onComplete.isEmpty()) {
            Collections.reverse(onComplete);
            connectionStatus.registerSynchronization(new ConnectionSynchronization() {
                @Override
                public void executionComplete() {
                    for (Runnable runnable : onComplete) {
                        runnable.run();
                    }
                }
            });
        }
    }


    @Override
    protected void closeConnection(ConnectionStatus<Connection> connectionStatus) {
        try {
            connectionStatus.getConnection().close();
        } catch (SQLException e) {
            throw new ConnectionException("Failed to close the connection: " + e.getMessage(), e);
        }
    }



}
