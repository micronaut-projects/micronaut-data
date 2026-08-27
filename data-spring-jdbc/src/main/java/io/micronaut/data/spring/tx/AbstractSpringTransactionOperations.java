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
package io.micronaut.data.spring.tx;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.support.AbstractPropagatedStatusTransactionOperations;
import io.micronaut.transaction.support.ExceptionUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.UndeclaredThrowableException;
import java.sql.Connection;
import java.util.function.Supplier;

/**
 * Adds Spring Transaction management capability to Micronaut Data.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public abstract class AbstractSpringTransactionOperations
        extends AbstractPropagatedStatusTransactionOperations<TransactionStatus<Connection>, Connection>
        implements SynchronousTransactionManager<Connection> {

    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;

    protected AbstractSpringTransactionOperations(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setReadOnly(true);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager, transactionDefinition);
    }

    @Override
    public boolean managesTransaction(TransactionStatus<Connection> transactionStatus) {
        if (transactionStatus instanceof SpringTransactionStatus springTransactionStatus) {
            return springTransactionStatus.isConnectionOf(this);
        }
        return false;
    }

    @Override
    public TransactionStatus<Connection> getTransaction(TransactionDefinition definition) throws TransactionException {
        DefaultTransactionDefinition def = asSpringTxDefinition(definition);
        org.springframework.transaction.TransactionStatus transaction = transactionManager.getTransaction(def);
        return new SpringTransactionStatus(transaction, definition, this);
    }

    @Override
    public void commit(TransactionStatus<Connection> status) throws TransactionException {
        SpringTransactionStatus springTransactionStatus = (SpringTransactionStatus) status;
        transactionManager.commit(springTransactionStatus.springStatus);
    }

    @Override
    public void rollback(TransactionStatus<Connection> status) throws TransactionException {
        SpringTransactionStatus springTransactionStatus = (SpringTransactionStatus) status;
        transactionManager.rollback(springTransactionStatus.springStatus);
    }

    @Override
    public <R> R executeRead(@NonNull TransactionCallback<Connection, R> callback) {
        return execute(readTransactionTemplate, callback, TransactionDefinition.READ_ONLY);
    }

    @Override
    public <R> R executeWrite(@NonNull TransactionCallback<Connection, R> callback) {
        return execute(writeTransactionTemplate, callback, TransactionDefinition.DEFAULT);
    }

    @Override
    protected <R> R doExecute(TransactionDefinition definition, TransactionCallback<Connection, R> callback) {
        ArgumentUtils.requireNonNull("callback", callback);
        ArgumentUtils.requireNonNull("definition", definition);

        final DefaultTransactionDefinition def = asSpringTxDefinition(definition);

        return execute(new TransactionTemplate(transactionManager, def), callback, definition);
    }

    @SuppressWarnings("NullAway")
    private DefaultTransactionDefinition asSpringTxDefinition(TransactionDefinition definition) {
        validateTransactionDefinition(definition);
        final DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        definition.isReadOnly().ifPresent(def::setReadOnly);
        def.setIsolationLevel(definition.getIsolationLevel().orElse(TransactionDefinition.Isolation.DEFAULT).getCode());
        def.setPropagationBehavior(definition.getPropagationBehavior().ordinal());
        def.setName(definition.getName());
        definition.getTimeout().ifPresent(timeout -> {
            if (!timeout.isNegative()) {
                def.setTimeout((int) timeout.getSeconds());
            }
        });
        return def;
    }

    @SuppressWarnings("NullAway")
    private <R> R execute(TransactionTemplate template,
                          TransactionCallback<Connection, R> callback,
                          TransactionDefinition transactionDefinition) {
        ArgumentUtils.requireNonNull("callback", callback);
        try {
            return template.execute(status -> execute(callback, status, transactionDefinition));
        } catch (UndeclaredThrowableException e) {
            return ExceptionUtil.sneakyThrow(e.getUndeclaredThrowable());
        }
    }

    @Nullable
    private <R> R execute(TransactionCallback<Connection, R> callback,
                          org.springframework.transaction.TransactionStatus status,
                          TransactionDefinition transactionDefinition) {
        SpringTransactionStatus txStatus = new SpringTransactionStatus(status, transactionDefinition, this);
        try {
            return callback.call(txStatus);
        } catch (RuntimeException | Error ex) {
            throw ex;
        } catch (Exception e) {
            return ExceptionUtil.sneakyThrow(e);
        }
    }

    /**
     * Internal transaction status.
     */
    private final class SpringTransactionStatus implements TransactionStatus<Connection> {

        private final org.springframework.transaction.TransactionStatus springStatus;
        private final TransactionDefinition transactionDefinition;
        private final TransactionOperations<Connection> transactionOperations;

        SpringTransactionStatus(org.springframework.transaction.TransactionStatus springStatus, TransactionDefinition transactionDefinition, TransactionOperations<Connection> transactionOperations) {
            this.springStatus = springStatus;
            this.transactionDefinition = transactionDefinition;
            this.transactionOperations = transactionOperations;
        }

        boolean isConnectionOf(TransactionOperations<Connection> transactionOperations) {
            return this.transactionOperations == transactionOperations;
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
        public TransactionDefinition getTransactionDefinition() {
            return transactionDefinition;
        }

        @NonNull
        @Override
        public Object getTransaction() {
            return springStatus;
        }

        @NonNull
        @Override
        public Connection getConnection() {
            return AbstractSpringTransactionOperations.this.getConnection();
        }

        @Override
        public <V> V propagate(Supplier<V> supplier) {
            return PropagatedContext.getOrEmpty().plus(this).propagate(supplier);
        }

        @Override
        public ConnectionStatus<Connection> getConnectionStatus() {
            throw new IllegalStateException("Connections status not supported for the Spring TX manager!");
        }

        @Override
        public void registerSynchronization(io.micronaut.transaction.support. @NonNull TransactionSynchronization synchronization) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public int getOrder() {
                    return synchronization.getOrder();
                }

                @Override
                public void beforeCommit(boolean readOnly) {
                    synchronization.beforeCommit(readOnly);
                }

                @Override
                public void beforeCompletion() {
                    synchronization.beforeCompletion();
                }

                @Override
                public void afterCommit() {
                    synchronization.afterCommit();
                }

                @Override
                public void afterCompletion(int status) {
                    switch (status) {
                        case 0 ->
                            synchronization.afterCompletion(io.micronaut.transaction.support.TransactionSynchronization.Status.COMMITTED);
                        case 1 ->
                            synchronization.afterCompletion(io.micronaut.transaction.support.TransactionSynchronization.Status.ROLLED_BACK);
                        case 2 ->
                            synchronization.afterCompletion(io.micronaut.transaction.support.TransactionSynchronization.Status.UNKNOWN);
                        default -> throw new IllegalStateException("Unknown status: " + status);
                    }
                }
            });
        }
    }
}
