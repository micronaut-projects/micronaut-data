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

import io.micronaut.aop.kotlin.KotlinInterceptedMethod;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.interceptor.CoroutineTxHelper;
import io.micronaut.transaction.interceptor.KotlinInterceptedMethodAsyncResultSupplier;
import io.micronaut.transaction.interceptor.ReactorCoroutineTxHelper;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import io.micronaut.transaction.support.TransactionSynchronizationManager.TransactionSynchronizationStateOp;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Implementation of the asynchronous transaction manager using a reactive transaction manager.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public final class AsyncUsingReactiveTransactionOperations<C> implements AsyncTransactionOperations<C> {

    private final ReactorReactiveTransactionOperations<C> reactiveTransactionOperations;
    @Nullable
    private final CoroutineTxHelper coroutineTxHelper;
    @Nullable
    private final ReactorCoroutineTxHelper reactorCoroutineTxHelper;

    public AsyncUsingReactiveTransactionOperations(ReactorReactiveTransactionOperations<C> reactiveTransactionOperations,
                                                   @Nullable CoroutineTxHelper coroutineTxHelper,
                                                   @Nullable ReactorCoroutineTxHelper reactorCoroutineTxHelper) {
        this.reactiveTransactionOperations = reactiveTransactionOperations;
        this.coroutineTxHelper = coroutineTxHelper;
        this.reactorCoroutineTxHelper = reactorCoroutineTxHelper;
    }

    @Override
    public <T> CompletionStage<T> withTransaction(TransactionDefinition definition,
                                                  Function<AsyncTransactionStatus<C>, CompletionStage<T>> handler) {
        try (TransactionSynchronizationStateOp op = TransactionSynchronizationManager.withGuardedState()) {
            TransactionSynchronizationManager.TransactionSynchronizationState state = op.getOrCreateState();
            ContextView previousContext = null;
            if (coroutineTxHelper != null && handler instanceof KotlinInterceptedMethodAsyncResultSupplier) {
                KotlinInterceptedMethod kotlinInterceptedMethod = ((KotlinInterceptedMethodAsyncResultSupplier) handler).getKotlinInterceptedMethod();
                Objects.requireNonNull(coroutineTxHelper).setupTxState(kotlinInterceptedMethod, state);
                if (reactorCoroutineTxHelper != null) {
                    previousContext = reactorCoroutineTxHelper.getReactorContext(kotlinInterceptedMethod);
                }
            }
            // Reactive transaction manager applied on Kotlin coroutine
            // Use reactive transaction manager to open a transaction and send the Reactor context in the coroutine context
            if (previousContext == null) {
                previousContext = (ContextView) TransactionSynchronizationManager.getResource(ContextView.class);
            }
            ContextView finalPreviousContext = previousContext;
            Mono<T> result = Mono.fromDirect(reactiveTransactionOperations.withTransaction(definition, status -> {
                return Mono.deferContextual(contextView -> {
                        try (TransactionSynchronizationStateOp ignore = TransactionSynchronizationManager.withState(state)) {
                            TransactionSynchronizationManager.rebindResource(ContextView.class, contextView);
                        }
                        return Mono.fromCompletionStage(() ->
                            TransactionSynchronizationManager.decorateCompletionStage(state,
                                () -> handler.apply(new DefaultAsyncTransactionStatus<>(status))));
                    })
                    .doAfterTerminate(() -> {
                        try (TransactionSynchronizationStateOp ignore = TransactionSynchronizationManager.withState(state)) {
                            TransactionSynchronizationManager.unbindResourceIfPossible(ContextView.class);
                        }
                        if (finalPreviousContext != null) {
                            TransactionSynchronizationManager.bindResource(ContextView.class, finalPreviousContext);
                        }
                    });
            }));
            if (previousContext != null) {
                result = result.contextWrite(previousContext);
            }
            return onCompleteCompleteFuture(result);
        }
    }

    private static <T> CompletableFuture<T> onCompleteCompleteFuture(Publisher<T> publisher) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        publisher.subscribe(new CoreSubscriber<T>() {

            private T result;

            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(T t) {
                result = t;
            }

            @Override
            public void onError(Throwable t) {
                completableFuture.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                completableFuture.complete(result);
            }
        });
        return completableFuture;
    }

    private static final class DefaultAsyncTransactionStatus<T> implements AsyncTransactionStatus<T> {

        private final ReactiveTransactionStatus<T> status;

        private DefaultAsyncTransactionStatus(ReactiveTransactionStatus<T> status) {
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
