/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.transaction.jdbc;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.jdbc.exceptions.CannotGetJdbcConnectionException;
import io.micronaut.transaction.support.TransactionSynchronizationAdapter;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Helper class that provides static methods for obtaining JDBC Connections from
 * a {@link javax.sql.DataSource}. Includes special support for Spring-managed
 * transactional Connections, e.g. managed by {@link DataSourceTransactionManager}
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author graemerocher
 * @see #getConnection
 * @see #releaseConnection
 * @see DataSourceTransactionManager
 */
public abstract class DataSourceUtils {

    /**
     * Order value for TransactionSynchronization objects that clean up JDBC Connections.
     */
    public static final int CONNECTION_SYNCHRONIZATION_ORDER = 1000;

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceUtils.class);


    /**
     * Obtain a Connection from the given DataSource. Translates SQLExceptions into
     * the Spring hierarchy of unchecked generic data access exceptions, simplifying
     * calling code and making any exception that is thrown more meaningful.
     * <p>Is aware of a corresponding Connection bound to the current thread, for example
     * when using {@link DataSourceTransactionManager}. Will bind a Connection to the
     * thread if transaction synchronization is active.
     * @param dataSource the DataSource to obtain Connections from
     * @return a JDBC Connection from the given DataSource
     * @throws CannotGetJdbcConnectionException
     * if the attempt to get a Connection failed
     * @see #releaseConnection
     */
    public static Connection getConnection(DataSource dataSource) throws CannotGetJdbcConnectionException {
        try {
            return doGetConnection(dataSource, true);
        } catch (SQLException ex) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", ex);
        } catch (IllegalStateException ex) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection: " + ex.getMessage());
        }
    }

    /**
     * Obtain a Connection from the given DataSource. Translates SQLExceptions into
     * the Spring hierarchy of unchecked generic data access exceptions, simplifying
     * calling code and making any exception that is thrown more meaningful.
     * <p>Is aware of a corresponding Connection bound to the current thread, for example
     * when using {@link DataSourceTransactionManager}. Will bind a Connection to the
     * thread if transaction synchronization is active.
     * @param dataSource the DataSource to obtain Connections from
     * @param allowCreate If true allow the creation of a new connection if non is bound
     * @return a JDBC Connection from the given DataSource
     * @throws CannotGetJdbcConnectionException
     * if the attempt to get a Connection failed
     * @see #releaseConnection
     */
    public static Connection getConnection(DataSource dataSource, boolean allowCreate) throws CannotGetJdbcConnectionException {
        try {
            return doGetConnection(dataSource, allowCreate);
        } catch (SQLException ex) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", ex);
        } catch (IllegalStateException ex) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection: " + ex.getMessage());
        }
    }

    /**
     * Actually obtain a JDBC Connection from the given DataSource.
     * Same as {@link #getConnection}, but throwing the original SQLException.
     * <p>Is aware of a corresponding Connection bound to the current thread, for example
     * when using {@link DataSourceTransactionManager}. Will bind a Connection to the thread
     * if transaction synchronization is active (e.g. if in a JTA transaction).
     * @param dataSource the DataSource to obtain Connections from
     * @param allowCreate If true allow the creation of a new connection if non is bound
     * @return a JDBC Connection from the given DataSource
     * @throws SQLException if thrown by JDBC methods
     * @see #doReleaseConnection
     */
    private static Connection doGetConnection(DataSource dataSource, boolean allowCreate) throws SQLException {
        Objects.requireNonNull(dataSource, "No DataSource specified");

        ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        if (conHolder != null && (conHolder.hasConnection() || conHolder.isSynchronizedWithTransaction())) {
            conHolder.requested();
            if (!conHolder.hasConnection()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Fetching resumed JDBC Connection from DataSource");
                }
                if (!allowCreate) {
                    throw new CannotGetJdbcConnectionException("No current JDBC Connection found. Consider wrapping this call in transactional boundaries.");
                }
                conHolder.setConnection(fetchConnection(dataSource));
            }
            return conHolder.getConnection();
        }
        // Else we either got no holder or an empty thread-bound holder here.
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Fetching JDBC Connection from DataSource");
        }
        if (!allowCreate) {
            throw new CannotGetJdbcConnectionException("No current JDBC Connection found. Consider wrapping this call in transactional boundaries.");
        }
        Connection con = fetchConnection(dataSource);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                // Use same Connection for further JDBC actions within the transaction.
                // Thread-bound object will get removed by synchronization at transaction completion.
                ConnectionHolder holderToUse = conHolder;
                if (holderToUse == null) {
                    holderToUse = new ConnectionHolder(con);
                } else {
                    holderToUse.setConnection(con);
                }
                holderToUse.requested();
                TransactionSynchronizationManager.registerSynchronization(new ConnectionSynchronization(
                        holderToUse,
                        dataSource)
                );
                holderToUse.setSynchronizedWithTransaction(true);
                if (holderToUse != conHolder) {
                    TransactionSynchronizationManager.bindResource(dataSource, holderToUse);
                }
            } catch (RuntimeException ex) {
                // Unexpected exception from external delegation call -> close Connection and rethrow.
                releaseConnection(con, dataSource);
                throw ex;
            }
        }

        return con;
    }

    /**
     * Actually fetch a {@link Connection} from the given {@link DataSource},
     * defensively turning an unexpected {@code null} return value from
     * {@link DataSource#getConnection()} into an {@link IllegalStateException}.
     * @param dataSource the DataSource to obtain Connections from
     * @return a JDBC Connection from the given DataSource (never {@code null})
     * @throws SQLException if thrown by JDBC methods
     * @throws IllegalStateException if the DataSource returned a null value
     * @see DataSource#getConnection()
     */
    private static Connection fetchConnection(DataSource dataSource) throws SQLException {
        Connection con = dataSource.getConnection();
        if (con == null) {
            throw new IllegalStateException("DataSource returned null from getConnection(): " + dataSource);
        }
        return con;
    }

    /**
     * Prepare the given Connection with the given transaction semantics.
     * @param con the Connection to prepare
     * @param definition the transaction definition to apply
     * @return the previous isolation level, if any
     * @throws SQLException if thrown by JDBC methods
     * @see #resetConnectionAfterTransaction
     */
    @Nullable
    public static TransactionDefinition.Isolation prepareConnectionForTransaction(Connection con, @Nullable TransactionDefinition definition)
            throws SQLException {

        Objects.requireNonNull(con, "No Connection specified");

        // Set read-only flag.
        if (definition != null && definition.isReadOnly()) {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Setting JDBC Connection [" + con + "] read-only");
                }
                con.setReadOnly(true);
            } catch (SQLException | RuntimeException ex) {
                Throwable exToCheck = ex;
                while (exToCheck != null) {
                    if (exToCheck.getClass().getSimpleName().contains("Timeout")) {
                        // Assume it's a connection timeout that would otherwise get lost: e.g. from JDBC 4.0
                        throw ex;
                    }
                    exToCheck = exToCheck.getCause();
                }
                // "read-only not supported" SQLException -> ignore, it's just a hint anyway
                LOGGER.debug("Could not set JDBC Connection read-only", ex);
            }
        }

        // Apply specific isolation level, if any.
        TransactionDefinition.Isolation previousIsolationLevel = null;
        if (definition != null) {
            TransactionDefinition.Isolation isolationLevel = definition.getIsolationLevel();
            if (isolationLevel != TransactionDefinition.Isolation.DEFAULT) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Changing isolation level of JDBC Connection [" + con + "] to " +
                            isolationLevel);
                }
                int currentIsolation = con.getTransactionIsolation();
                if (currentIsolation != isolationLevel.getCode()) {
                    previousIsolationLevel = TransactionDefinition.Isolation.valueOf(currentIsolation);
                    con.setTransactionIsolation(isolationLevel.getCode());
                }
            }
        }

        return previousIsolationLevel;
    }

    /**
     * Reset the given Connection after a transaction,
     * regarding read-only flag and isolation level.
     * @param con the Connection to reset
     * @param previousIsolationLevel the isolation level to restore, if any
     * @see #prepareConnectionForTransaction
     */
    public static void resetConnectionAfterTransaction(Connection con, @Nullable TransactionDefinition.Isolation previousIsolationLevel) {
        Objects.requireNonNull(con, "No Connection specified");
        try {
            // Reset transaction isolation to previous value, if changed for the transaction.
            if (previousIsolationLevel != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Resetting isolation level of JDBC Connection [" +
                            con + "] to " + previousIsolationLevel);
                }
                con.setTransactionIsolation(previousIsolationLevel.getCode());
            }

            // Reset read-only flag.
            if (con.isReadOnly()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Resetting read-only flag of JDBC Connection [" + con + "]");
                }
                con.setReadOnly(false);
            }
        } catch (Throwable ex) {
            LOGGER.debug("Could not reset JDBC Connection after transaction", ex);
        }
    }

    /**
     * Determine whether the given JDBC Connection is transactional, that is,
     * bound to the current thread by Spring's transaction facilities.
     * @param con the Connection to check
     * @param dataSource the DataSource that the Connection was obtained from
     * (may be {@code null})
     * @return whether the Connection is transactional
     */
    public static boolean isConnectionTransactional(Connection con, @Nullable DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        return (conHolder != null && connectionEquals(conHolder, con));
    }

    /**
     * Apply the current transaction timeout, if any,
     * to the given JDBC Statement object.
     * @param stmt the JDBC Statement object
     * @param dataSource the DataSource that the Connection was obtained from
     * @throws SQLException if thrown by JDBC methods
     * @see java.sql.Statement#setQueryTimeout
     */
    public static void applyTransactionTimeout(Statement stmt, @Nullable DataSource dataSource) throws SQLException {
        applyTimeout(stmt, dataSource, -1);
    }

    /**
     * Apply the specified timeout - overridden by the current transaction timeout,
     * if any - to the given JDBC Statement object.
     * @param stmt the JDBC Statement object
     * @param dataSource the DataSource that the Connection was obtained from
     * @param timeout the timeout to apply (or 0 for no timeout outside of a transaction)
     * @throws SQLException if thrown by JDBC methods
     * @see java.sql.Statement#setQueryTimeout
     */
    public static void applyTimeout(Statement stmt, @Nullable DataSource dataSource, int timeout) throws SQLException {
        Objects.requireNonNull(stmt, "No Statement specified");
        ConnectionHolder holder = null;
        if (dataSource != null) {
            holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        }
        if (holder != null && holder.hasTimeout()) {
            // Remaining transaction timeout overrides specified value.
            stmt.setQueryTimeout(holder.getTimeToLiveInSeconds());
        } else if (timeout >= 0) {
            // No current transaction timeout -> apply specified value.
            stmt.setQueryTimeout(timeout);
        }
    }

    /**
     * Close the given Connection, obtained from the given DataSource,
     * if it is not managed externally (that is, not bound to the thread).
     * @param con the Connection to close if necessary
     * (if this is {@code null}, the call will be ignored)
     * @param dataSource the DataSource that the Connection was obtained from
     * (may be {@code null})
     * @see #getConnection
     */
    public static void releaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) {
        try {
            doReleaseConnection(con, dataSource);
        } catch (SQLException ex) {
            LOGGER.debug("Could not close JDBC Connection", ex);
        } catch (Throwable ex) {
            LOGGER.debug("Unexpected exception on closing JDBC Connection", ex);
        }
    }

    /**
     * Actually close the given Connection, obtained from the given DataSource.
     * Same as {@link #releaseConnection}, but throwing the original SQLException.
     * @param con the Connection to close if necessary
     * (if this is {@code null}, the call will be ignored)
     * @param dataSource the DataSource that the Connection was obtained from
     * (may be {@code null})
     * @throws SQLException if thrown by JDBC methods
     * @see #doGetConnection
     */
    public static void doReleaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) throws SQLException {
        if (con == null) {
            return;
        }
        if (dataSource != null) {
            ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
            if (conHolder != null && connectionEquals(conHolder, con)) {
                // It's the transactional Connection: Don't close it.
                conHolder.released();
                return;
            }
        }
        doCloseConnection(con, dataSource);
    }

    /**
     * Close the Connection.
     * @param con the Connection to close if necessary
     * @param dataSource the DataSource that the Connection was obtained from
     * @throws SQLException if thrown by JDBC methods
     * @see Connection#close()
     */
    public static void doCloseConnection(Connection con, @Nullable DataSource dataSource) throws SQLException {
        con.close();
    }

    /**
     * Determine whether the given two Connections are equal, asking the target
     * Connection in case of a proxy. Used to detect equality even if the
     * user passed in a raw target Connection while the held one is a proxy.
     * @param conHolder the ConnectionHolder for the held Connection (potentially a proxy)
     * @param passedInCon the Connection passed-in by the user
     * (potentially a target Connection without proxy)
     * @return whether the given Connections are equal
     * @see #getTargetConnection
     */
    private static boolean connectionEquals(ConnectionHolder conHolder, Connection passedInCon) {
        if (!conHolder.hasConnection()) {
            return false;
        }
        Connection heldCon = conHolder.getConnection();
        // Explicitly check for identity too: for Connection handles that do not implement
        // "equals" properly, such as the ones Commons DBCP exposes).
        return (heldCon == passedInCon || heldCon.equals(passedInCon) ||
                getTargetConnection(heldCon).equals(passedInCon));
    }

    /**
     * Return the innermost target Connection of the given Connection. If the given
     * Connection is a proxy, it will be unwrapped until a non-proxy Connection is
     * found. Otherwise, the passed-in Connection will be returned as-is.
     * @param con the Connection proxy to unwrap
     * @return the innermost target Connection, or the passed-in one if no proxy
     */
    public static Connection getTargetConnection(Connection con) {
        return con;
    }

    /**
     * Determine the connection synchronization order to use for the given
     * DataSource. Decreased for every level of nesting that a DataSource
     * has, checked through the level of DelegatingDataSource nesting.
     * @param dataSource the DataSource to check
     * @return the connection synchronization order to use
     * @see #CONNECTION_SYNCHRONIZATION_ORDER
     */
    private static int getConnectionSynchronizationOrder(DataSource dataSource) {
        int order = CONNECTION_SYNCHRONIZATION_ORDER;
        DataSource currDs = dataSource;
        while (currDs instanceof DelegatingDataSource) {
            order--;
            currDs = ((DelegatingDataSource) currDs).getTargetDataSource();
        }
        return order;
    }


    /**
     * Callback for resource cleanup at the end of a non-native JDBC transaction
     * (e.g. when participating in a JtaTransactionManager transaction).
     */
    private static class ConnectionSynchronization extends TransactionSynchronizationAdapter {

        private final ConnectionHolder connectionHolder;

        private final DataSource dataSource;

        private int order;

        private boolean holderActive = true;

        public ConnectionSynchronization(ConnectionHolder connectionHolder, DataSource dataSource) {
            this.connectionHolder = connectionHolder;
            this.dataSource = dataSource;
            this.order = getConnectionSynchronizationOrder(dataSource);
        }

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public void suspend() {
            if (this.holderActive) {
                TransactionSynchronizationManager.unbindResource(this.dataSource);
                if (this.connectionHolder.hasConnection() && !this.connectionHolder.isOpen()) {
                    // Release Connection on suspend if the application doesn't keep
                    // a handle to it anymore. We will fetch a fresh Connection if the
                    // application accesses the ConnectionHolder again after resume,
                    // assuming that it will participate in the same transaction.
                    releaseConnection(this.connectionHolder.getConnection(), this.dataSource);
                    this.connectionHolder.setConnection(null);
                }
            }
        }

        @Override
        public void resume() {
            if (this.holderActive) {
                TransactionSynchronizationManager.bindResource(this.dataSource, this.connectionHolder);
            }
        }

        @Override
        public void beforeCompletion() {
            // Release Connection early if the holder is not open anymore
            // (that is, not used by another resource like a Hibernate Session
            // that has its own cleanup via transaction synchronization),
            // to avoid issues with strict JTA implementations that expect
            // the close call before transaction completion.
            if (!this.connectionHolder.isOpen()) {
                TransactionSynchronizationManager.unbindResource(this.dataSource);
                this.holderActive = false;
                if (this.connectionHolder.hasConnection()) {
                    releaseConnection(this.connectionHolder.getConnection(), this.dataSource);
                }
            }
        }

        @Override
        public void afterCompletion(int status) {
            // If we haven't closed the Connection in beforeCompletion,
            // close it now. The holder might have been used for other
            // cleanup in the meantime, for example by a Hibernate Session.
            if (this.holderActive) {
                // The thread-bound ConnectionHolder might not be available anymore,
                // since afterCompletion might get called from a different thread.
                TransactionSynchronizationManager.unbindResourceIfPossible(this.dataSource);
                this.holderActive = false;
                if (this.connectionHolder.hasConnection()) {
                    releaseConnection(this.connectionHolder.getConnection(), this.dataSource);
                    // Reset the ConnectionHolder: It might remain bound to the thread.
                    this.connectionHolder.setConnection(null);
                }
            }
            this.connectionHolder.reset();
        }
    }

}
