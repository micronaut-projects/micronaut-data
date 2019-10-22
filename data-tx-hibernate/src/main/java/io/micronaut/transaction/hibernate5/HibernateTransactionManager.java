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
package io.micronaut.transaction.hibernate5;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
import io.micronaut.transaction.exceptions.IllegalTransactionStateException;
import io.micronaut.transaction.exceptions.InvalidIsolationLevelException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.jdbc.ConnectionHolder;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;
import io.micronaut.transaction.jdbc.DataSourceUtils;
import io.micronaut.transaction.jdbc.JdbcTransactionObjectSupport;
import io.micronaut.transaction.support.AbstractSynchronousTransactionManager;
import io.micronaut.transaction.support.DefaultTransactionStatus;
import io.micronaut.transaction.support.ResourceTransactionManager;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import org.hibernate.*;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode;
import org.hibernate.resource.transaction.spi.TransactionStatus;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Objects;


/**
 * {@link io.micronaut.transaction.SynchronousTransactionManager}
 * implementation for a single Hibernate {@link SessionFactory}.
 * Binds a Hibernate Session from the specified factory to the thread,
 * potentially allowing for one thread-bound Session per factory.
 * {@code SessionFactory.getCurrentSession()} is required for Hibernate
 * access code that needs to support this transaction handling mechanism,
 * with the SessionFactory being configured with {@link MicronautSessionContext}.
 *
 * <p>Supports custom isolation levels, and timeouts that get applied as
 * Hibernate transaction timeouts.
 *
 * <p>This transaction manager is appropriate for applications that use a single
 * Hibernate SessionFactory for transactional data access, but it also supports
 * direct DataSource access within a transaction (i.e. plain JDBC code working
 * with the same DataSource). This allows for mixing services which access Hibernate
 * and services which use plain JDBC (without being aware of Hibernate)!
 * Application code needs to stick to the same simple Connection lookup pattern as
 * with {@link io.micronaut.transaction.jdbc.DataSourceTransactionManager}
 * (i.e. {@link DataSourceUtils#getConnection}.
 *
 * <p>This transaction manager supports nested transactions via JDBC 3.0 Savepoints.
 * The {@link #setNestedTransactionAllowed} "nestedTransactionAllowed"} flag defaults
 * to "false", though, as nested transactions will just apply to the JDBC Connection,
 * not to the Hibernate Session and its cached entity objects and related context.
 * You can manually set the flag to "true" if you want to use nested transactions
 * for JDBC access code which participates in Hibernate transactions (provided that
 * your JDBC driver supports Savepoints). <i>Note that Hibernate itself does not
 * support nested transactions! Hence, do not expect Hibernate access code to
 * semantically participate in a nested transaction.</i>
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 4.2
 * @see SessionFactory#getCurrentSession()
 * @see DataSourceUtils#getConnection
 * @see DataSourceUtils#releaseConnection
 * @see io.micronaut.transaction.jdbc.DataSourceTransactionManager
 */
@SuppressWarnings("serial")
@EachBean(SessionFactory.class)
@Requires(missingClasses = "org.springframework.orm.hibernate5.HibernateTransactionManager")
@Replaces(DataSourceTransactionManager.class)
public class HibernateTransactionManager extends AbstractSynchronousTransactionManager<Connection>
        implements ResourceTransactionManager<EntityManagerFactory, Connection> {

    @NonNull
    private final SessionFactory sessionFactory;

    @NonNull
    private final DataSource dataSource;

    private boolean prepareConnection = true;

    private boolean allowResultAccessAfterCompletion = false;

    private boolean hibernateManagedSession = false;

    @Nullable
    private final Interceptor entityInterceptor;


    /**
     * Create a new HibernateTransactionManager instance.
     * @param sessionFactory the SessionFactory to manage transactions for
     * @param dataSource The data source associated with the session factory
     * @param entityInterceptor The configured entity interceptor
     */
    public HibernateTransactionManager(
            SessionFactory sessionFactory,
            @Parameter DataSource dataSource,
            @Nullable Interceptor entityInterceptor) {
        this.sessionFactory = sessionFactory;
        this.dataSource = dataSource;
        this.entityInterceptor = entityInterceptor;
    }

    /**
     * @return Return the SessionFactory that this instance should manage transactions for.
     */
    @NonNull
    public SessionFactory getSessionFactory() {
        return this.sessionFactory;
    }

    /**
     * @return Return the JDBC DataSource that this instance manages transactions for.
     */
    @Nullable
    public DataSource getDataSource() {
        return this.dataSource;
    }

    /**
     * Set whether to prepare the underlying JDBC Connection of a transactional
     * Hibernate Session, that is, whether to apply a transaction-specific
     * isolation level and/or the transaction's read-only flag to the underlying
     * JDBC Connection.
     * <p>Default is "true". If you turn this flag off, the transaction manager
     * will not support per-transaction isolation levels anymore. It will not
     * call {@code Connection.setReadOnly(true)} for read-only transactions
     * anymore either. If this flag is turned off, no cleanup of a JDBC Connection
     * is required after a transaction, since no Connection settings will get modified.
     * @param prepareConnection  Whether to prepare the connection
     * @see Connection#setTransactionIsolation
     * @see Connection#setReadOnly
     */
    public void setPrepareConnection(boolean prepareConnection) {
        this.prepareConnection = prepareConnection;
    }

    /**
     * Set whether to allow result access after completion, typically via Hibernate's
     * ScrollableResults mechanism.
     * <p>Default is "false". Turning this flag on enforces over-commit holdability on the
     * underlying JDBC Connection (if {@link #prepareConnection "prepareConnection"} is on)
     * and skips the disconnect-on-completion step.
     * @param allowResultAccessAfterCompletion Whether to allow result access after completion
     * @see Connection#setHoldability
     * @see ResultSet#HOLD_CURSORS_OVER_COMMIT
     * @see #disconnectOnCompletion(Session)
     */
    public void setAllowResultAccessAfterCompletion(boolean allowResultAccessAfterCompletion) {
        this.allowResultAccessAfterCompletion = allowResultAccessAfterCompletion;
    }

    /**
     * Set whether to operate on a Hibernate-managed Session, that is, whether to obtain the Session through
     * Hibernate's {@link SessionFactory#getCurrentSession()}
     * instead of {@link SessionFactory#openSession()} (with a
     * {@link TransactionSynchronizationManager}
     * check preceding it).
     * <p>Default is "false", i.e. using a Spring-managed Session: taking the current
     * thread-bound Session if available (e.g. in an Open-Session-in-View scenario),
     * creating a new Session for the current transaction otherwise.
     * <p>Switch this flag to "true" in order to enforce use of a Hibernate-managed Session.
     * Note that this requires {@link SessionFactory#getCurrentSession()}
     * to always return a proper Session when called for a Spring-managed transaction;
     * transaction begin will fail if the {@code getCurrentSession()} call fails.
     * <p>This mode will typically be used in combination with a custom Hibernate
     * {@link org.hibernate.context.spi.CurrentSessionContext} implementation that stores
     * Sessions in a place other than Spring's TransactionSynchronizationManager.
     * It may also be used in combination with Spring's Open-Session-in-View support
     * (using Spring's default {@link MicronautSessionContext}), in which case it subtly
     * differs from the Spring-managed Session mode: The pre-bound Session will <i>not</i>
     * receive a {@code clear()} call (on rollback) or a {@code disconnect()}
     * call (on transaction completion) in such a scenario; this is rather left up
     * to a custom CurrentSessionContext implementation (if desired).
     *
     * @param hibernateManagedSession  True if hibernate managed sessions should be used
     */
    public void setHibernateManagedSession(boolean hibernateManagedSession) {
        this.hibernateManagedSession = hibernateManagedSession;
    }

    /**
     * @return Return the current Hibernate entity interceptor, or {@code null} if none.
     * Resolves an entity interceptor bean name via the bean factory,
     * if necessary.
     */
    @Nullable
    public Interceptor getEntityInterceptor()  {
        return this.entityInterceptor;
    }

    @Override
    public EntityManagerFactory getResourceFactory() {
        return getSessionFactory();
    }

    @Override
    protected Connection getConnection(Object transaction) {
        final Session session = ((HibernateTransactionObject) transaction).getSessionHolder().getSession();
        return ((SessionImplementor) session).connection();
    }

    @Override
    protected Object doGetTransaction() {
        HibernateTransactionObject txObject = new HibernateTransactionObject();
        txObject.setSavepointAllowed(isNestedTransactionAllowed());

        SessionFactory sessionFactory = getSessionFactory();
        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.getResource(sessionFactory);
        if (sessionHolder != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Found thread-bound Session [" + sessionHolder.getSession() + "] for Hibernate transaction");
            }
            txObject.setSessionHolder(sessionHolder);
        } else if (this.hibernateManagedSession) {
            Session session = sessionFactory.getCurrentSession();
            if (logger.isDebugEnabled()) {
                logger.debug("Found Hibernate-managed Session [" + session + "] for Spring-managed transaction");
            }
            txObject.setExistingSession(session);
        }

        ConnectionHolder conHolder = (ConnectionHolder)
                TransactionSynchronizationManager.getResource(getDataSource());
        txObject.setConnectionHolder(conHolder);

        return txObject;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        HibernateTransactionObject txObject = (HibernateTransactionObject) transaction;
        return (txObject.hasSpringManagedTransaction() ||
                (this.hibernateManagedSession && txObject.hasHibernateManagedTransaction()));
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        HibernateTransactionObject txObject = (HibernateTransactionManager.HibernateTransactionObject) transaction;

        if (txObject.hasConnectionHolder() && !txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
            throw new IllegalTransactionStateException(
                    "Pre-bound JDBC Connection found! HibernateTransactionManager does not support " +
                            "running within DataSourceTransactionManager if told to manage the DataSource itself. " +
                            "It is recommended to use a single HibernateTransactionManager for all transactions " +
                            "on a single DataSource, no matter whether Hibernate or JDBC access.");
        }

        Session session = null;

        try {
            if (!txObject.hasSessionHolder() || txObject.getSessionHolder().isSynchronizedWithTransaction()) {
                Interceptor entityInterceptor = getEntityInterceptor();
                Session newSession = (entityInterceptor != null ?
                        getSessionFactory().withOptions().interceptor(entityInterceptor).openSession() :
                        getSessionFactory().openSession());
                if (logger.isDebugEnabled()) {
                    logger.debug("Opened new Session [" + newSession + "] for Hibernate transaction");
                }
                txObject.setSession(newSession);
            }

            session = txObject.getSessionHolder().getSession();

            boolean holdabilityNeeded = this.allowResultAccessAfterCompletion && !txObject.isNewSession();
            boolean isolationLevelNeeded = (definition.getIsolationLevel() != TransactionDefinition.Isolation.DEFAULT);
            if (holdabilityNeeded || isolationLevelNeeded || definition.isReadOnly()) {
                if (this.prepareConnection && isSameConnectionForEntireSession(session)) {
                    // We're allowed to change the transaction settings of the JDBC Connection.
                    if (logger.isDebugEnabled()) {
                        logger.debug("Preparing JDBC Connection of Hibernate Session [" + session + "]");
                    }
                    Connection con = ((SessionImplementor) session).connection();
                    TransactionDefinition.Isolation previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
                    txObject.setPreviousIsolationLevel(previousIsolationLevel);
                    if (this.allowResultAccessAfterCompletion && !txObject.isNewSession()) {
                        int currentHoldability = con.getHoldability();
                        if (currentHoldability != ResultSet.HOLD_CURSORS_OVER_COMMIT) {
                            txObject.setPreviousHoldability(currentHoldability);
                            con.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);
                        }
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
                        logger.debug("Not preparing JDBC Connection of Hibernate Session [" + session + "]");
                    }
                }
            }

            if (definition.isReadOnly() && txObject.isNewSession()) {
                // Just set to MANUAL in case of a new Session for this transaction.
                session.setFlushMode(FlushMode.MANUAL);
                // As of 5.1, we're also setting Hibernate's read-only entity mode by default.
                session.setDefaultReadOnly(true);
            }

            if (!definition.isReadOnly() && !txObject.isNewSession()) {
                // We need AUTO or COMMIT for a non-read-only transaction.
                FlushMode flushMode = session.getHibernateFlushMode();
                if (FlushMode.MANUAL.equals(flushMode)) {
                    session.setFlushMode(FlushMode.AUTO);
                    txObject.getSessionHolder().setPreviousFlushMode(flushMode);
                }
            }

            Transaction hibTx;

            // Register transaction timeout.
            Duration timeout = determineTimeout(definition);
            if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                // Use Hibernate's own transaction timeout mechanism on Hibernate 3.1+
                // Applies to all statements, also to inserts, updates and deletes!
                hibTx = session.getTransaction();
                hibTx.setTimeout(((int) timeout.toMillis() / 1000));
                hibTx.begin();
            } else {
                // Open a plain Hibernate transaction without specified timeout.
                hibTx = session.beginTransaction();
            }

            // Add the Hibernate transaction to the session holder.
            txObject.getSessionHolder().setTransaction(hibTx);

            // Register the Hibernate Session's JDBC Connection for the DataSource, if set.
            if (getDataSource() != null) {
                SessionImplementor sessionImpl = (SessionImplementor) session;
                // The following needs to use a lambda expression instead of a method reference
                // for compatibility with Hibernate ORM <5.2 where connection() is defined on
                // SessionImplementor itself instead of on SharedSessionContractImplementor...
                ConnectionHolder conHolder = new ConnectionHolder(sessionImpl::connection);
                if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                    conHolder.setTimeout(timeout);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Exposing Hibernate transaction as JDBC [" + conHolder.getConnectionHandle() + "]");
                }
                TransactionSynchronizationManager.bindResource(getDataSource(), conHolder);
                txObject.setConnectionHolder(conHolder);
            }

            // Bind the session holder to the thread.
            if (txObject.isNewSessionHolder()) {
                TransactionSynchronizationManager.bindResource(getSessionFactory(), txObject.getSessionHolder());
            }
            txObject.getSessionHolder().setSynchronizedWithTransaction(true);
        } catch (Throwable ex) {
            if (txObject.isNewSession()) {
                try {
                    if (session != null && session.getTransaction().getStatus() == TransactionStatus.ACTIVE) {
                        session.getTransaction().rollback();
                    }
                } catch (Throwable ex2) {
                    logger.debug("Could not rollback Session after failed transaction begin", ex);
                } finally {
                    SessionFactoryUtils.closeSession(session);
                    txObject.setSessionHolder(null);
                }
            }
            throw new CannotCreateTransactionException("Could not open Hibernate Session for transaction", ex);
        }
    }

    @Override
    protected Object doSuspend(Object transaction) {
        HibernateTransactionObject txObject = (HibernateTransactionObject) transaction;
        txObject.setSessionHolder(null);
        SessionHolder sessionHolder =
                (SessionHolder) TransactionSynchronizationManager.unbindResource(getSessionFactory());
        txObject.setConnectionHolder(null);
        ConnectionHolder connectionHolder = null;
        if (getDataSource() != null) {
            connectionHolder = (ConnectionHolder) TransactionSynchronizationManager.unbindResource(getDataSource());
        }
        return new SuspendedResourcesHolder(sessionHolder, connectionHolder);
    }

    @Override
    protected void doResume(@Nullable Object transaction, Object suspendedResources) {
        SessionFactory sessionFactory = getSessionFactory();

        SuspendedResourcesHolder resourcesHolder = (HibernateTransactionManager.SuspendedResourcesHolder) suspendedResources;
        if (TransactionSynchronizationManager.hasResource(sessionFactory)) {
            // From non-transactional code running in active transaction synchronization
            // -> can be safely removed, will be closed on transaction completion.
            TransactionSynchronizationManager.unbindResource(sessionFactory);
        }
        TransactionSynchronizationManager.bindResource(sessionFactory, resourcesHolder.getSessionHolder());
        if (getDataSource() != null && resourcesHolder.getConnectionHolder() != null) {
            TransactionSynchronizationManager.bindResource(getDataSource(), resourcesHolder.getConnectionHolder());
        }
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        HibernateTransactionObject txObject = (HibernateTransactionObject) status.getTransaction();
        Transaction hibTx = txObject.getSessionHolder().getTransaction();
        Objects.requireNonNull(hibTx, "No Hibernate transaction");
        if (status.isDebug()) {
            logger.debug("Committing Hibernate transaction on Session [" +
                    txObject.getSessionHolder().getSession() + "]");
        }

        try {
            hibTx.commit();
        } catch (org.hibernate.TransactionException ex) {
            // assumably from commit call to the underlying JDBC connection
            throw new TransactionSystemException("Could not commit Hibernate transaction", ex);
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        HibernateTransactionObject txObject = (HibernateTransactionObject) status.getTransaction();
        Transaction hibTx = txObject.getSessionHolder().getTransaction();
        Objects.requireNonNull(hibTx, "No Hibernate transaction");
        if (status.isDebug()) {
            logger.debug("Rolling back Hibernate transaction on Session [" +
                    txObject.getSessionHolder().getSession() + "]");
        }

        try {
            hibTx.rollback();
        } catch (org.hibernate.TransactionException ex) {
            throw new TransactionSystemException("Could not roll back Hibernate transaction", ex);
        } finally {
            if (!txObject.isNewSession() && !this.hibernateManagedSession) {
                // Clear all pending inserts/updates/deletes in the Session.
                // Necessary for pre-bound Sessions, to avoid inconsistent state.
                txObject.getSessionHolder().getSession().clear();
            }
        }
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        HibernateTransactionObject txObject = (HibernateTransactionObject) status.getTransaction();
        if (status.isDebug()) {
            logger.debug("Setting Hibernate transaction on Session [" +
                    txObject.getSessionHolder().getSession() + "] rollback-only");
        }
        txObject.setRollbackOnly();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void doCleanupAfterCompletion(Object transaction) {
        HibernateTransactionObject txObject = (HibernateTransactionObject) transaction;

        // Remove the session holder from the thread.
        if (txObject.isNewSessionHolder()) {
            TransactionSynchronizationManager.unbindResource(getSessionFactory());
        }

        // Remove the JDBC connection holder from the thread, if exposed.
        if (getDataSource() != null) {
            TransactionSynchronizationManager.unbindResource(getDataSource());
        }

        Session session = txObject.getSessionHolder().getSession();
        if (this.prepareConnection && isPhysicallyConnected(session)) {
            // We're running with connection release mode "on_close": We're able to reset
            // the isolation level and/or read-only flag of the JDBC Connection here.
            // Else, we need to rely on the connection pool to perform proper cleanup.
            try {
                Connection con = ((SessionImplementor) session).connection();
                Integer previousHoldability = txObject.getPreviousHoldability();
                if (previousHoldability != null) {
                    con.setHoldability(previousHoldability);
                }
                DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel());
            } catch (HibernateException ex) {
                logger.debug("Could not access JDBC Connection of Hibernate Session", ex);
            } catch (Throwable ex) {
                logger.debug("Could not reset JDBC Connection after transaction", ex);
            }
        }

        if (txObject.isNewSession()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Closing Hibernate Session [" + session + "] after transaction");
            }
            SessionFactoryUtils.closeSession(session);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Not closing pre-bound Hibernate Session [" + session + "] after transaction");
            }
            if (txObject.getSessionHolder().getPreviousFlushMode() != null) {
                session.setFlushMode(txObject.getSessionHolder().getPreviousFlushMode());
            }
            if (!this.allowResultAccessAfterCompletion && !this.hibernateManagedSession) {
                disconnectOnCompletion(session);
            }
        }
        txObject.getSessionHolder().clear();
    }

    /**
     * Disconnect a pre-existing Hibernate Session on transaction completion,
     * returning its database connection but preserving its entity state.
     * <p>The default implementation simply calls {@link Session#disconnect()}.
     * Subclasses may override this with a no-op or with fine-tuned disconnection logic.
     * @param session the Hibernate Session to disconnect
     * @see Session#disconnect()
     */
    protected void disconnectOnCompletion(Session session) {
        session.disconnect();
    }

    /**
     * Return whether the given Hibernate Session will always hold the same
     * JDBC Connection. This is used to check whether the transaction manager
     * can safely prepare and clean up the JDBC Connection used for a transaction.
     * <p>The default implementation checks the Session's connection release mode
     * to be "on_close".
     * @param session the Hibernate Session to check
     * @see ConnectionReleaseMode#ON_CLOSE
     * @return Whether the same connection is needed for the whole session
     */
    protected boolean isSameConnectionForEntireSession(Session session) {
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
     * @param session the Hibernate Session to check
     * @see #isSameConnectionForEntireSession(Session)
     * @return Is the session physically connected
     */
    protected boolean isPhysicallyConnected(Session session) {
        if (!(session instanceof SessionImplementor)) {
            // The best we can do is to check whether we're logically connected.
            return session.isConnected();
        }
        return ((SessionImplementor) session).getJdbcCoordinator().getLogicalConnection().isPhysicallyConnected();
    }

    @NonNull
    @Override
    public Connection getConnection() {
        final Session currentSession = sessionFactory.getCurrentSession();
        return ((SessionImplementor) currentSession).connection();
    }


    /**
     * Hibernate transaction object, representing a SessionHolder.
     * Used as transaction object by HibernateTransactionManager.
     */
    private class HibernateTransactionObject extends JdbcTransactionObjectSupport {

        @Nullable
        private SessionHolder sessionHolder;

        private boolean newSessionHolder;

        private boolean newSession;

        @Nullable
        private Integer previousHoldability;

        public void setSession(Session session) {
            this.sessionHolder = new SessionHolder(session);
            this.newSessionHolder = true;
            this.newSession = true;
        }

        public void setExistingSession(Session session) {
            this.sessionHolder = new SessionHolder(session);
            this.newSessionHolder = true;
            this.newSession = false;
        }

        public void setSessionHolder(@Nullable SessionHolder sessionHolder) {
            this.sessionHolder = sessionHolder;
            this.newSessionHolder = false;
            this.newSession = false;
        }

        public SessionHolder getSessionHolder() {
            Objects.requireNonNull(this.sessionHolder, "No SessionHolder available");
            return this.sessionHolder;
        }

        public boolean hasSessionHolder() {
            return (this.sessionHolder != null);
        }

        public boolean isNewSessionHolder() {
            return this.newSessionHolder;
        }

        public boolean isNewSession() {
            return this.newSession;
        }

        public void setPreviousHoldability(@Nullable Integer previousHoldability) {
            this.previousHoldability = previousHoldability;
        }

        @Nullable
        public Integer getPreviousHoldability() {
            return this.previousHoldability;
        }

        public boolean hasSpringManagedTransaction() {
            return (this.sessionHolder != null && this.sessionHolder.getTransaction() != null);
        }

        public boolean hasHibernateManagedTransaction() {
            return (this.sessionHolder != null &&
                    this.sessionHolder.getSession().getTransaction().getStatus() == TransactionStatus.ACTIVE);
        }

        public void setRollbackOnly() {
            getSessionHolder().setRollbackOnly();
            if (hasConnectionHolder()) {
                getConnectionHolder().setRollbackOnly();
            }
        }

        @Override
        public boolean isRollbackOnly() {
            return getSessionHolder().isRollbackOnly() ||
                    (hasConnectionHolder() && getConnectionHolder().isRollbackOnly());
        }

        @Override
        public void flush() {
            getSessionHolder().getSession().flush();
        }
    }


    /**
     * Holder for suspended resources.
     * Used internally by {@code doSuspend} and {@code doResume}.
     */
    private static final class SuspendedResourcesHolder {

        private final SessionHolder sessionHolder;

        @Nullable
        private final ConnectionHolder connectionHolder;

        private SuspendedResourcesHolder(SessionHolder sessionHolder, @Nullable ConnectionHolder conHolder) {
            this.sessionHolder = sessionHolder;
            this.connectionHolder = conHolder;
        }

        private SessionHolder getSessionHolder() {
            return this.sessionHolder;
        }

        @Nullable
        private ConnectionHolder getConnectionHolder() {
            return this.connectionHolder;
        }
    }

}
