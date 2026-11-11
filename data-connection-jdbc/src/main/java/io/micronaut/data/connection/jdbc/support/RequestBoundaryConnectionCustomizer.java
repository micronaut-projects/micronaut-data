/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.connection.jdbc.support;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import io.micronaut.data.connection.support.ConnectionCustomizer;
import oracle.jdbc.OracleConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Marks a request boundary around each connection that Micronaut Data opens, for connection operations and for
 * transactions, with {@link Connection#beginRequest()} when the connection is opened and
 * {@link Connection#endRequest()} before it is closed, after the transaction has completed and the connection
 * settings have been restored.
 *
 * <p>Request boundaries let the driver know when a unit of work starts and ends. Oracle uses them for
 * Transparent Application Continuity and to reset the session state ({@code RESET_STATE}). For a connection to a
 * Database Resident Connection Pool (DRCP), the customizer also releases the pooled server after the request
 * with {@code detachServerConnection(null)}; a released connection attaches again on its next round trip. Oracle
 * UCP marks request boundaries itself, and nested calls are ignored, so enabling it with UCP is harmless.</p>
 *
 * <p>Enabled per data source with {@code datasources.<name>.request-boundaries: true}.</p>
 *
 * @since 5.3.0
 */
@EachBean(DataSource.class)
@Requires(condition = RequestBoundaryCondition.class)
@Context
@Internal
final class RequestBoundaryConnectionCustomizer implements ConnectionCustomizer<Connection> {

    private static final Logger LOG = LoggerFactory.getLogger(RequestBoundaryConnectionCustomizer.class);
    private static final boolean ORACLE_JDBC_PRESENT = ClassUtils.isPresent("oracle.jdbc.OracleConnection", RequestBoundaryConnectionCustomizer.class.getClassLoader());

    RequestBoundaryConnectionCustomizer(@Parameter AbstractConnectionOperations<Connection> connectionOperations) {
        connectionOperations.addConnectionCustomizer(this);
    }

    @Override
    public <R> Function<ConnectionStatus<Connection>, R> intercept(Function<ConnectionStatus<Connection>, R> operation) {
        // The request is marked when the connection is opened, which also covers transactions
        return operation;
    }

    @Override
    public void connectionOpened(ConnectionStatus<Connection> connectionStatus) {
        Connection connection = connectionStatus.getConnection();
        beginRequest(connection);
        connectionStatus.registerSynchronization(new ConnectionSynchronization() {
            @Override
            public void beforeClosed() {
                endRequest(connection);
            }

            @Override
            public int getOrder() {
                // after the other synchronizations, which may still use the connection
                return Ordered.LOWEST_PRECEDENCE;
            }
        });
    }

    static void beginRequest(Connection connection) {
        try {
            connection.beginRequest();
        } catch (SQLException e) {
            throw new ConnectionException("Failed to begin a request on the connection: " + e.getMessage(), e);
        }
    }

    static void endRequest(Connection connection) {
        try {
            connection.endRequest();
        } catch (SQLException e) {
            LOG.warn("Failed to end the request on the connection: {}", e.getMessage(), e);
        } finally {
            // release the DRCP server even when ending the request failed
            if (ORACLE_JDBC_PRESENT) {
                OracleDrcp.release(connection);
            }
        }
    }

    @Override
    public int getOrder() {
        // connectionOpened runs in order, so the request begins before the other customizers' work on the
        // connection; it ends in its own LOWEST_PRECEDENCE synchronization, after theirs
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * The Oracle JDBC specific code, loaded only when the driver is present.
     */
    private static final class OracleDrcp {

        static void release(Connection connection) {
            try {
                if (connection.isWrapperFor(OracleConnection.class)) {
                    OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
                    if (oracleConnection.isDRCPEnabled()) {
                        oracleConnection.detachServerConnection(null);
                    }
                }
            } catch (SQLException e) {
                LOG.warn("Failed to release the DRCP server of the connection: {}", e.getMessage(), e);
            }
        }
    }
}
