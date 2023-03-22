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
package io.micronaut.data.spring.hibernate;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.spring.jdbc.SpringJdbcTransactionOperations;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionException;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.UndeclaredThrowableException;
import java.sql.Connection;
import java.time.Duration;

/**
 * Adds Spring Transaction management capability to Micronaut Data.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Requires(classes = HibernateTransactionManager.class)
@EachBean(HibernateTransactionManager.class)
@Internal
@Replaces(SpringJdbcTransactionOperations.class)
public class SpringHibernateTransactionOperations implements TransactionOperations<Connection> {

    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final SessionFactory sessionFactory;
    private final HibernateTransactionManager transactionManager;

    /**
     * Default constructor.
     * @param hibernateTransactionManager The hibernate transaction manager.
     */
    protected SpringHibernateTransactionOperations(HibernateTransactionManager hibernateTransactionManager) {
        this.sessionFactory = hibernateTransactionManager.getSessionFactory();
        this.transactionManager = hibernateTransactionManager;
        this.writeTransactionTemplate = new TransactionTemplate(hibernateTransactionManager);
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setReadOnly(true);
        this.readTransactionTemplate = new TransactionTemplate(hibernateTransactionManager, transactionDefinition);
    }

    @Override
    public <R> R executeRead(@NonNull TransactionCallback<Connection, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        return readTransactionTemplate.execute(status -> {
                    try {
                        return callback.call(new JpaTransactionStatus(status));
                    } catch (RuntimeException | Error ex) {
                        throw ex;
                    } catch (Exception e) {
                        throw new UndeclaredThrowableException(e, "TransactionCallback threw undeclared checked exception");
                    }
                }
        );
    }

    @Override
    public <R> R executeWrite(@NonNull TransactionCallback<Connection, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        return writeTransactionTemplate.execute(status -> {
                    try {
                        return callback.call(new JpaTransactionStatus(status));
                    } catch (RuntimeException | Error ex) {
                        throw ex;
                    } catch (Exception e) {
                        throw new UndeclaredThrowableException(e, "TransactionCallback threw undeclared checked exception");
                    }
                }
        );
    }

    @NonNull
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

    @Override
    public <R> R execute(@NonNull TransactionDefinition definition, @NonNull TransactionCallback<Connection, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        ArgumentUtils.requireNonNull("definition", definition);

        final DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setReadOnly(definition.isReadOnly());
        def.setIsolationLevel(definition.getIsolationLevel().getCode());
        def.setPropagationBehavior(definition.getPropagationBehavior().ordinal());
        def.setName(definition.getName());
        final Duration timeout = definition.getTimeout();
        if (!timeout.isNegative()) {
            def.setTimeout((int) timeout.getSeconds());
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager, def);
        return template.execute(status -> {
                    try {
                        return callback.call(new JpaTransactionStatus(status));
                    } catch (RuntimeException | Error ex) {
                        throw ex;
                    } catch (Exception e) {
                        throw new UndeclaredThrowableException(e, "TransactionCallback threw undeclared checked exception");
                    }
                }
        );
    }

    /**
     * Internal transaction status.
     */
    private final class JpaTransactionStatus implements TransactionStatus<Connection> {

        private final org.springframework.transaction.TransactionStatus springStatus;

        JpaTransactionStatus(org.springframework.transaction.TransactionStatus springStatus) {
            this.springStatus = springStatus;
        }

        @Override
        public boolean isNewTransaction() {
            return springStatus.isNewTransaction();
        }

        @Override
        public void setRollbackOnly() {
            springStatus.setRollbackOnly();
        }

        @Override
        public boolean isRollbackOnly() {
            return springStatus.isRollbackOnly();
        }

        @Override
        public boolean isCompleted() {
            return springStatus.isCompleted();
        }

        @Override
        public boolean hasSavepoint() {
            return springStatus.hasSavepoint();
        }

        @Override
        public void flush() {
            springStatus.flush();
        }

        @NonNull
        @Override
        public Object getTransaction() {
            return springStatus;
        }

        @NonNull
        @Override
        public Connection getConnection() {
            final SessionImplementor session = (SessionImplementor) sessionFactory.getCurrentSession();
            return session.getJdbcCoordinator().getLogicalConnection().getPhysicalConnection();
        }

        @Override
        public Object createSavepoint() throws TransactionException {
            return springStatus.createSavepoint();
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) throws TransactionException {
            springStatus.rollbackToSavepoint(savepoint);
        }

        @Override
        public void releaseSavepoint(Object savepoint) throws TransactionException {
            springStatus.releaseSavepoint(savepoint);
        }
    }
}

