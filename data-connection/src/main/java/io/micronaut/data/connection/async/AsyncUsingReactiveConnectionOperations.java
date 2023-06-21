/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.connection.async;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
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
 * Implementation of the asynchronous connection operations using a reactive connection operations.
 *
 * @param <C> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class AsyncUsingReactiveConnectionOperations<C> implements AsyncConnectionOperations<C> {

    private final ReactorConnectionOperations<C> reactorConnectionOperations;

    public AsyncUsingReactiveConnectionOperations(ReactorConnectionOperations<C> reactorConnectionOperations) {
        this.reactorConnectionOperations = reactorConnectionOperations;
    }

    @Override
    public Optional<ConnectionStatus<C>> findConnectionStatus() {
        return Optional.ofNullable(
            reactorConnectionOperations.getConnectionStatus(
                ReactorPropagation.addPropagatedContext(Context.empty(), PropagatedContext.getOrEmpty())
            )
        );
    }

    @Override
    public <T> CompletionStage<T> withConnection(ConnectionDefinition definition, Function<ConnectionStatus<C>, CompletionStage<T>> handler) {
        Mono<T> result = Mono.fromDirect(reactorConnectionOperations.withConnection(definition,
            status -> Mono.deferContextual(contextView -> Mono.fromCompletionStage(() -> {
                try (PropagatedContext.Scope ignore = ReactorPropagation.findPropagatedContext(contextView)
                    .orElseGet(PropagatedContext::getOrEmpty)
                    .propagate()) {
                    return handler.apply(status);
                }
            }))));
        return onCompleteCompleteFuture(result);
    }

    private static <T> CompletableFuture<T> onCompleteCompleteFuture(Publisher<T> publisher) {
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        publisher.subscribe(new CoreSubscriber<>() {

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
                try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                    completableFuture.completeExceptionally(t);
                }
            }

            @Override
            public void onComplete() {
                try (PropagatedContext.Scope ignore = propagatedContext.propagate()) {
                    completableFuture.complete(result);
                }
            }
        });
        return completableFuture;
    }

}
