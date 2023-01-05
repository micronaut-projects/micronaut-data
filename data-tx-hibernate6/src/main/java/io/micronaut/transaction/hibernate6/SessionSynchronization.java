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
package io.micronaut.transaction.hibernate6;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.support.TransactionSynchronization;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionImplementor;


/**
 * Callback for resource cleanup at the end of a Spring-managed transaction
 * for a pre-bound Hibernate Session.
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 4.2
 */
@Internal
public class SessionSynchronization implements TransactionSynchronization, Ordered {

    private final SessionHolder sessionHolder;

    private final SessionFactory sessionFactory;

    private final boolean newSession;

    private boolean holderActive = true;

    /**
     * Default constructor.
     * @param sessionHolder The session holder
     * @param sessionFactory The session factory
     * @param newSession Is this a new session
     */
    SessionSynchronization(
        @NonNull SessionHolder sessionHolder,
        @NonNull SessionFactory sessionFactory,
        boolean newSession) {
        this.sessionHolder = sessionHolder;
        this.sessionFactory = sessionFactory;
        this.newSession = newSession;
    }

    private Session getCurrentSession() {
        return this.sessionHolder.getSession();
    }

    @Override
    public int getOrder() {
        return SessionFactoryUtils.SESSION_SYNCHRONIZATION_ORDER;
    }

    @Override
    public void suspend() {
        if (this.holderActive) {
            TransactionSynchronizationManager.unbindResource(this.sessionFactory);
            // Eagerly disconnect the Session here, to make release mode "on_close" work on JBoss.
            ((SessionImplementor) getCurrentSession()).getJdbcCoordinator().getLogicalConnection().manualDisconnect();
        }
    }

    @Override
    public void resume() {
        if (this.holderActive) {
            TransactionSynchronizationManager.bindResource(this.sessionFactory, this.sessionHolder);
        }
    }

    @Override
    public void flush() {
        SessionFactoryUtils.flush(getCurrentSession(), false);
    }

    @Override
    public void beforeCommit(boolean readOnly) throws TransactionException {
        if (!readOnly) {
            Session session = getCurrentSession();
            // Read-write transaction -> flush the Hibernate Session.
            // Further check: only flush when not FlushMode.MANUAL.
            if (!FlushMode.MANUAL.equals(session.getHibernateFlushMode())) {
                SessionFactoryUtils.flush(getCurrentSession(), true);
            }
        }
    }

    @Override
    public void beforeCompletion() {
        try {
            Session session = this.sessionHolder.getSession();
            if (this.sessionHolder.getPreviousFlushMode() != null) {
                // In case of pre-bound Session, restore previous flush mode.
                session.setFlushMode(this.sessionHolder.getPreviousFlushMode().toJpaFlushMode());
            }
            // Eagerly disconnect the Session here, to make release mode "on_close" work nicely.
            ((SessionImplementor) session).getJdbcCoordinator().getLogicalConnection().manualDisconnect();
        } finally {
            // Unbind at this point if it's a new Session...
            if (this.newSession) {
                TransactionSynchronizationManager.unbindResource(this.sessionFactory);
                this.holderActive = false;
            }
        }
    }

    @Override
    public void afterCommit() {
    }

    @Override
    public void afterCompletion(@NonNull Status status) {
        try {
            if (status != Status.COMMITTED) {
                // Clear all pending inserts/updates/deletes in the Session.
                // Necessary for pre-bound Sessions, to avoid inconsistent state.
                this.sessionHolder.getSession().clear();
            }
        } finally {
            this.sessionHolder.setSynchronizedWithTransaction(false);
            // Call close() at this point if it's a new Session...
            if (this.newSession) {
                SessionFactoryUtils.closeSession(this.sessionHolder.getSession());
            }
        }
    }
}
