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
package io.micronaut.transaction.jdbc;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.OracleTransactional;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.support.OracleTransactionPriorityStatements;
import io.micronaut.transaction.support.TransactionExecutionListener;
import io.micronaut.transaction.support.TransactionUtil;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleConnection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Locale;

/**
 * Applies Oracle transaction priority for JDBC transactions.
 */
@Internal
@Singleton
@Requires(classes = OracleConnection.class)
final class OracleTransactionPriorityTransactionExecutionListener implements TransactionExecutionListener<Connection> {

    private static final Logger LOG = LoggerFactory.getLogger(OracleTransactionPriorityTransactionExecutionListener.class);
    private static final int ORACLE_INVALID_ALTER_SESSION_OPTION = 2248;
    private static final String ORACLE_PRODUCT_NAME_UPPER = "ORACLE";

    @Override
    public void afterBegin(ConnectionStatus<Connection> connectionStatus, TransactionDefinition definition) {
        if (!definition.getProperties().containsKey(OracleTransactional.ORACLE_PRIORITY)) {
            return;
        }
        Connection connection = connectionStatus.getConnection();
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName != null) {
                productName = productName.toUpperCase(Locale.ENGLISH);
            }
            if (ORACLE_PRODUCT_NAME_UPPER.equals(productName)) {
                OracleTransactional.Priority priority = TransactionUtil.getOraclePriority(definition);
                if (priority != null) {
                    PriorityChange change = applyOracleTxnPriority(connection, priority);
                    if (change.applied()) {
                        // Registered for the transaction: it is reset when the transaction completes,
                        // also when the connection is used by an outer scope
                        connectionStatus.registerSynchronization(new ConnectionSynchronization() {
                            @Override
                            public void executionComplete() {
                                resetOracleTxnPriority(connection, change.previous());
                            }
                        });
                    }
                }
            }
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Could not evaluate/apply Oracle transaction priority", e);
        }
    }

    /**
     * Sets the priority and reads the previous one in one round trip. Reading the priority of the session needs
     * the privilege to read {@code V$PARAMETER}: without it the previous priority isn't known, and the session is
     * reset to {@code HIGH}, the default priority. The query is dynamic, so that a missing privilege fails when it
     * runs, inside the exception handler, not when the block is compiled.
     */
    private static PriorityChange applyOracleTxnPriority(Connection connection, OracleTransactional.Priority level) throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Setting Oracle txn_priority to {}", level);
        }
        try (CallableStatement statement = connection.prepareCall(OracleTransactionPriorityStatements.SET_PRIORITY_READING_PREVIOUS)) {
            statement.setString(1, level.name());
            statement.registerOutParameter(2, Types.VARCHAR);
            statement.execute();
            return new PriorityChange(true, OracleTransactionPriorityStatements.parsePriority(statement.getString(2)));
        } catch (SQLException e) {
            if (isOracleTxnPriorityUnsupported(e)) {
                LOG.debug("Setting Oracle txn_priority failed with ORA-02248; continuing without priority support", e);
                return new PriorityChange(false, null);
            }
            throw e;
        }
    }

    private static void resetOracleTxnPriority(Connection connection, OracleTransactional.@Nullable Priority previous) {
        OracleTransactional.Priority level = previous == null ? OracleTransactional.Priority.HIGH : previous;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resetting Oracle txn_priority to {}", level);
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(OracleTransactionPriorityStatements.setPriority(level));
        } catch (SQLException e) {
            if (isOracleTxnPriorityUnsupported(e)) {
                LOG.debug("Resetting Oracle txn_priority failed with ORA-02248; continuing without priority support", e);
                return;
            }
            throw new TransactionSystemException("Could not reset Oracle transaction priority", e);
        }
    }

    private static boolean isOracleTxnPriorityUnsupported(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            if (current.getErrorCode() == ORACLE_INVALID_ALTER_SESSION_OPTION ||
                (current.getMessage() != null && current.getMessage().contains("ORA-02248"))) {
                return true;
            }
            current = current.getNextException();
        }
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                if (isOracleTxnPriorityUnsupported(sqlException)) {
                    return true;
                }
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * @param applied  Whether the priority was set
     * @param previous The priority of the session before, or {@code null} if it couldn't be read
     */
    private record PriorityChange(boolean applied, OracleTransactional.@Nullable Priority previous) {
    }
}
