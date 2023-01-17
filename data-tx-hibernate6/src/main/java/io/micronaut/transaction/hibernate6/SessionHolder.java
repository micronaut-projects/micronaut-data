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
import io.micronaut.transaction.jpa.JpaEntityManagerHolder;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.Transaction;

/**
 * Resource holder wrapping a Hibernate {@link Session} (plus an optional {@link Transaction}).
 * {@link org.springframework.orm.hibernate5.HibernateTransactionManager} binds instances of this class to the thread,
 * for a given {@link org.hibernate.SessionFactory}. Extends {@link JpaEntityManagerHolder}
 * as of 5.1, automatically exposing an {@code EntityManager} handle on Hibernate 5.2+.
 *
 * <p>Note: This is an SPI class, not intended to be used by applications.
 *
 * @author Juergen Hoeller
 * @since 4.2
 * @see HibernateTransactionManager
 * @see SessionFactoryUtils
 */
public class SessionHolder extends JpaEntityManagerHolder {

    private final Session session;

    @Nullable
    private Transaction transaction;

    @Nullable
    private FlushMode previousFlushMode;

    /**
     * Default constructor.
     * @param session The session
     */
    public SessionHolder(Session session) {
        // Check below is always true against Hibernate >= 5.2 but not against 5.0/5.1 at runtime
        super(session);
        this.session = session;
    }

    /**
     * @return Get the associated session
     */
    public Session getSession() {
        return this.session;
    }

    /**
     * Set the associated transaction.
     * @param transaction The transaction
     */
    public void setTransaction(@Nullable Transaction transaction) {
        this.transaction = transaction;
        setTransactionActive(transaction != null);
    }

    /**
     * @return Get the current transaction.
     */
    @Nullable
    public Transaction getTransaction() {
        return this.transaction;
    }

    /**
     * @param previousFlushMode The previous flush mode
     */
    public void setPreviousFlushMode(@Nullable FlushMode previousFlushMode) {
        this.previousFlushMode = previousFlushMode;
    }

    /**
     * @return The the previous flush mode
     */
    @Nullable
    public FlushMode getPreviousFlushMode() {
        return this.previousFlushMode;
    }

    @Override
    public void clear() {
        super.clear();
        this.transaction = null;
        this.previousFlushMode = null;
    }

}
