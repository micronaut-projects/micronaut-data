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
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;
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

    public AsyncUsingReactiveTransactionOperations(ReactorReactiveTransactionOperations<C> reactiveTransactionOperations) {
        this.reactiveTransactionOperations = reactiveTransactionOperations;
    }

    @Override
    public boolean managesTransaction(AsyncTransactionStatus<C> transactionStatus) {
        if (transactionStatus instanceof DefaultAsyncTransactionStatus<C> status) {
            return status.asyncTransactionOperations == this;
        }
        return false;
    }

    @Override
    public Optional<? extends AsyncTransactionStatus<?>> findTransactionStatus() {
        return Optional.ofNullable(
            reactiveTransactionOperations.getTransactionStatus(
                ReactorPropagation.addPropagatedContext(Context.empty(), PropagatedContext.getOrEmpty())
            )
        ).map(status -> new DefaultAsyncTransactionStatus<>(status, this));
    }

    @Override
    public <T extends @Nullable Object> CompletionStage<T> withTransaction(TransactionDefinition definition,
                                                                          Function<AsyncTransactionStatus<C>, CompletionStage<T>> handler) {
        Mono<T> result = Mono.fromDirect(reactiveTransactionOperations.withTransaction(definition,
            status -> Mono.deferContextual(contextView -> Mono.fromCompletionStage(() -> {
                DefaultAsyncTransactionStatus<C> asyncStatus = new DefaultAsyncTransactionStatus<>(status, this);
                PropagatedContext propagatedContext = ReactorPropagation.findPropagatedContext(contextView).orElseGet(PropagatedContext::getOrEmpty);
                return asyncStatus.propagate(propagatedContext, () -> handler.apply(asyncStatus));
            }))));
        return onCompleteCompleteFuture(result);
    }

    private static <T extends @Nullable Object> CompletableFuture<T> onCompleteCompleteFuture(Publisher<T> publisher) {
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        publisher.subscribe(new CoreSubscriber<>() {

            @Nullable
            private T result;

            @NonNull
            @Override
            public Context currentContext() {
                return ReactorPropagation.addPropagatedContext(Context.empty(), propagatedContext);
            }

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
                propagatedContext.propagate(() -> completableFuture.completeExceptionally(t));
            }

            @Override
            public void onComplete() {
                propagatedContext.propagate(() -> completableFuture.complete(result));
            }
        });
        return completableFuture;
    }

    private record DefaultAsyncTransactionStatus<T>(
        ReactiveTransactionStatus<T> status,
        AsyncTransactionOperations<T> asyncTransactionOperations) implements AsyncTransactionStatus<T> {

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
