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
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.connection.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.connection.jdbc.exceptions.CannotGetJdbcConnectionException;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import io.micronaut.data.connection.support.ConnectionClientInformation;
import io.micronaut.data.connection.support.JdbcConnectionUtils;
import io.micronaut.data.model.query.builder.sql.Dialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@link DataSource} connection operations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@EachBean(DataSource.class)
public final class DefaultDataSourceConnectionOperations extends AbstractConnectionOperations<Connection> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDataSourceConnectionOperations.class);
    private final DataSource dataSource;
    private final DataJdbcConfiguration dataJdbcConfiguration;

    DefaultDataSourceConnectionOperations(DataSource dataSource,
                                          @Parameter DataJdbcConfiguration dataJdbcConfiguration) {
        this.dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
        this.dataJdbcConfiguration = dataJdbcConfiguration;
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
        ConnectionDefinition connectionDefinition = connectionStatus.getDefinition();
        connectionDefinition.isReadOnly().ifPresent(readOnly -> {
            List<Runnable> onCompleteCallbacks = new ArrayList<>(1);
            JdbcConnectionUtils.applyReadOnly(LOG, connectionStatus.getConnection(), readOnly, onCompleteCallbacks);
            if (!onCompleteCallbacks.isEmpty()) {
                connectionStatus.registerSynchronization(new ConnectionSynchronization() {
                    @Override
                    public void executionComplete() {
                        for (Runnable onCompleteCallback : onCompleteCallbacks) {
                            onCompleteCallback.run();
                        }
                    }
                });
            }
        });
        if (!dataJdbcConfiguration.isClientInfoTracing()) {
            return;
        }
        if (dataJdbcConfiguration.getDialect() != Dialect.ORACLE) {
            LOG.warn("Client info tracing is supported only for Oracle database connections.");
            return;
        }
        ConnectionClientInformation connectionClientInformation = connectionDefinition.connectionClientInformation();
        if (connectionClientInformation == null) {
            LOG.warn("ConnectionClientInformation not provided for the connection.");
            return;
        }
        LOG.debug("Setting client info to the Oracle connection");
        Connection conn = connectionStatus.getConnection();

        try {
            if (connectionClientInformation.clientId() != null) {
                conn.setClientInfo("OCSID.CLIENTID", connectionClientInformation.clientId());
            }
            conn.setClientInfo("OCSID.MODULE", connectionClientInformation.module());
            conn.setClientInfo("OCSID.ACTION", connectionClientInformation.action());
        } catch (SQLClientInfoException e) {
            LOG.warn("Failed to set client info", e);
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
