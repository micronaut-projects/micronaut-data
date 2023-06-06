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
package io.micronaut.data.connection.sync;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Implementation of the synchronous connection operations using a reactive connection operations.
 *
 * @param <T> The connection type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class SynchronousConnectionOperationsFromReactiveConnectionOperations<T> implements ConnectionOperations<T> {

    private final ReactorConnectionOperations<T> reactorConnectionOperations;
    private final Scheduler scheduler;

    public SynchronousConnectionOperationsFromReactiveConnectionOperations(ReactorConnectionOperations<T> reactorConnectionOperations,
                                                                           ExecutorService blockingExecutorService) {
        this.reactorConnectionOperations = reactorConnectionOperations;
        this.scheduler = Schedulers.fromExecutorService(blockingExecutorService);
    }

    @Override
    public Optional<ConnectionStatus<T>> findConnectionStatus() {
        return Optional.empty(); // Not supported
    }

    @Override
    public <R> R execute(ConnectionDefinition definition, Function<ConnectionStatus<T>, R> callback) {
        Mono<R> result = reactorConnectionOperations.withConnectionMono(definition, status -> Mono.deferContextual(contextView -> {
            try (PropagatedContext.Scope ignore = ReactorPropagation.findPropagatedContext(contextView).orElseGet(PropagatedContext::getOrEmpty).propagate()) {
                return Mono.justOrEmpty(callback.apply(status));
            }
        }).subscribeOn(scheduler)).contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, PropagatedContext.getOrEmpty()));
        return result.onErrorMap(e -> {
            if (e instanceof UndeclaredThrowableException) {
                return e.getCause();
            }
            return e;
        }).block();
    }
}
