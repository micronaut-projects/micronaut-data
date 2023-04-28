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
import io.micronaut.data.spring.tx.AbstractSpringTransactionOperations;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.support.TransactionSynchronization;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.orm.hibernate5.HibernateTransactionManager;

import java.sql.Connection;
import java.util.Optional;

/**
 * Adds Spring Transaction management capability to Micronaut Data.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Requires(classes = HibernateTransactionManager.class, condition = SpringHibernateTransactionManagerCondition.class)
@EachBean(HibernateTransactionManager.class)
@Internal
public final class SpringHibernateTransactionOperations implements TransactionOperations<Session> {

    private final SpringConnectionTransactionOperations transactionOperations;
    private final SessionFactory sessionFactory;

    /**
     * Default constructor.
     *
     * @param hibernateTransactionManager The hibernate transaction manager.
     */
    SpringHibernateTransactionOperations(HibernateTransactionManager hibernateTransactionManager) {
        this.sessionFactory = hibernateTransactionManager.getSessionFactory();
        this.transactionOperations = new SpringConnectionTransactionOperations(hibernateTransactionManager);
    }

    @Override
    public Optional<? extends TransactionStatus<?>> findTransactionStatus() {
        return transactionOperations.findTransactionStatus();
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
    public <R> R execute(TransactionDefinition definition, TransactionCallback<Session, R> callback) {
        return transactionOperations.execute(definition, status -> callback.call(new TransactionStatus<>() {
            @Override
            public boolean hasSavepoint() {
                return status.hasSavepoint();
            }

            @Override
            public Object getTransaction() {
                return status.getTransaction();
            }

            @Override
            public Session getConnection() {
                return sessionFactory.getCurrentSession();
            }

            @Override
            public Object createSavepoint() throws TransactionException {
                return status.createSavepoint();
            }

            @Override
            public void rollbackToSavepoint(Object savepoint) throws TransactionException {
                status.releaseSavepoint(savepoint);
            }

            @Override
            public void releaseSavepoint(Object savepoint) throws TransactionException {
                status.releaseSavepoint(savepoint);
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
            public void registerSynchronization(TransactionSynchronization synchronization) {
                status.registerSynchronization(synchronization);
            }
        }));
    }

    public static final class SpringConnectionTransactionOperations extends AbstractSpringTransactionOperations {

        private final SessionFactory sessionFactory;

        /**
         * Default constructor.
         *
         * @param hibernateTransactionManager The hibernate transaction manager.
         */
        SpringConnectionTransactionOperations(HibernateTransactionManager hibernateTransactionManager) {
            super(hibernateTransactionManager);
            this.sessionFactory = hibernateTransactionManager.getSessionFactory();
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

}

