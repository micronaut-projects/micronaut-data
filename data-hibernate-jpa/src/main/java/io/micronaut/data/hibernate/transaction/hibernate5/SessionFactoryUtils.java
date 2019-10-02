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
package io.micronaut.data.hibernate.transaction.hibernate5;


import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.transaction.jdbc.DataSourceUtils;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;

/**
 * Helper class featuring methods for Hibernate Session handling.
 * Also provides support for exception translation.
 *
 * <p>Used internally by {@link HibernateTransactionManager}.
 * Can also be used directly in application code.
 *
 * @author Juergen Hoeller
 * @since 4.2
 * @see HibernateTransactionManager
 */
public abstract class SessionFactoryUtils {

    /**
     * Order value for TransactionSynchronization objects that clean up Hibernate Sessions.
     * Returns {@code DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 100}
     * to execute Session cleanup before JDBC Connection cleanup, if any.
     * @see DataSourceUtils#CONNECTION_SYNCHRONIZATION_ORDER
     */
    public static final int SESSION_SYNCHRONIZATION_ORDER =
            DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 100;

    private static final Logger LOG = LoggerFactory.getLogger(SessionFactoryUtils.class);

    /**
     * Trigger a flush on the given Hibernate Session, converting regular
     * {@link HibernateException} instances as well as Hibernate 5.2's
     * {@link PersistenceException} wrappers accordingly.
     * @param session the Hibernate Session to flush
     * @param synch whether this flush is triggered by transaction synchronization
     * @throws DataAccessException in case of flush failures
     * @since 4.3.2
     */
    static void flush(Session session, boolean synch) throws DataAccessException {
        if (synch) {
            LOG.debug("Flushing Hibernate Session on transaction synchronization");
        } else {
            LOG.debug("Flushing Hibernate Session on explicit request");
        }
        try {
            session.flush();
        } catch (PersistenceException ex) {
            if (ex.getCause() instanceof NoResultException) {
                throw new EmptyResultException();
            }
            throw ex;
        }

    }

    /**
     * Perform actual closing of the Hibernate Session,
     * catching and logging any cleanup exceptions thrown.
     * @param session the Hibernate Session to close (may be {@code null})
     * @see Session#close()
     */
    static void closeSession(@Nullable Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (HibernateException ex) {
                LOG.debug("Could not close Hibernate Session", ex);
            } catch (Throwable ex) {
                LOG.debug("Unexpected exception on closing Hibernate Session", ex);
            }
        }
    }

}

