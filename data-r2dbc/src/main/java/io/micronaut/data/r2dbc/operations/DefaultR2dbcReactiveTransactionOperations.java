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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.r2dbc.transaction.R2dbcReactorReactiveTransactionOperations;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.IsolationLevel;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Defines an implementation of Micronaut Data's core interfaces for R2DBC.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@EachBean(ConnectionFactory.class)
@Internal
final class DefaultR2dbcReactiveTransactionOperations implements R2dbcReactorReactiveTransactionOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultR2dbcReactiveTransactionOperations.class);
    private final String dataSourceName;
    private final DefaultReactiveR2dbcConnectionOperations connectionOperations;

    DefaultR2dbcReactiveTransactionOperations(@Parameter String dataSourceName,
                                              @Parameter DefaultReactiveR2dbcConnectionOperations connectionOperations) {
        this.dataSourceName = dataSourceName;
        this.connectionOperations = connectionOperations;
    }

    @NonNull
    @Override
    public <T> Publisher<T> withTransaction(
        @NonNull ReactiveTransactionStatus<Connection> status,
        @NonNull TransactionalCallback<Connection, T> handler) {
        Objects.requireNonNull(status, "Transaction status cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");
        return Flux.defer(() -> {
            try {
                return handler.doInTransaction(status);
            } catch (Exception e) {
                return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
            }
        }).contextWrite(context -> addTxStatus(context, status));
    }

    @Override
    public Optional<ReactiveTransactionStatus<Connection>> findTransactionStatus(ContextView contextView) {
        return ReactorPropagation.findAllContextElements(contextView, R2dbcTransactionPropagatedContext.class)
            .filter(e -> e.transactionOperations == this)
            .map(R2dbcTransactionPropagatedContext::status)
            .findFirst();
    }

    @Override
    public TransactionDefinition getTransactionDefinition(ContextView contextView) {
        throw new IllegalStateException();
    }

    @Override
    @NonNull
    public <T> Flux<T> withTransaction(@NonNull TransactionDefinition definition,
                                       @NonNull TransactionalCallback<Connection, T> handler) {
        Objects.requireNonNull(definition, "Transaction definition cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");

        return Flux.deferContextual(contextView -> {
            TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
            ReactiveTransactionStatus<Connection> transactionStatus = getTransactionStatus(contextView);
            if (transactionStatus != null) {
                // existing transaction, use it
                if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED || propagationBehavior == TransactionDefinition.Propagation.NEVER) {
                    return Flux.error(new TransactionUsageException("Found an existing transaction but propagation behaviour doesn't support it: " + propagationBehavior));
                }
                ReactiveTransactionStatus<Connection> existingTransaction = existingTransaction(transactionStatus);
                try {
                    return Flux.from(handler.doInTransaction(existingTransaction))
                        .contextWrite(ctx -> addTxStatus(ctx, existingTransaction));
                } catch (Exception e) {
                    return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                }
            } else {
                if (propagationBehavior == TransactionDefinition.Propagation.MANDATORY) {
                    return Flux.error(new NoTransactionException("Expected an existing transaction, but none was found in the Reactive context."));
                }
                return connectionOperations.withConnectionFluxWithCloseCallback(definition.getConnectionDefinition(), (connectionStatus, cancelCallback) -> {
                        Connection connection = connectionStatus.getConnection();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Transaction: {} begin for dataSource: {}", definition.getName(), dataSourceName);
                        }
                        DefaultReactiveTransactionStatus status = new DefaultReactiveTransactionStatus(definition, connection, true);
                        Mono<Boolean> resourceSupplier;
                        if (definition.getIsolationLevel() != TransactionDefinition.DEFAULT.getIsolationLevel()) {
                            IsolationLevel isolationLevel = getIsolationLevel(definition);
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Setting Isolation Level ({}) for transaction: {} for dataSource: {}", isolationLevel, definition.getName(), dataSourceName);
                            }
                            if (isolationLevel != null) {
                                resourceSupplier = Flux.from(connection.setTransactionIsolationLevel(isolationLevel))
                                    .thenMany(connection.beginTransaction())
                                    .hasElements();
                            } else {
                                resourceSupplier = Flux.from(connection.beginTransaction()).hasElements();
                            }
                        } else {
                            resourceSupplier = Flux.from(connection.beginTransaction()).hasElements();
                        }

                        Function<Boolean, Publisher<?>> onSuccess = ignore -> doCommit(status, cancelCallback);
                        BiFunction<Boolean, Throwable, Publisher<?>> onException = (b, throwable) -> onException(status, definition, throwable, cancelCallback);

                        return Flux.usingWhen(resourceSupplier,
                            (b) -> {
                                try {
                                    return Flux.from(handler.doInTransaction(status))
                                        .contextWrite(context -> addTxStatus(context, status));
                                } catch (Exception e) {
                                    return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                                }
                            },
                            onSuccess,
                            onException,
                            onSuccess);
                    }
                );
            }
        });
    }

    private Flux<Void> onException(DefaultReactiveTransactionStatus status,
                                   TransactionDefinition definition,
                                   Throwable throwable,
                                   Supplier<Publisher<Void>> cancelConnection) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Rolling back transaction: {} on error: {} for dataSource {}",
                status.getDefinition().getName(), throwable.getMessage(), dataSourceName, throwable);
        }
        if (!definition.rollbackOn(throwable)) {
            return doCommit(status, cancelConnection);
        }
        return rollback(status, cancelConnection)
            .onErrorResume((rollbackError) -> {
                if (rollbackError != throwable && LOG.isWarnEnabled()) {
                    LOG.warn("Error occurred during transaction: {} rollback failed with: {} for dataSource {}",
                        status.getDefinition().getName(), rollbackError.getMessage(), dataSourceName, rollbackError);
                }
                return Mono.error(throwable);
            });
    }

    private Flux<Void> rollback(DefaultReactiveTransactionStatus status, Supplier<Publisher<Void>> cancelConnection) {
        return Flux.from(status.getConnection().rollbackTransaction()).as(flux -> finishTx(flux, status, cancelConnection));
    }

    private Flux<Void> doCommit(DefaultReactiveTransactionStatus status, Supplier<Publisher<Void>> cancelConnection) {
        if (status.isRollbackOnly()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Rolling back transaction: {} for dataSource {}", status.getDefinition().getName(), dataSourceName);
            }
            return rollback(status, cancelConnection);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Committing transaction: {} for dataSource {}", status.getDefinition().getName(), dataSourceName);
        }
        return Flux.from(status.getConnection().commitTransaction()).as(flux -> finishTx(flux, status, cancelConnection));

    }

    private Flux<Void> finishTx(Flux<Void> flux, DefaultReactiveTransactionStatus status, Supplier<Publisher<Void>> cancelConnection) {
        return flux.hasElements()
            .flatMapMany(ignore -> {
                status.completed = true;
                return cancelConnection.get();
            });
    }

    @NonNull
    private Context addTxStatus(@NonNull Context context, @NonNull ReactiveTransactionStatus<Connection> status) {
        return ReactorPropagation.addContextElement(
            context,
            new R2dbcTransactionPropagatedContext(this, status)
        );
    }

    private record R2dbcTransactionPropagatedContext(
        ReactiveTransactionOperations<?> transactionOperations,
        ReactiveTransactionStatus<Connection> status)
        implements PropagatedContextElement {
    }

    private IsolationLevel getIsolationLevel(TransactionDefinition definition) {
        return switch (definition.getIsolationLevel()) {
            case READ_COMMITTED -> IsolationLevel.READ_COMMITTED;
            case READ_UNCOMMITTED -> IsolationLevel.READ_UNCOMMITTED;
            case REPEATABLE_READ -> IsolationLevel.REPEATABLE_READ;
            case SERIALIZABLE -> IsolationLevel.SERIALIZABLE;
            default -> null;
        };
    }

    private ReactiveTransactionStatus<Connection> existingTransaction(ReactiveTransactionStatus<Connection> existing) {
        return new ReactiveTransactionStatus<>() {
            @Override
            public Connection getConnection() {
                return existing.getConnection();
            }

            @Override
            public boolean isNewTransaction() {
                return false;
            }

            @Override
            public void setRollbackOnly() {
                existing.setRollbackOnly();
            }

            @Override
            public boolean isRollbackOnly() {
                return existing.isRollbackOnly();
            }

            @Override
            public boolean isCompleted() {
                return existing.isCompleted();
            }
        };
    }

    /**
     * Represents the current reactive transaction status.
     */
    private static final class DefaultReactiveTransactionStatus implements ReactiveTransactionStatus<Connection> {
        private final TransactionDefinition definition;
        private final Connection connection;
        private final boolean isNew;
        private boolean rollbackOnly;
        private boolean completed;

        public DefaultReactiveTransactionStatus(TransactionDefinition definition, Connection connection, boolean isNew) {
            this.definition = definition;
            this.connection = connection;
            this.isNew = isNew;
        }

        public TransactionDefinition getDefinition() {
            return definition;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public boolean isNewTransaction() {
            return isNew;
        }

        @Override
        public void setRollbackOnly() {
            this.rollbackOnly = true;
        }

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        @Override
        public boolean isCompleted() {
            return completed;
        }
    }
}
