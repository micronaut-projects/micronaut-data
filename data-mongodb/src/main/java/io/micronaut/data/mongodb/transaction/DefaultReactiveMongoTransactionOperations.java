/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.mongodb.transaction;

import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.manager.reactive.ReactorReactiveConnectionOperations;
import io.micronaut.data.mongodb.conf.RequiresReactiveMongo;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;

/**
 * The reactive MongoDB trasactions operations implementation.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@RequiresReactiveMongo
@EachBean(MongoClient.class)
@Internal
final class DefaultReactiveMongoTransactionOperations implements ReactorReactiveTransactionOperations<ClientSession> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveMongoTransactionOperations.class);
    private final String serverName;
    private final ReactorReactiveConnectionOperations<ClientSession> connectionOperations;

    /**
     * Default constructor.
     *
     * @param serverName           The server name
     * @param connectionOperations The connection operations
     */
    DefaultReactiveMongoTransactionOperations(@Parameter String serverName,
                                              @Parameter ReactorReactiveConnectionOperations<ClientSession> connectionOperations) {
        this.serverName = serverName;
        this.connectionOperations = connectionOperations;
    }

    @Override
    public ReactiveTransactionStatus<ClientSession> getTransactionStatus(ContextView contextView) {
        return findTxStatus(contextView);
    }

    @Override
    public TransactionDefinition getTransactionDefinition(ContextView contextView) {
        return null; // TODO:
    }

    @Override
    @NonNull
    public <T> Flux<T> withTransaction(@NonNull TransactionDefinition definition,
                                       @NonNull TransactionalCallback<ClientSession, T> handler) {
        Objects.requireNonNull(definition, "Transaction definition cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");

        return Flux.deferContextual(contextView -> {
            ReactiveTransactionStatus<ClientSession> transactionStatus = getTransactionStatus(contextView);
            TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
            if (transactionStatus != null) {
                // existing transaction, use it
                if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED || propagationBehavior == TransactionDefinition.Propagation.NEVER) {
                    return Flux.error(new TransactionUsageException("Found an existing transaction but propagation behaviour doesn't support it: " + propagationBehavior));
                }
                ReactiveTransactionStatus<ClientSession> existingTransaction = existingTransaction(transactionStatus);
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
                return connectionOperations.withConnectionFlux(definition.getConnectionDefinition(), clientSession -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Transaction Begin for MongoDB configuration: {}", serverName);
                    }
                    DefaultReactiveTransactionStatus status = new DefaultReactiveTransactionStatus(clientSession, true);
                    if (definition.getIsolationLevel() != TransactionDefinition.DEFAULT.getIsolationLevel()) {
                        throw new TransactionUsageException("Isolation level not supported");
                    } else {
                        clientSession.startTransaction();
                    }

                    return Flux.usingWhen(Mono.just(status), sts -> {
                        try {
                            return Flux.from(handler.doInTransaction(status))
                                .contextWrite(context -> addTxStatus(context, status));
                        } catch (Exception e) {
                            return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                        }
                    }, this::doCommit, (sts, throwable) -> {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Rolling back transaction on error: " + throwable.getMessage(), throwable);
                        }
                        Flux<Void> abort;
                        if (definition.rollbackOn(throwable)) {
                            abort = Flux.from(sts.getConnection().abortTransaction());
                        } else {
                            abort = Flux.error(throwable);
                        }
                        return abort.onErrorResume((rollbackError) -> {
                            if (rollbackError != throwable && LOG.isWarnEnabled()) {
                                LOG.warn("Error occurred during transaction rollback: " + rollbackError.getMessage(), rollbackError);
                            }
                            return Mono.error(throwable);
                        }).as(flux -> doFinish(flux, status));

                    }, this::doCommit);
                });
            }
        });

    }

    private ReactiveTransactionStatus<ClientSession> existingTransaction(ReactiveTransactionStatus<ClientSession> existing) {
        return new ReactiveTransactionStatus<>() {
            @Override
            public ClientSession getConnection() {
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

    private Publisher<Void> doCommit(DefaultReactiveTransactionStatus status) {
        if (status.isRollbackOnly()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Rolling back transaction on MongoDB configuration {}.", status);
            }
            return Flux.from(status.getConnection().abortTransaction()).as(flux -> doFinish(flux, status));
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Committing transaction for MongoDB configuration {}.", status);
            }
            return Flux.from(status.getConnection().commitTransaction()).as(flux -> doFinish(flux, status));
        }
    }

    private <T> Publisher<Void> doFinish(Flux<T> flux, DefaultReactiveTransactionStatus status) {
        return flux.hasElements().map(ignore -> {
            status.completed = true;
            return ignore;
        }).then();
    }

    @NonNull
    private Context addTxStatus(@NonNull Context context, @NonNull ReactiveTransactionStatus status) {
        return ReactorPropagation.addContextElement(
            context,
            new MongoTransactionPropagatedContext(this, status)
        );
    }

    @Nullable
    private ReactiveTransactionStatus<ClientSession> findTxStatus(@NonNull ContextView contextView) {
        return ReactorPropagation.findAllContextElements(contextView, MongoTransactionPropagatedContext.class)
            .filter(e -> e.transactionOperations == this)
            .map(MongoTransactionPropagatedContext::status)
            .findFirst()
            .orElse(null);
    }

    private record MongoTransactionPropagatedContext(
        ReactiveTransactionOperations<?> transactionOperations,
        ReactiveTransactionStatus<ClientSession> status)
        implements PropagatedContextElement {
    }

    /**
     * Represents the current reactive transaction status.
     */
    private static final class DefaultReactiveTransactionStatus implements ReactiveTransactionStatus<ClientSession> {
        private final ClientSession connection;
        private final boolean isNew;
        private boolean rollbackOnly;
        private boolean completed;

        public DefaultReactiveTransactionStatus(ClientSession connection, boolean isNew) {
            this.connection = connection;
            this.isNew = isNew;
        }

        @Override
        public ClientSession getConnection() {
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
