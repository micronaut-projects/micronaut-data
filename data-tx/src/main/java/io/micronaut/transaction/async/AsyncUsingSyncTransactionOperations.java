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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.interceptor.CoroutineTxHelper;
import io.micronaut.transaction.interceptor.KotlinInterceptedMethodAsyncResultSupplier;
import io.micronaut.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
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
    @Nullable
    private final CoroutineTxHelper coroutineTxHelper;

    public AsyncUsingSyncTransactionOperations(SynchronousTransactionManager<C> synchronousTransactionManager,
                                               @Nullable
                                               CoroutineTxHelper coroutineTxHelper) {
        this.synchronousTransactionManager = synchronousTransactionManager;
        this.coroutineTxHelper = coroutineTxHelper;
    }

    @Override
    public <T> CompletionStage<T> withTransaction(TransactionDefinition definition,
                                                  Function<AsyncTransactionStatus<C>, CompletionStage<T>> handler) {
        return TransactionSynchronizationManager.withGuardedState(() -> {
            TransactionSynchronizationManager.TransactionSynchronizationState state = TransactionSynchronizationManager.getOrCreateState();
            if (coroutineTxHelper != null && handler instanceof KotlinInterceptedMethodAsyncResultSupplier) {
                Objects.requireNonNull(coroutineTxHelper).setupTxState(
                        ((KotlinInterceptedMethodAsyncResultSupplier) handler).getKotlinInterceptedMethod(), state);
            }

            TransactionStatus<C> status = synchronousTransactionManager.getTransaction(definition);
            CompletionStage<T> result;
            try {
                result = handler.apply(new DefaultAsyncTransactionStatus<>(status));
            } catch (Exception e) {
                CompletableFuture<T> r = new CompletableFuture<>();
                r.completeExceptionally(e);
                result = r;
            }

            CompletableFuture<T> newResult = new CompletableFuture<>();
            // Last step to complete the TX, we need to use `withState` to properly setup thread-locals for the TX manager
            result.whenComplete((o, throwable) -> TransactionSynchronizationManager.withState(state, () -> {
                if (throwable == null) {
                    synchronousTransactionManager.commit(status);
                    newResult.complete(o);
                } else {
                    try {
                        synchronousTransactionManager.rollback(status);
                    } catch (Exception e) {
                        // Ignore rethrow
                    }
                    newResult.completeExceptionally(throwable);
                }
                return null;
            }));
            return newResult;
        });
    }

    private static final class DefaultAsyncTransactionStatus<T> implements AsyncTransactionStatus<T> {

        private final TransactionStatus<T> status;

        private DefaultAsyncTransactionStatus(TransactionStatus<T> status) {
            this.status = status;
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
        public T getConnection() {
            return status.getConnection();
        }
    }
}
