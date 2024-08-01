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
package io.micronaut.transaction.jdbc;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionSynchronization;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.connection.support.JdbcConnectionUtils;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.support.AbstractDefaultTransactionOperations;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The {@link DataSource} transaction manager.
 * Partially based on https://github.com/spring-projects/spring-framework/blob/main/spring-jdbc/src/main/java/org/springframework/jdbc/datasource/DataSourceTransactionManager.java
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@EachBean(DataSource.class)
@Requires(condition = JdbcTransactionManagerCondition.class)
@TypeHint(DataSourceTransactionManager.class)
public final class DataSourceTransactionManager extends AbstractDefaultTransactionOperations<Connection> {

    // Error with this message is thrown from SQL server when operation is not supported (like Connection.releaseSavepoint)
    private static final String OPERATION_NOT_SUPPORTED = "This operation is not supported.";
    private final DataSource dataSource;

    private boolean enforceReadOnly = false;

    /**
     * Create a new DataSourceTransactionManager instance.
     *
     * @param dataSource                   the JDBC DataSource to manage transactions for
     * @param connectionOperations         the connection operations
     * @param synchronousConnectionManager the synchronous connection operations
     */
    public DataSourceTransactionManager(@NonNull DataSource dataSource,
                                        @Parameter ConnectionOperations<Connection> connectionOperations,
                                        @Parameter @Nullable SynchronousConnectionManager<Connection> synchronousConnectionManager) {
        super(connectionOperations, synchronousConnectionManager);
        Objects.requireNonNull(dataSource, "DataSource cannot be null");
        dataSource = DelegatingDataSource.unwrapDataSource(dataSource);
        this.dataSource = dataSource;
    }

    /**
     * @return Return the JDBC DataSource that this instance manages transactions for.
     */
    @NonNull
    public DataSource getDataSource() {
        return this.dataSource;
    }

    /**
     * Specify whether to enforce the read-only nature of a transaction
     * (as indicated by {@link TransactionDefinition#isReadOnly()})
     * through an explicit statement on the transactional connection:
     * "SET TRANSACTION READ ONLY" as understood by Oracle, MySQL and Postgres.
     * <p>The exact treatment, including any SQL statement executed on the connection,
     * can be customized through {@link #prepareTransactionalConnection}.
     * <p>This mode of read-only handling goes beyond the {@link Connection#setReadOnly}
     * hint that Spring applies by default. In contrast to that standard JDBC hint,
     * "SET TRANSACTION READ ONLY" enforces an isolation-level-like connection mode
     * where data manipulation statements are strictly disallowed. Also, on Oracle,
     * this read-only mode provides read consistency for the entire transaction.
     * <p>Note that older Oracle JDBC drivers (9i, 10g) used to enforce this read-only
     * mode even for {@code Connection.setReadOnly(true}. However, with recent drivers,
     * this strong enforcement needs to be applied explicitly, e.g. through this flag.
     *
     * @param enforceReadOnly True if read-only should be enforced
     * @see #prepareTransactionalConnection
     * @since 4.3.7
     */
    public void setEnforceReadOnly(boolean enforceReadOnly) {
        this.enforceReadOnly = enforceReadOnly;
    }

    /**
     * @return Return whether to enforce the read-only nature of a transaction
     * through an explicit statement on the transactional connection.
     * @see #setEnforceReadOnly
     * @since 4.3.7
     */
    public boolean isEnforceReadOnly() {
        return this.enforceReadOnly;
    }

    @Override
    protected void doBegin(DefaultTransactionStatus<Connection> status) {
        TransactionDefinition definition = status.getTransactionDefinition();
        Connection connection = status.getConnection();

        List<Runnable> onComplete = new ArrayList<>(5);

        definition.isReadOnly()
            .ifPresent(readOnly -> JdbcConnectionUtils.applyReadOnly(logger, connection, readOnly, onComplete));
        definition.getIsolationLevel()
            .ifPresent(isolation -> JdbcConnectionUtils.applyTransactionIsolation(logger, connection, isolation.getCode(), onComplete));
        JdbcConnectionUtils.applyAutoCommit(logger, connection, false, onComplete);

        //        prepareTransactionalConnection(connection, definition);

        if (!onComplete.isEmpty()) {
            Collections.reverse(onComplete);
            status.getConnectionStatus().registerSynchronization(new ConnectionSynchronization() {
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
    protected void doCommit(DefaultTransactionStatus<Connection> status) {
        Connection connection = status.getConnection();
        if (logger.isDebugEnabled()) {
            logger.debug("Committing JDBC transaction on Connection [{}]", connection);
        }
        try {
            connection.commit();
        } catch (SQLException ex) {
            throw new TransactionSystemException("Could not commit JDBC transaction", ex);
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus<Connection> status) {
        Connection connection = status.getConnection();
        if (logger.isDebugEnabled()) {
            logger.debug("Rolling back JDBC transaction on Connection [{}]", connection);
        }
        try {
            connection.rollback();
        } catch (SQLException ex) {
            throw new TransactionSystemException("Could not roll back JDBC transaction", ex);
        }
    }

    @Override
    protected void doNestedBegin(DefaultTransactionStatus<Connection> status) {
        try {
            Connection connection = status.getConnection();
            Savepoint savepoint = connection.setSavepoint();
            status.setSavepoint(savepoint);
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("Could not create JDBC savepoint", e);
        }
    }

    @Override
    protected void doNestedCommit(DefaultTransactionStatus<Connection> status) {
        if (status.getSavepoint() != null) {
            Connection connection = status.getConnection();
            if (logger.isDebugEnabled()) {
                logger.debug("Releasing JDBC savepoint on Connection [{}]", connection);
            }
            try {
                connection.releaseSavepoint((Savepoint) status.getSavepoint());
            } catch (Exception e) {
                if (isUnsupportedOperation(e)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("JDBC SavePoint release not supported by the Connection [{}]", connection, e);
                    }
                } else {
                    throw new TransactionSystemException("Could not release JDBC savepoint", e);
                }
            }
        } else {
            throw new TransactionSystemException("Missing a JDBC savepoint");
        }
    }

    @Override
    protected void doNestedRollback(DefaultTransactionStatus<Connection> status) {
        if (status.getSavepoint() != null) {
            Connection connection = status.getConnection();
            if (logger.isDebugEnabled()) {
                logger.debug("Rolling back JDBC transaction to the savepoint on Connection [{}]", connection);
            }
            try {
                connection.rollback((Savepoint) status.getSavepoint());
            } catch (Exception e) {
                throw new TransactionSystemException("Could not roll back to JDBC savepoint", e);
            }
        } else {
            throw new TransactionSystemException("Missing a JDBC savepoint");
        }
    }

    /**
     * Prepare the transactional {@code Connection} right after transaction begin.
     * <p>The default implementation executes a "SET TRANSACTION READ ONLY" statement
     * if the {@link #setEnforceReadOnly "enforceReadOnly"} flag is set to {@code true}
     * and the transaction definition indicates a read-only transaction.
     * <p>The "SET TRANSACTION READ ONLY" is understood by Oracle, MySQL and Postgres
     * and may work with other databases as well. If you'd like to adapt this treatment,
     * override this method accordingly.
     *
     * @param con        the transactional JDBC Connection
     * @param definition the current transaction definition
     * @throws SQLException if thrown by JDBC API
     * @see #setEnforceReadOnly
     * @since 4.3.7
     */
    protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition)
            throws SQLException {

        if (isEnforceReadOnly() && definition.isReadOnly().orElse(false)) {
            try (Statement stmt = con.createStatement()) {
                stmt.executeUpdate("SET TRANSACTION READ ONLY");
            }
        }
    }

    @NonNull
    @Override
    public Connection getConnection() {
        return connectionOperations.getConnectionStatus().getConnection();
    }

    /**
     * Checks if thrown exception is from the JDBC driver telling that feature is not supported.
     * For example, some drivers don't support {@link Connection#releaseSavepoint(Savepoint)}
     * and we want to handle that case and continue execution.
     *
     * @param exception The thrown exception
     * @return true if exception is thrown for unsupported operation
     */
    private static boolean isUnsupportedOperation(Exception exception) {
        if (exception instanceof SQLFeatureNotSupportedException) {
            return true;
        } else if (exception instanceof SQLException sqlException) {
            return OPERATION_NOT_SUPPORTED.equals(sqlException.getMessage());
        }
        return false;
    }
}
