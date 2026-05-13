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
package io.micronaut.data.spring.jpa.hibernate;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.spring.tx.AbstractSpringTransactionOperations;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.support.TransactionSynchronization;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.jspecify.annotations.Nullable;
import org.springframework.orm.jpa.hibernate.HibernateTransactionManager;

import java.sql.Connection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adds Spring Transaction management capability to Micronaut Data.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Requires(classes = HibernateTransactionManager.class, condition = SpringHibernateTransactionManagerCondition.class)
@EachBean(HibernateTransactionManager.class)
@Replaces(io.micronaut.transaction.hibernate.HibernateTransactionManager.class)
@Internal
public final class SpringHibernateTransactionOperations implements SynchronousTransactionManager<Session> {

    private final SpringJdbcConnectionTransactionOperations transactionOperations;
    private final SessionFactory sessionFactory;

    /**
     * Default constructor.
     *
     * @param hibernateTransactionManager The hibernate transaction manager.
     */
    SpringHibernateTransactionOperations(HibernateTransactionManager hibernateTransactionManager) {
        hibernateTransactionManager.setNestedTransactionAllowed(true);
        this.sessionFactory = Objects.requireNonNull(hibernateTransactionManager.getSessionFactory());
        this.transactionOperations = new SpringJdbcConnectionTransactionOperations(hibernateTransactionManager);
    }

    @Override
    public Optional<TransactionStatus<Session>> findTransactionStatus() {
        return transactionOperations.findTransactionStatus()
            .map(status -> new SessionTransactionStatus(status, status.getTransactionDefinition(), this));
    }

    @Override
    public Session getConnection() {
        return sessionFactory.getCurrentSession();
    }

    @Override
    public boolean hasConnection() {
        return transactionOperations.hasConnection();
    }

    @Override
    public <R extends @Nullable Object> R execute(TransactionDefinition definition, TransactionCallback<Session, R> callback) {
        return transactionOperations.execute(definition, status -> callback.call(new SessionTransactionStatus(status, definition, this)));
    }

    @Override
    public TransactionStatus<Session> getTransaction(TransactionDefinition definition) throws TransactionException {
        return new SessionTransactionStatus(transactionOperations.getTransaction(definition), definition, this);
    }

    @Override
    public void commit(TransactionStatus<Session> status) throws TransactionException {
        SessionTransactionStatus sessionTransactionStatus = (SessionTransactionStatus) status;
        transactionOperations.commit(sessionTransactionStatus.status);
    }

    @Override
    public void rollback(TransactionStatus<Session> status) throws TransactionException {
        SessionTransactionStatus sessionTransactionStatus = (SessionTransactionStatus) status;
        transactionOperations.rollback(sessionTransactionStatus.status);
    }

    @Override
    public boolean managesTransaction(TransactionStatus<Session> transactionStatus) {
        if (transactionStatus instanceof SessionTransactionStatus sessionTransactionStatus) {
            return sessionTransactionStatus.isConnectionOf(this);
        }
        return false;
    }

    private static final class SpringJdbcConnectionTransactionOperations extends AbstractSpringTransactionOperations {

        private final SessionFactory sessionFactory;

        /**
         * Default constructor.
         *
         * @param hibernateTransactionManager The hibernate transaction manager.
         */
        SpringJdbcConnectionTransactionOperations(HibernateTransactionManager hibernateTransactionManager) {
            super(hibernateTransactionManager);
            this.sessionFactory = Objects.requireNonNull(hibernateTransactionManager.getSessionFactory());
        }

        @Override
        public Connection getConnection() {
            final SessionImplementor session = (SessionImplementor) sessionFactory.getCurrentSession();
            return session.getJdbcCoordinator().getLogicalConnection().getPhysicalConnection();
        }

        @Override
        public boolean hasConnection() {
            final SessionImplementor session = (SessionImplementor) sessionFactory.getCurrentSession();
            return session.isConnected();
        }

    }

    private final class SessionTransactionStatus implements TransactionStatus<Session> {

        private final TransactionStatus<Connection> status;
        private final TransactionDefinition definition;
        private final TransactionOperations<?> transactionOperations;

        SessionTransactionStatus(TransactionStatus<Connection> status, TransactionDefinition definition, TransactionOperations<?> transactionOperations) {
            this.status = status;
            this.definition = definition;
            this.transactionOperations = transactionOperations;
        }

        boolean isConnectionOf(TransactionOperations<?> transactionOperations) {
            return this.transactionOperations == transactionOperations;
        }

        @Override
        @Nullable
        public Object getTransaction() {
            return status.getTransaction();
        }

        @Override
        public Session getConnection() {
            return sessionFactory.getCurrentSession();
        }

        @Override
        public ConnectionStatus<Session> getConnectionStatus() {
            throw new IllegalStateException("Connection status is not supported for Spring Hibernate TX manager!");
        }

        @Override
        public boolean isNewTransaction() {
            return status.isNewTransaction();
        }

        @Override
        public void setRollbackOnly() {
            status.setRollbackOnly();
        }

        @Override
        public boolean isRollbackOnly() {
            return status.isRollbackOnly();
        }

        @Override
        public boolean isCompleted() {
            return status.isCompleted();
        }

        @Override
        public TransactionDefinition getTransactionDefinition() {
            return definition;
        }

        @Override
        public void registerSynchronization(TransactionSynchronization synchronization) {
            status.registerSynchronization(synchronization);
        }

        @Override
        public <V> V propagate(PropagatedContext propagatedContext, Supplier<V> supplier) {
            return status.propagate(propagatedContext, supplier);
        }

        @Override
        public void propagate(Runnable runnable) {
            status.propagate(runnable);
        }

        @Override
        public <V> V propagate(Supplier<V> supplier) {
            return status.propagate(supplier);
        }
    }
}
