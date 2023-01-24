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


import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.jdbc.DataSourceUtils;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class featuring methods for Hibernate Session handling.
 * Also provides support for exception translation.
 *
 * <p>Used internally by {@link HibernateTransactionManager}.
 * Can also be used directly in application code.
 *
 * @author Juergen Hoeller
 * @author graemerocher
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
     * Trigger a flush on the given Hibernate Session.
     *
     * @param session the Hibernate Session to flush
     * @param synch whether this flush is triggered by transaction synchronization
     * @since 4.3.2
     */
    static void flush(Session session, boolean synch) {
        if (synch) {
            LOG.debug("Flushing Hibernate Session on transaction synchronization");
        } else {
            LOG.debug("Flushing Hibernate Session on explicit request");
        }
        session.flush();
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

