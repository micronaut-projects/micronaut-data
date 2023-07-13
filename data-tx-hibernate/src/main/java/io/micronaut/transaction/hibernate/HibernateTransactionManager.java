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
package io.micronaut.transaction.hibernate;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.SynchronousConnectionManager;
import io.micronaut.data.connection.support.JdbcConnectionUtils;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.InvalidIsolationLevelException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.impl.DefaultTransactionStatus;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;
import io.micronaut.transaction.support.AbstractDefaultTransactionOperations;
import io.micronaut.transaction.support.TransactionSynchronization;
import jakarta.persistence.PersistenceException;
import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The Hibernate transaction manager.
 * Partially based on https://github.com/spring-projects/spring-framework/blob/main/spring-orm/src/main/java/org/springframework/orm/hibernate5/HibernateTransactionManager.java
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@EachBean(DataSource.class)
@Replaces(DataSourceTransactionManager.class)
@Requires(condition = HibernateTransactionManagerCondition.class)
@TypeHint(HibernateTransactionManager.class)
public final class HibernateTransactionManager extends AbstractDefaultTransactionOperations<Session> {

    private boolean prepareConnection = true;

    private boolean allowResultAccessAfterCompletion = false;

    HibernateTransactionManager(@Parameter ConnectionOperations<Session> connectionOperations,
                                @Parameter SynchronousConnectionManager<Session> synchronousConnectionManager) {
        super(connectionOperations, synchronousConnectionManager);
    }

    @Override
    protected void doBegin(DefaultTransactionStatus<Session> txStatus) {
        Session session = txStatus.getConnection();
        TransactionDefinition definition = txStatus.getTransactionDefinition();
        boolean isNewSession = txStatus.getConnectionStatus().isNew();

        boolean isReadOnly = definition.isReadOnly().orElse(false);
        if (isReadOnly && isNewSession) {
            // Just set to MANUAL in case of a new Session for this transaction.
            session.setFlushMode(FlushMode.MANUAL.toJpaFlushMode());
            // As of 5.1, we're also setting Hibernate's read-only entity mode by default.
            session.setDefaultReadOnly(true);
        }
        List<Runnable> onComplete = new ArrayList<>(5);

        boolean holdabilityNeeded = allowResultAccessAfterCompletion && !isNewSession;
        boolean isolationLevelNeeded = definition.getIsolationLevel().isPresent();
        if (holdabilityNeeded || isolationLevelNeeded || definition.isReadOnly().isPresent()) {
            if (prepareConnection && isSameConnectionForEntireSession(session)) {
                // We're allowed to change the transaction settings of the JDBC Connection.
                if (logger.isDebugEnabled()) {
                    logger.debug("Preparing JDBC Connection of Hibernate Session [{}]", session);
                }
                Connection connection = getConnection(session);

                definition.isReadOnly().ifPresent(readOnly -> JdbcConnectionUtils.applyReadOnly(logger, connection, readOnly, onComplete));
                definition.getIsolationLevel().ifPresent(isolation ->
                    JdbcConnectionUtils.applyTransactionIsolation(logger, connection, isolation.getCode(), onComplete));

                if (allowResultAccessAfterCompletion && !isNewSession) {
                    JdbcConnectionUtils.applyHoldability(logger, connection, ResultSet.HOLD_CURSORS_OVER_COMMIT, onComplete);
                }

            } else {
                // Not allowed to change the transaction settings of the JDBC Connection.
                if (isolationLevelNeeded) {
                    // We should set a specific isolation level but are not allowed to...
                    throw new InvalidIsolationLevelException(
                        "HibernateTransactionManager is not allowed to support custom isolation levels: " +
                            "make sure that its 'prepareConnection' flag is on (the default) and that the " +
                            "Hibernate connection release mode is set to 'on_close' (the default for JDBC).");
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Not preparing JDBC Connection of Hibernate Session [{}]", session);
                }
            }
        }

        if (!isReadOnly && !isNewSession) {
            // We need AUTO or COMMIT for a non-read-only transaction.
            FlushMode flushMode = session.getHibernateFlushMode();
            if (FlushMode.MANUAL.equals(flushMode)) {
                session.setFlushMode(FlushMode.AUTO.toJpaFlushMode());
                txStatus.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCompletion() {
                        session.setFlushMode(flushMode.toJpaFlushMode());
                    }
                });
            }
        }

        determineTimeout(definition).ifPresent(timeout -> {
            // Register transaction timeout.
            // Use Hibernate's own transaction timeout mechanism on Hibernate 3.1+
            // Applies to all statements, also to inserts, updates and deletes!
            Transaction hibTx = session.getTransaction();
            hibTx.setTimeout(((int) timeout.toMillis() / 1000));
        });

        if (!onComplete.isEmpty()) {
            Collections.reverse(onComplete);
            txStatus.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(Status status) {
                    if (isPhysicallyConnected(session)) {
                        // We're running with connection release mode "on_close": We're able to reset
                        // the isolation level and/or read-only flag of the JDBC Connection here.
                        // Else, we need to rely on the connection pool to perform proper cleanup.
                        for (Runnable runnable : onComplete) {
                            runnable.run();
                        }
                    }
                }
            });
        }

        Transaction transaction = session.beginTransaction();
        txStatus.setTransaction(transaction);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus<Session> tx) {
        Transaction transaction = (Transaction) tx.getTransaction();
        Objects.requireNonNull(transaction, "No Hibernate transaction");

        if (logger.isDebugEnabled()) {
            logger.debug("Committing Hibernate transaction on Session [{}]", tx.getConnection());
        }

        try {
            transaction.commit();
        } catch (org.hibernate.TransactionException ex) {
            // assumable from commit call to the underlying JDBC connection
            throw new TransactionSystemException("Could not commit Hibernate transaction", ex);
        } catch (PersistenceException ex) {
            if (ex.getCause() instanceof HibernateException) {
                throw ex;
            }
            throw ex;
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus<Session> tx) {
        Transaction transaction = (Transaction) tx.getTransaction();
        Objects.requireNonNull(transaction, "No Hibernate transaction");
        if (logger.isDebugEnabled()) {
            logger.debug("Rolling back Hibernate transaction on Session [{}]", tx.getConnection());
        }

        try {
            transaction.rollback();
        } catch (org.hibernate.TransactionException ex) {
            throw new TransactionSystemException("Could not roll back Hibernate transaction", ex);
        } finally {
            if (!tx.getConnectionStatus().isNew()) {
                // Clear all pending inserts/updates/deletes in the Session.
                // Necessary for pre-bound Sessions, to avoid inconsistent state.
                tx.getConnection().clear();
            }
        }
    }

    @Override
    @NonNull
    public Session getConnection() {
        return connectionOperations.getConnectionStatus().getConnection();
    }

    @Override
    @NonNull
    public boolean hasConnection() {
        return connectionOperations.findConnectionStatus().isPresent();
    }

    /**
     * Return whether the given Hibernate Session will always hold the same
     * JDBC Connection. This is used to check whether the transaction manager
     * can safely prepare and clean up the JDBC Connection used for a transaction.
     * <p>The default implementation checks the Session's connection release mode
     * to be "on_close".
     *
     * @param session the Hibernate Session to check
     * @return Whether the same connection is needed for the whole session
     * @see org.hibernate.ConnectionReleaseMode#ON_CLOSE
     */
    private boolean isSameConnectionForEntireSession(Session session) {
        if (!(session instanceof SessionImplementor)) {
            // The best we can do is to assume we're safe.
            return true;
        }
        PhysicalConnectionHandlingMode releaseMode =
            ((SessionImplementor) session).getJdbcCoordinator()
                .getLogicalConnection()
                .getConnectionHandlingMode();
        return PhysicalConnectionHandlingMode.DELAYED_ACQUISITION_AND_HOLD.equals(releaseMode);
    }

    /**
     * Determine whether the given Session is (still) physically connected
     * to the database, that is, holds an active JDBC Connection internally.
     *
     * @param session the Hibernate Session to check
     * @return Is the session physically connected
     * @see #isSameConnectionForEntireSession(Session)
     */
    private boolean isPhysicallyConnected(Session session) {
        if (!(session instanceof SessionImplementor)) {
            // The best we can do is to check whether we're logically connected.
            return session.isConnected();
        }
        return ((SessionImplementor) session).getJdbcCoordinator().getLogicalConnection().isPhysicallyConnected();
    }

    private Connection getConnection(Session session) {
        return ((SessionImplementor) session).getJdbcCoordinator().getLogicalConnection().getPhysicalConnection();
    }

}
