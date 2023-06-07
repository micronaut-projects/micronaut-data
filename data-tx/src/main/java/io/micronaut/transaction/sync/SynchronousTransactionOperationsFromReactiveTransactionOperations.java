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
package io.micronaut.transaction.sync;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.TransactionException;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Implementation of the synchronous transaction operations using a reactive transaction operations.
 *
 * @param <T> The connection type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class SynchronousTransactionOperationsFromReactiveTransactionOperations<T> implements TransactionOperations<T> {

    private final ReactorReactiveTransactionOperations<T> reactiveTransactionOperations;
    private final Scheduler scheduler;

    public SynchronousTransactionOperationsFromReactiveTransactionOperations(ReactorReactiveTransactionOperations<T> reactiveTransactionOperations,
                                                                             ExecutorService blockingExecutorService) {
        this.reactiveTransactionOperations = reactiveTransactionOperations;
        this.scheduler = Schedulers.fromExecutorService(blockingExecutorService);
    }

    @Override
    public T getConnection() {
        throw noSupported();
    }

    @Override
    public boolean hasConnection() {
        throw noSupported();
    }

    @Override
    public Optional<? extends TransactionStatus<?>> findTransactionStatus() {
        return Optional.empty();
    }

    @Override
    public <R> R execute(TransactionDefinition definition, TransactionCallback<T, R> callback) {
        Mono<R> result = reactiveTransactionOperations.withTransactionMono(definition, status -> Mono.deferContextual(contextView -> {
            try (PropagatedContext.Scope ignore = ReactorPropagation.findPropagatedContext(contextView).orElseGet(PropagatedContext::getOrEmpty).propagate()) {
                return Mono.justOrEmpty(callback.apply(new DefaultTransactionStatus<>(status)));
            }
        }).subscribeOn(scheduler)).contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, PropagatedContext.getOrEmpty()));
        return result.onErrorMap(e -> {
            if (e instanceof UndeclaredThrowableException) {
                return e.getCause();
            }
            return e;
        }).block();
    }

    @NonNull
    private IllegalStateException noSupported() {
        return new IllegalStateException("This synchronous transaction manager is implemented using blocking of the reactive transaction manager " +
            "and only supports 'execute', 'executeRead' and 'executeWrite' methods.");
    }

    private final class DefaultTransactionStatus<K> implements TransactionStatus<K> {

        private final ReactiveTransactionStatus<K> transactionStatus;

        private DefaultTransactionStatus(ReactiveTransactionStatus<K> transactionStatus) {
            this.transactionStatus = transactionStatus;
        }

        @Override
        public Object createSavepoint() throws TransactionException {
            throw noSupported();
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) throws TransactionException {
            throw noSupported();
        }

        @Override
        public void releaseSavepoint(Object savepoint) throws TransactionException {
            throw noSupported();
        }

        @Override
        public boolean isNewTransaction() {
            return transactionStatus.isNewTransaction();
        }

        @Override
        public void setRollbackOnly() {
            transactionStatus.setRollbackOnly();
        }

        @Override
        public boolean isRollbackOnly() {
            return transactionStatus.isRollbackOnly();
        }

        @Override
        public boolean isCompleted() {
            return transactionStatus.isCompleted();
        }

        @Override
        public TransactionDefinition getTransactionDefinition() {
            return transactionStatus.getTransactionDefinition();
        }

        @Override
        public boolean hasSavepoint() {
            throw noSupported();
        }

        @Override
        public void flush() {
            throw noSupported();
        }

        @Override
        public Object getTransaction() {
            throw noSupported();
        }

        @Override
        public K getConnection() {
            throw noSupported();
        }

        @Override
        public ConnectionStatus<K> getConnectionStatus() {
            return transactionStatus.getConnectionStatus();
        }
    }
}
