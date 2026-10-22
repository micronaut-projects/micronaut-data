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
import io.micronaut.data.connection.exceptions.ConnectionException;
import io.micronaut.data.connection.support.AbstractConnectionOperations;
import io.micronaut.data.connection.support.ConnectionCustomizer;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Marks a request boundary around each connection that Micronaut Data opens, with
 * {@link Connection#beginRequest()} and {@link Connection#endRequest()}.
 *
 * <p>Request boundaries let the driver know when a unit of work starts and ends. Oracle uses them for
 * Transparent Application Continuity, to reset the session state ({@code RESET_STATE}) and, for a
 * connection to a Database Resident Connection Pool (DRCP), to attach and release the pooled server: for
 * DRCP-enabled Oracle connections the customizer also calls {@code attachServerConnection()} and
 * {@code detachServerConnection(null)}. Oracle UCP marks request boundaries itself, and nested calls are
 * ignored, so enabling it with UCP is harmless.</p>
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
        return connectionStatus -> {
            if (!connectionStatus.isNew()) {
                // The request belongs to the scope that opened the connection
                return operation.apply(connectionStatus);
            }
            Connection connection = connectionStatus.getConnection();
            @Nullable OracleConnection drcpConnection = drcpConnection(connection);
            try {
                if (drcpConnection != null) {
                    drcpConnection.attachServerConnection();
                }
                connection.beginRequest();
            } catch (SQLException e) {
                throw new ConnectionException("Failed to begin a request on the connection: " + e.getMessage(), e);
            }
            try {
                return operation.apply(connectionStatus);
            } finally {
                try {
                    connection.endRequest();
                    if (drcpConnection != null) {
                        drcpConnection.detachServerConnection(null);
                    }
                } catch (SQLException e) {
                    LOG.warn("Failed to end the request on the connection: {}", e.getMessage(), e);
                }
            }
        };
    }

    private static @Nullable OracleConnection drcpConnection(Connection connection) {
        if (!ORACLE_JDBC_PRESENT) {
            return null;
        }
        try {
            if (connection.isWrapperFor(OracleConnection.class)) {
                OracleConnection oracleConnection = connection.unwrap(OracleConnection.class);
                if (oracleConnection.isDRCPEnabled()) {
                    return oracleConnection;
                }
            }
        } catch (SQLException e) {
            LOG.debug("Failed to detect a DRCP connection: {}", e.getMessage(), e);
        }
        return null;
    }

    @Override
    public int getOrder() {
        // Customizers wrap in order, so the last one is the outermost: the request then covers the work of
        // every other customizer
        return Ordered.LOWEST_PRECEDENCE;
    }
}
