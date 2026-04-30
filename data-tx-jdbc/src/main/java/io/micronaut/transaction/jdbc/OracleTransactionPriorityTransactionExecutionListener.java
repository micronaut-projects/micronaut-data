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
import io.micronaut.transaction.support.TransactionExecutionListener;
import io.micronaut.transaction.support.TransactionUtil;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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
        OracleTransactional.Priority priority = TransactionUtil.getOraclePriority(definition);
        if (priority == null) {
            return;
        }
        Connection connection = connectionStatus.getConnection();
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName != null) {
                productName = productName.toUpperCase(Locale.ENGLISH);
            }
            if (ORACLE_PRODUCT_NAME_UPPER.equals(productName)) {
                boolean applied = applyOracleTxnPriority(connection, priority);
                if (applied) {
                    connectionStatus.registerSynchronization(new ConnectionSynchronization() {
                        @Override
                        public void executionComplete() {
                            resetOracleTxnPriority(connection);
                        }
                    });
                }
            }
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Could not evaluate/apply Oracle transaction priority", e);
        }
    }

    private static boolean applyOracleTxnPriority(Connection connection, OracleTransactional.Priority level) throws SQLException {
        String sql = "ALTER SESSION SET \"txn_priority\"=\"" + level.name() + "\"";
        return executeOracleTxnPriorityStatement(connection, sql, "Setting", level.name());
    }

    private static void resetOracleTxnPriority(Connection connection) {
        String sql = "ALTER SESSION SET \"txn_priority\"=\"HIGH\"";
        try {
            executeOracleTxnPriorityStatement(connection, sql, "Resetting", "HIGH");
        } catch (SQLException e) {
            throw new TransactionSystemException("Could not reset Oracle transaction priority", e);
        }
    }

    private static boolean executeOracleTxnPriorityStatement(Connection connection,
                                                            String sql,
                                                            String action,
                                                            String level) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Oracle txn_priority to {}", action, level);
            }
            stmt.executeUpdate(sql);
            return true;
        } catch (SQLException e) {
            if (isOracleTxnPriorityUnsupported(e)) {
                LOG.debug("{} Oracle txn_priority failed with ORA-02248; continuing without priority support", action, e);
                return false;
            }
            throw e;
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
}
