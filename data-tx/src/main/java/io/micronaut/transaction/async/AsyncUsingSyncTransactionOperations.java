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
package io.micronaut.transaction.async;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Implementation of the asynchronous transaction manager using a synchronous transaction manager.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class AsyncUsingSyncTransactionOperations<C> implements AsyncTransactionOperations<C> {

    private final SynchronousTransactionManager<C> synchronousTransactionManager;

    public AsyncUsingSyncTransactionOperations(SynchronousTransactionManager<C> synchronousTransactionManager) {
        this.synchronousTransactionManager = synchronousTransactionManager;
    }

    @Override
    public boolean managesTransaction(AsyncTransactionStatus<C> transactionStatus) {
        if (transactionStatus instanceof DefaultAsyncTransactionStatus<C> status) {
            return status.operations == this;
        }
        return false;
    }

    @Override
    public <T extends @Nullable Object> CompletionStage<T> withTransaction(TransactionDefinition definition,
                                                                          Function<AsyncTransactionStatus<C>, CompletionStage<T>> handler) {
        CompletableFuture<T> newResult = new CompletableFuture<>();
        TransactionStatus<C> status = synchronousTransactionManager.getTransaction(definition);
        CompletionStage<T> result;
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        try {
            DefaultAsyncTransactionStatus<C> txStatus = new DefaultAsyncTransactionStatus<>(status, this);
            result = txStatus.propagate(propagatedContext.plus(status), () -> handler.apply(txStatus));
        } catch (Throwable e) {
            CompletableFuture<T> r = new CompletableFuture<>();
            r.completeExceptionally(e);
            result = r;
        }

        // Last step to complete the TX, we need to use `withState` to properly setup thread-locals for the TX manager
        result.whenComplete((o, throwable) -> {
            propagatedContext.propagate(() -> {
                if (throwable == null) {
                    try {
                        synchronousTransactionManager.commit(status);
                    } catch (Throwable e) {
                        newResult.completeExceptionally(e);
                        return null;
                    }
                    newResult.complete(o);
                } else {
                    try {
                        synchronousTransactionManager.rollback(status);
                    } catch (Throwable e) {
                        // Ignore rethrow
                    }
                    newResult.completeExceptionally(throwable);
                }
                return null;
            });
        });
        return newResult;
    }

    private record DefaultAsyncTransactionStatus<T>(
        TransactionStatus<T> status,
        AsyncTransactionOperations<T> operations) implements AsyncTransactionStatus<T> {

        @Override
        public ConnectionStatus<T> getConnectionStatus() {
            return status.getConnectionStatus();
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
            return status.getTransactionDefinition();
        }

        @Override
        @NonNull
        public T getConnection() {
            return status.getConnection();
        }
    }
}
