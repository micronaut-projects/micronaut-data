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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.transaction.SavepointManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.*;
import io.micronaut.transaction.support.SmartTransactionObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Objects;

/**
 * Convenient base class for JDBC-aware transaction objects. Can contain a
 * {@link ConnectionHolder} with a JDBC {@code Connection}, and implements the
 * {@link SavepointManager} interface based on that {@code ConnectionHolder}.
 *
 * <p>Allows for programmatic management of JDBC {@link java.sql.Savepoint Savepoints}.
 * {@link io.micronaut.transaction.support.DefaultTransactionStatus}
 * automatically delegates to this, as it autodetects transaction objects which
 * implement the {@link SavepointManager} interface.
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 1.1
 * @see DataSourceTransactionManager
 */
@Internal
public abstract class JdbcTransactionObjectSupport implements SavepointManager, SmartTransactionObject {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcTransactionObjectSupport.class);

    @Nullable
    private ConnectionHolder connectionHolder;

    @Nullable
    private TransactionDefinition.Isolation previousIsolationLevel;

    private boolean savepointAllowed = false;

    /**
     * Sets the connection holder.
     * @param connectionHolder The connection holder
     */
    public void setConnectionHolder(@Nullable ConnectionHolder connectionHolder) {
        this.connectionHolder = connectionHolder;
    }

    /**
     * Retrieves the connection holder.
     * @return The connection holder
     */
    public @NonNull ConnectionHolder getConnectionHolder() {
        Objects.requireNonNull(this.connectionHolder , "No ConnectionHolder available");
        return this.connectionHolder;
    }

    /**
     * @return Whether a connection holder is present
     */
    public boolean hasConnectionHolder() {
        return (this.connectionHolder != null);
    }

    /**
     * Sets the previous isolation level.
     * @param previousIsolationLevel The isolation level
     */
    public void setPreviousIsolationLevel(@Nullable TransactionDefinition.Isolation previousIsolationLevel) {
        this.previousIsolationLevel = previousIsolationLevel;
    }

    /**
     * @return The previous isolation level
     */
    @Nullable
    public TransactionDefinition.Isolation getPreviousIsolationLevel() {
        return this.previousIsolationLevel;
    }

    /**
     * Sets whether save points are allowed.
     * @param savepointAllowed True if they are allowed
     */
    public void setSavepointAllowed(boolean savepointAllowed) {
        this.savepointAllowed = savepointAllowed;
    }

    /**
     * @return Whether the save point is allowed
     */
    public boolean isSavepointAllowed() {
        return this.savepointAllowed;
    }

    @Override
    public void flush() {
        // no-op
    }


    //---------------------------------------------------------------------
    // Implementation of SavepointManager
    //---------------------------------------------------------------------

    /**
     * This implementation creates a JDBC 3.0 Savepoint and returns it.
     * @see java.sql.Connection#setSavepoint
     */
    @Override
    public Object createSavepoint() throws TransactionException {
        ConnectionHolder conHolder = getConnectionHolderForSavepoint();
        try {
            if (!conHolder.supportsSavepoints()) {
                throw new NestedTransactionNotSupportedException(
                        "Cannot create a nested transaction because savepoints are not supported by your JDBC driver");
            }
            if (conHolder.isRollbackOnly()) {
                throw new CannotCreateTransactionException(
                        "Cannot create savepoint for transaction which is already marked as rollback-only");
            }
            return conHolder.createSavepoint();
        } catch (SQLException ex) {
            throw new CannotCreateTransactionException("Could not create JDBC savepoint", ex);
        }
    }

    /**
     * This implementation rolls back to the given JDBC 3.0 Savepoint.
     * @see java.sql.Connection#rollback(java.sql.Savepoint)
     */
    @Override
    public void rollbackToSavepoint(Object savepoint) throws TransactionException {
        ConnectionHolder conHolder = getConnectionHolderForSavepoint();
        try {
            conHolder.getConnection().rollback((Savepoint) savepoint);
            conHolder.resetRollbackOnly();
        } catch (Throwable ex) {
            throw new TransactionSystemException("Could not roll back to JDBC savepoint", ex);
        }
    }

    /**
     * This implementation releases the given JDBC 3.0 Savepoint.
     * @see java.sql.Connection#releaseSavepoint
     */
    @Override
    public void releaseSavepoint(Object savepoint) throws TransactionException {
        ConnectionHolder conHolder = getConnectionHolderForSavepoint();
        try {
            conHolder.getConnection().releaseSavepoint((Savepoint) savepoint);
        } catch (Throwable ex) {
            LOG.debug("Could not explicitly release JDBC savepoint", ex);
        }
    }

    /**
     * @return The connection holder for the save point
     * @throws TransactionException If an error occurs retrieving the connection holder
     */
    protected ConnectionHolder getConnectionHolderForSavepoint() throws TransactionException {
        if (!isSavepointAllowed()) {
            throw new NestedTransactionNotSupportedException(
                    "Transaction manager does not allow nested transactions");
        }
        if (!hasConnectionHolder()) {
            throw new TransactionUsageException(
                    "Cannot create nested transaction when not exposing a JDBC transaction");
        }
        return getConnectionHolder();
    }

}
