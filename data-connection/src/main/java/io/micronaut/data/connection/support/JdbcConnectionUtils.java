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
package io.micronaut.data.connection.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.exceptions.ConnectionException;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC connection utils.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class JdbcConnectionUtils {

    private JdbcConnectionUtils() {
    }

    /**
     * Apply auto commit value.
     *
     * @param logger              The logger
     * @param connection          The connection
     * @param autoCommit          The autocommit value
     * @param onCompleteCallbacks The callback to restore the original value
     */
    public static void applyAutoCommit(Logger logger,
                                       Connection connection,
                                       boolean autoCommit,
                                       List<Runnable> onCompleteCallbacks) {
        boolean connectionAutoCommit = getAutoCommit(connection);
        // Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
        // so we don't want to do it unnecessarily (for example if we've explicitly
        // configured the connection pool to set it already).
        if (connectionAutoCommit != autoCommit) {
            setAutoCommit(logger, connection, autoCommit);
            onCompleteCallbacks.add(() -> setAutoCommit(logger, connection, connectionAutoCommit));
        }
    }

    /**
     * Apply read only value.
     *
     * @param logger              The logger
     * @param connection          The connection
     * @param isReadOnly          The read only value
     * @param onCompleteCallbacks The callback to restore the original value
     */
    public static void applyReadOnly(Logger logger,
                                     Connection connection,
                                     boolean isReadOnly,
                                     List<Runnable> onCompleteCallbacks) {
        boolean connectionReadOnly = isReadOnly(connection);
        if (connectionReadOnly != isReadOnly) {
            setConnectionReadOnly(logger, connection, isReadOnly);
            onCompleteCallbacks.add(() -> setConnectionReadOnly(logger, connection, connectionReadOnly));
        }
    }

    /**
     * Apply TX isolation level.
     *
     * @param logger              The logger
     * @param connection          The connection
     * @param txIsolationLevel    The TX isolation level
     * @param onCompleteCallbacks The callback to restore the original value
     */
    public static void applyTransactionIsolation(Logger logger,
                                                 Connection connection,
                                                 int txIsolationLevel,
                                                 List<Runnable> onCompleteCallbacks) {
        int connectionTransactionIsolation = getTransactionIsolation(connection);
        if (connectionTransactionIsolation != txIsolationLevel) {
            setTransactionIsolation(logger, connection, txIsolationLevel);
            onCompleteCallbacks.add(() -> setTransactionIsolation(logger, connection, connectionTransactionIsolation));
        }
    }

    /**
     * Apply holdability.
     *
     * @param logger              The logger
     * @param connection          The connection
     * @param holdability         The holdability
     * @param onCompleteCallbacks The callback to restore the original value
     */
    public static void applyHoldability(Logger logger,
                                        Connection connection,
                                        int holdability,
                                        List<Runnable> onCompleteCallbacks) {
        int connectionHoldability = getHoldability(connection);
        if (connectionHoldability != holdability) {
            setHoldability(logger, connection, holdability);
            onCompleteCallbacks.add(() -> setHoldability(logger, connection, connectionHoldability));
        }
    }

    private static int getTransactionIsolation(Connection connection) {
        try {
            return connection.getTransactionIsolation();
        } catch (SQLException e) {
            throw new ConnectionException("Failed to read the connection's transaction isolation value: " + e.getMessage(), e);
        }
    }

    private static int getHoldability(Connection connection) {
        try {
            return connection.getHoldability();
        } catch (SQLException e) {
            throw new ConnectionException("Failed to read the holdability value: " + e.getMessage(), e);
        }
    }

    private static boolean getAutoCommit(Connection connection) {
        try {
            return connection.getAutoCommit();
        } catch (SQLException e) {
            throw new ConnectionException("Failed to read the connection's auto commit value: " + e.getMessage(), e);
        }
    }

    private static boolean isReadOnly(Connection connection) {
        try {
            return connection.isReadOnly();
        } catch (SQLException e) {
            throw new ConnectionException("Failed to read the connection's read only flag: " + e.getMessage(), e);
        }
    }

    private static void setAutoCommit(Logger logger, @NonNull Connection connection, boolean autoCommit) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Setting JDBC Connection [{}] auto-commit [{}]", connection, autoCommit);
            }
            connection.setAutoCommit(autoCommit);
        } catch (SQLException e) {
            logger.debug("Could not set JDBC Connection [{}] auto-commit", connection, e);
            throw new ConnectionException("Could not set JDBC Connection [" + connection + "] read-only: " + e.getMessage(), e);
        }
    }

    private static void setConnectionReadOnly(Logger logger, @NonNull Connection connection, boolean readOnly) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Setting JDBC Connection [{}] read-only [{}]", connection, readOnly);
            }
            connection.setReadOnly(readOnly);
        } catch (SQLException e) {
            logger.debug("Could not set JDBC Connection [{}] read-only", connection, e);
            throw new ConnectionException("Could not set JDBC Connection [" + connection + "] read-only: " + e.getMessage(), e);
        }
    }

    private static void setTransactionIsolation(Logger logger, @NonNull Connection connection, int isolationLevel) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Changing isolation level of JDBC Connection [{}] to {}", connection, isolationLevel);
            }
            connection.setTransactionIsolation(isolationLevel);
        } catch (SQLException e) {
            logger.debug("Cannot change isolation level of JDBC Connection [{}]", connection, e);
            throw new ConnectionException("Cannot change isolation level of JDBC Connection: " + e.getMessage(), e);
        }
    }

    private static void setHoldability(Logger logger, @NonNull Connection connection, int holdability) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Changing holdability of JDBC Connection [{}] to {}", connection, holdability);
            }
            connection.setHoldability(holdability);
        } catch (SQLException e) {
            logger.debug("Cannot change holdability of JDBC Connection [{}]", connection, e);
            throw new ConnectionException("Cannot change holdability of JDBC Connection: " + e.getMessage(), e);
        }
    }

}
