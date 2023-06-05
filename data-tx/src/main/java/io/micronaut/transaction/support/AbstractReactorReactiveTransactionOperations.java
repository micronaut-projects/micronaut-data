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
package io.micronaut.transaction.support;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.data.connection.manager.reactive.ReactorReactiveConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
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
import java.util.Optional;
import java.util.function.Function;

/**
 * Abstract Reactor transaction operations.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public abstract class AbstractReactorReactiveTransactionOperations<C> implements ReactorReactiveTransactionOperations<C> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractReactorReactiveTransactionOperations.class);

    private final ReactorReactiveConnectionOperations<C> connectionOperations;

    protected AbstractReactorReactiveTransactionOperations(@Parameter ReactorReactiveConnectionOperations<C> connectionOperations) {
        this.connectionOperations = connectionOperations;
    }

    @NonNull
    protected abstract Publisher<Void> beginTransaction(@NonNull ConnectionStatus<C> connectionStatus,
                                                        @NonNull TransactionDefinition transactionDefinition);

    @NonNull
    protected abstract Publisher<Void> commitTransaction(@NonNull ConnectionStatus<C> connectionStatus,
                                                         @NonNull TransactionDefinition transactionDefinition);

    @NonNull
    protected abstract Publisher<Void> rollbackTransaction(@NonNull ConnectionStatus<C> connectionStatus,
                                                           @NonNull TransactionDefinition transactionDefinition);

    @Override
    public final Optional<ReactiveTransactionStatus<C>> findTransactionStatus(ContextView contextView) {
        return ReactorPropagation.findAllContextElements(contextView, ReactiveTransactionPropagatedContext.class)
            .filter(e -> e.transactionOperations == this)
            .map(e -> (ReactiveTransactionStatus<C>) e.status)
            .findFirst();
    }

    @Override
    public final ReactiveTransactionStatus<C> getTransactionStatus(ContextView contextView) {
        return findTransactionStatus(contextView).orElse(null);
    }

    @Override
    public final TransactionDefinition getTransactionDefinition(ContextView contextView) {
        ReactiveTransactionStatus<C> status = getTransactionStatus(contextView);
        return status == null ? null : status.getTransactionDefinition();
    }

    @Override
    @NonNull
    public final <T> Flux<T> withTransaction(@NonNull TransactionDefinition definition,
                                             @NonNull TransactionalCallback<C, T> handler) {
        Objects.requireNonNull(definition, "Transaction definition cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");

        return Flux.deferContextual(contextView -> {
            ReactiveTransactionStatus<C> transactionStatus = getTransactionStatus(contextView);
            TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
            if (transactionStatus != null) {
                if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED || propagationBehavior == TransactionDefinition.Propagation.NEVER) {
                    return Flux.error(propagationNotSupported(propagationBehavior));
                }
                if (propagationBehavior == TransactionDefinition.Propagation.REQUIRES_NEW) {
                    return connectionOperations.withConnectionFlux(definition.getConnectionDefinition(), connectionStatus ->
                        executeTransactionFlux(
                            new DefaultReactiveTransactionStatus<>(connectionStatus, true, definition),
                            handler
                        )
                    );
                }
                return executeCallbackFlux(
                    existingTransaction(transactionStatus, definition),
                    handler
                );
            }
            if (propagationBehavior == TransactionDefinition.Propagation.MANDATORY) {
                return Flux.error(expectedTransaction());
            }
            return connectionOperations.withConnectionFlux(definition.getConnectionDefinition(), connectionStatus ->
                executeTransactionFlux(
                    new DefaultReactiveTransactionStatus<>(connectionStatus, true, definition),
                    handler
                )
            );
        });
    }

    @Override
    public <T> Mono<T> withTransactionMono(TransactionDefinition definition, Function<ReactiveTransactionStatus<C>, Mono<T>> handler) {
        Objects.requireNonNull(definition, "Transaction definition cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");

        return Mono.deferContextual(contextView -> {
            ReactiveTransactionStatus<C> transactionStatus = getTransactionStatus(contextView);
            TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
            if (transactionStatus != null) {
                if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED || propagationBehavior == TransactionDefinition.Propagation.NEVER) {
                    return Mono.error(propagationNotSupported(propagationBehavior));
                }
                if (propagationBehavior == TransactionDefinition.Propagation.REQUIRES_NEW) {
                    return connectionOperations.withConnectionMono(definition.getConnectionDefinition(), connectionStatus ->
                        executeTransactionMono(
                            new DefaultReactiveTransactionStatus<>(connectionStatus, true, definition),
                            handler
                        )
                    );
                }
                return executeCallbackMono(
                    existingTransaction(transactionStatus, definition),
                    handler
                );
            }
            if (propagationBehavior == TransactionDefinition.Propagation.MANDATORY) {
                return Mono.error(expectedTransaction());
            }
            return connectionOperations.withConnectionMono(definition.getConnectionDefinition(), connectionStatus ->
                executeTransactionMono(
                    new DefaultReactiveTransactionStatus<>(connectionStatus, true, definition),
                    handler
                )
            );
        });
    }

    /**
     * Execute the transaction.
     *
     * @param txStatus The transaction status
     * @param handler  The callback
     * @param <R>      The callback result type
     * @return The callback result
     */
    @NonNull
    protected <R> Flux<R> executeTransactionFlux(@NonNull DefaultReactiveTransactionStatus<C> txStatus,
                                                 @NonNull TransactionalCallback<C, R> handler) {
        return Flux.usingWhen(
            Mono.fromDirect(
                beginTransaction(txStatus.getConnectionStatus(), txStatus.getTransactionDefinition())
            ).thenMany(Mono.just(txStatus)),
            status -> executeCallbackFlux(status, handler),
            this::doCommit,
            this::doRollback,
            this::doCommit);
    }

    /**
     * Execute the transaction.
     *
     * @param txStatus The transaction status
     * @param handler  The callback
     * @param <R>      The callback result type
     * @return The callback result
     */
    @NonNull
    protected <R> Mono<R> executeTransactionMono(@NonNull DefaultReactiveTransactionStatus<C> txStatus,
                                                 @NonNull Function<ReactiveTransactionStatus<C>, Mono<R>> handler) {
        return Mono.usingWhen(
            Mono.fromDirect(
                beginTransaction(txStatus.getConnectionStatus(), txStatus.getTransactionDefinition())
            ).thenReturn(txStatus),
            status -> executeCallbackMono(status, handler),
            this::doCommit,
            this::doRollback,
            this::doCommit);
    }

    /**
     * Execute the callback.
     *
     * @param status  The transaction status
     * @param handler The callback
     * @param <R>     The callback result type
     * @return The callback result
     */
    @NonNull
    protected <R> Flux<R> executeCallbackFlux(@NonNull ReactiveTransactionStatus<C> status,
                                              @NonNull TransactionalCallback<C, R> handler) {
        try {
            return Flux.from(handler.doInTransaction(status))
                .contextWrite(context -> addTxStatus(context, status));
        } catch (Exception e) {
            return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
        }
    }

    /**
     * Execute the callback.
     *
     * @param status  The transaction status
     * @param handler The callback
     * @param <R>     The callback result type
     * @return The callback result
     */
    @NonNull
    protected <R> Mono<R> executeCallbackMono(@NonNull ReactiveTransactionStatus<C> status,
                                              @NonNull Function<ReactiveTransactionStatus<C>, Mono<R>> handler) {
        try {
            return handler.apply(status)
                .contextWrite(context -> addTxStatus(context, status));
        } catch (Exception e) {
            return Mono.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
        }
    }

    private ReactiveTransactionStatus<C> existingTransaction(ReactiveTransactionStatus<C> existing, TransactionDefinition transactionDefinition) {
        return new ReactiveTransactionStatus<>() {
            @Override
            public C getConnection() {
                return existing.getConnection();
            }

            @Override
            public ConnectionStatus<C> getConnectionStatus() {
                return existing.getConnectionStatus();
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

            @Override
            public TransactionDefinition getTransactionDefinition() {
                return transactionDefinition;
            }
        };
    }

    @NonNull
    private Publisher<Void> doCommit(@NonNull DefaultReactiveTransactionStatus<C> status) {
        Flux<Void> op;
        if (status.isRollbackOnly()) {
            op = Flux.from(rollbackTransaction(status.getConnectionStatus(), status.getTransactionDefinition()));
        } else {
            op = Flux.from(commitTransaction(status.getConnectionStatus(), status.getTransactionDefinition()));
        }
        return op.as(flux -> doFinish(flux, status));
    }

    @NonNull
    private Publisher<Void> doRollback(@NonNull DefaultReactiveTransactionStatus<C> status, @NonNull Throwable throwable) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Rolling back transaction on error: " + throwable.getMessage(), throwable);
        }
        Flux<Void> abort;
        TransactionDefinition definition = status.getTransactionDefinition();
        if (definition.rollbackOn(throwable)) {
            abort = Flux.from(rollbackTransaction(status.getConnectionStatus(), definition));
        } else {
            abort = Flux.error(throwable);
        }
        return abort.onErrorResume((rollbackError) -> {
            if (rollbackError != throwable && LOG.isWarnEnabled()) {
                LOG.warn("Error occurred during transaction rollback: " + rollbackError.getMessage(), rollbackError);
            }
            return Mono.error(throwable);
        }).as(flux -> doFinish(flux, status));
    }

    private <T> Publisher<Void> doFinish(Flux<T> flux, DefaultReactiveTransactionStatus<C> status) {
        return flux.hasElements().map(ignore -> {
            status.completed = true;
            return ignore;
        }).then();
    }

    @NonNull
    private Context addTxStatus(@NonNull Context context, @NonNull ReactiveTransactionStatus<C> status) {
        return ReactorPropagation.addContextElement(
            context,
            new ReactiveTransactionPropagatedContext<>(this, status)
        );
    }

    @NonNull
    private NoTransactionException expectedTransaction() {
        return new NoTransactionException("Expected an existing transaction, but none was found in the Reactive context.");
    }

    @NonNull
    private TransactionUsageException propagationNotSupported(TransactionDefinition.Propagation propagationBehavior) {
        return new TransactionUsageException("Found an existing transaction but propagation behaviour doesn't support it: " + propagationBehavior);
    }

    private record ReactiveTransactionPropagatedContext<C>(
        ReactiveTransactionOperations<?> transactionOperations,
        ReactiveTransactionStatus<C> status)
        implements PropagatedContextElement {
    }

    /**
     * Represents the current reactive transaction status.
     *
     * @param <C> The connection type
     */
    protected static final class DefaultReactiveTransactionStatus<C> implements ReactiveTransactionStatus<C> {
        private final ConnectionStatus<C> connectionStatus;
        private final boolean isNew;
        private final TransactionDefinition transactionDefinition;
        private boolean rollbackOnly;
        private boolean completed;

        public DefaultReactiveTransactionStatus(ConnectionStatus<C> connectionStatus,
                                                boolean isNew,
                                                TransactionDefinition transactionDefinition) {
            this.connectionStatus = connectionStatus;
            this.isNew = isNew;
            this.transactionDefinition = transactionDefinition;
        }

        @Override
        public ConnectionStatus<C> getConnectionStatus() {
            return connectionStatus;
        }

        @Override
        public TransactionDefinition getTransactionDefinition() {
            return transactionDefinition;
        }

        @Override
        public C getConnection() {
            return connectionStatus.getConnection();
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
