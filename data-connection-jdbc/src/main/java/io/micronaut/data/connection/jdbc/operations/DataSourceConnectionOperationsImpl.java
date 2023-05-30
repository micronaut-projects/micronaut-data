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
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.jdbc.advice.ContextualConnectionProvider;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.connection.jdbc.exceptions.CannotGetJdbcConnectionException;
import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import io.micronaut.data.connection.support.AbstractConnectionOperations;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * The {@link DataSource} connection operations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@EachBean(DataSource.class)
public final class DataSourceConnectionOperationsImpl extends AbstractConnectionOperations<Connection> implements DataSourceConnectionOperations, ContextualConnectionProvider {

    private final DataSource dataSource;

    DataSourceConnectionOperationsImpl(DataSource dataSource) {
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
    }

    @Override
    protected void closeConnection(ConnectionStatus<Connection> connectionStatus) {
        try {
            connectionStatus.getConnection().close();
        } catch (SQLException e) {
            throw new ConnectionException("Failed to close the connection: " + e.getMessage(), e);
        }
    }

    @Override
    public Connection find() {
        return findConnectionStatus().map(ConnectionStatus::getConnection).orElse(null);
    }
}
