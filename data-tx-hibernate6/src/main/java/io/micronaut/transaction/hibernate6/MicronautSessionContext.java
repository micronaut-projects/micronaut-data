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

import io.micronaut.core.annotation.TypeHint;
import io.micronaut.transaction.jpa.JpaEntityManagerHolder;
import io.micronaut.transaction.support.SynchronousTransactionState;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.context.spi.CurrentSessionContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;

/**
 * Implementation of Hibernate 3.1's {@link CurrentSessionContext} interface
 * that delegates to {@link SessionFactoryUtils} for providing a
 *  current {@link Session}.
 *
 * <p>This CurrentSessionContext implementation can also be specified in custom
 * SessionFactory setup through the "hibernate.current_session_context_class"
 * property, with the fully qualified name of this class as value.
 *
 * @author Juergen Hoeller
 * @author graemerocher
 * @since 4.2
 */
@SuppressWarnings("serial")
@TypeHint(MicronautSessionContext.class)
public final class MicronautSessionContext implements CurrentSessionContext {

    private final SessionFactoryImplementor sessionFactory;

    /**
     * Create a new SpringSessionContext for the given Hibernate SessionFactory.
     * @param sessionFactory the SessionFactory to provide current Sessions for
     */
    public MicronautSessionContext(SessionFactoryImplementor sessionFactory) {
        this.sessionFactory = sessionFactory;
    }


    /**
     * Retrieve the Spring-managed Session for the current thread, if any.
     */
    @Override
    @SuppressWarnings("deprecation")
    public Session currentSession() throws HibernateException {
        Object value = TransactionSynchronizationManager.getResource(this.sessionFactory);
        if (value instanceof Session) {
            return (Session) value;
        } else if (value instanceof SessionHolder) {
            // HibernateTransactionManager
            SessionHolder sessionHolder = (SessionHolder) value;
            Session session = sessionHolder.getSession();
            if (!sessionHolder.isSynchronizedWithTransaction()) {
                SynchronousTransactionState state = TransactionSynchronizationManager.getSynchronousTransactionState(this.sessionFactory);
                if (state != null && state.isSynchronizationActive()) {
                    state.registerSynchronization(new SessionSynchronization(sessionHolder, this.sessionFactory, false));
                    sessionHolder.setSynchronizedWithTransaction(true);
                    // Switch to FlushMode.AUTO, as we have to assume a thread-bound Session
                    // with FlushMode.MANUAL, which needs to allow flushing within the transaction.
                    FlushMode flushMode = session.getHibernateFlushMode();
                    if (flushMode.equals(FlushMode.MANUAL) && !state.isTransactionReadOnly()) {
                        session.setFlushMode(FlushMode.AUTO.toJpaFlushMode());
                        sessionHolder.setPreviousFlushMode(flushMode);
                    }
                }
            }
            return session;
        } else if (value instanceof JpaEntityManagerHolder) {
            // JpaTransactionManager
            return ((JpaEntityManagerHolder) value).getEntityManager().unwrap(Session.class);
        }

        SynchronousTransactionState state = TransactionSynchronizationManager.getSynchronousTransactionState(this.sessionFactory);
        if (state == null || !state.isSynchronizationActive()) {
            throw new HibernateException("Could not obtain transaction-synchronized Session for current thread");
        }
        Session session = this.sessionFactory.openSession();
        if (state.isTransactionReadOnly()) {
            session.setFlushMode(FlushMode.MANUAL.toJpaFlushMode());
        }
        SessionHolder sessionHolder = new SessionHolder(session);
        state.registerSynchronization(new SessionSynchronization(sessionHolder, this.sessionFactory, true));
        TransactionSynchronizationManager.bindResource(this.sessionFactory, sessionHolder);
        sessionHolder.setSynchronizedWithTransaction(true);
        return session;
    }

}
