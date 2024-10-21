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
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.connection.jdbc.exceptions.CannotGetJdbcConnectionException;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import io.micronaut.data.connection.support.ConnectionTracingInfo;
import io.micronaut.data.connection.support.JdbcConnectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@link DataSource} connection operations.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@EachBean(DataSource.class)
public final class DefaultDataSourceConnectionOperations extends AbstractConnectionOperations<Connection> {

    private static final String ORACLE_TRACE_CLIENTID = "OCSID.CLIENTID";
    private static final String ORACLE_TRACE_MODULE = "OCSID.MODULE";
    private static final String ORACLE_TRACE_ACTION = "OCSID.ACTION";
    private static final String ORACLE_CONNECTION_DATABASE_PRODUCT_NAME = "Oracle";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDataSourceConnectionOperations.class);
    private final DataSource dataSource;
    private final Map<Connection, Boolean> connectionIsOracleMap = new ConcurrentHashMap<>(20);

    DefaultDataSourceConnectionOperations(DataSource dataSource) {
        this.dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
    }

    @Override
    protected Connection openConnection(ConnectionDefinition definition) {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", e);
        }

        // Set client info for connection if Oracle connection after connection is opened
        ConnectionTracingInfo connectionTracingInfo = definition.connectionTracingInfo();
        if (connectionTracingInfo != null) {
            boolean oracleConnection = connectionIsOracleMap.computeIfAbsent(connection, this::isOracleConnection);
            if (!oracleConnection) {
                LOG.debug("Connection tracing info is supported only for Oracle database connections.");
            } else {
                LOG.trace("Setting connection tracing info to the Oracle connection");
                try {
                    if (connectionTracingInfo.appName() != null) {
                        connection.setClientInfo(ORACLE_TRACE_CLIENTID, connectionTracingInfo.appName());
                    }
                    connection.setClientInfo(ORACLE_TRACE_MODULE, connectionTracingInfo.module());
                    connection.setClientInfo(ORACLE_TRACE_ACTION, connectionTracingInfo.action());
                } catch (SQLClientInfoException e) {
                    LOG.debug("Failed to set connection tracing info", e);
                }
            }
        }
        return connection;
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
    }

    @Override
    protected void closeConnection(ConnectionStatus<Connection> connectionStatus) {
        Connection connection = connectionStatus.getConnection();
        // Clear client info for connection if it was Oracle connection and client info was set previously
        ConnectionTracingInfo connectionTracingInfo = connectionStatus.getDefinition().connectionTracingInfo();
        if (connectionTracingInfo != null) {
            boolean oracleConnection = connectionIsOracleMap.computeIfAbsent(connection, this::isOracleConnection);
            if (oracleConnection) {
                try {
                    connection.setClientInfo(ORACLE_TRACE_CLIENTID, connectionTracingInfo.appName());
                    connection.setClientInfo(ORACLE_TRACE_MODULE, connectionTracingInfo.module());
                    connection.setClientInfo(ORACLE_TRACE_ACTION, connectionTracingInfo.action());
                } catch (SQLClientInfoException e) {
                    LOG.debug("Failed to clear connection tracing info", e);
                }
            }
        }

        try {
            connection.close();
        } catch (SQLException e) {
            throw new ConnectionException("Failed to close the connection: " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether current connection is Oracle database connection.
     *
     * @param connection The connection
     * @return true if current connection is Oracle database connection
     */
    private boolean isOracleConnection(Connection connection) {
        try {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            return StringUtils.isNotEmpty(databaseProductName) && databaseProductName.equalsIgnoreCase(ORACLE_CONNECTION_DATABASE_PRODUCT_NAME);
        } catch (SQLException e) {
            LOG.debug("Failed to get database product name from the connection", e);
            return false;
        }
    }
}
