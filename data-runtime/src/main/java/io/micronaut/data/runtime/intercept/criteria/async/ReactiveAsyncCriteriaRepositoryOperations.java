/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.runtime.intercept.criteria.async;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.data.operations.async.AsyncCriteriaRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCriteriaRepositoryOperations;
import io.micronaut.data.runtime.operations.AsyncPageIdCriteriaRepositoryOperations;
import io.micronaut.data.runtime.operations.ReactivePageIdCriteriaRepositoryOperations;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * An async criteria operations adapter that delegates to reactive criteria operations.
 *
 * @author Denis Stepanov
 * @since 5.0.0
 */
@Internal
final class ReactiveAsyncCriteriaRepositoryOperations implements AsyncCriteriaRepositoryOperations, AsyncPageIdCriteriaRepositoryOperations {

    private final ReactiveCriteriaRepositoryOperations reactiveOperations;

    ReactiveAsyncCriteriaRepositoryOperations(ReactiveCriteriaRepositoryOperations reactiveOperations) {
        this.reactiveOperations = reactiveOperations;
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return reactiveOperations.getCriteriaBuilder();
    }

    @Override
    public CompletionStage<Boolean> exists(CriteriaQuery<?> query) {
        return toCompletionStage(reactiveOperations.exists(query));
    }

    @Override
    public <R> CompletionStage<R> findOne(CriteriaQuery<R> query) {
        return toCompletionStage(reactiveOperations.findOne(query));
    }

    @Override
    public <T> CompletionStage<List<T>> findAll(CriteriaQuery<T> query) {
        return toListCompletionStage(reactiveOperations.findAll(query));
    }

    @Override
    public <T> CompletionStage<List<T>> findAll(CriteriaQuery<T> query, int offset, int limit) {
        return toListCompletionStage(reactiveOperations.findAll(query, offset, limit));
    }

    @Override
    public <T> CompletionStage<List<T>> findPageIds(CriteriaQuery<T> query, int offset, int limit) {
        if (reactiveOperations instanceof ReactivePageIdCriteriaRepositoryOperations pageIdOperations) {
            return toListCompletionStage(pageIdOperations.findPageIds(query, offset, limit));
        }
        return findAll(query, offset, limit);
    }

    @Override
    public CompletionStage<Number> updateAll(CriteriaUpdate<Number> query) {
        return toCompletionStage(reactiveOperations.updateAll(query));
    }

    @Override
    public CompletionStage<Number> deleteAll(CriteriaDelete<Number> query) {
        return toCompletionStage(reactiveOperations.deleteAll(query));
    }

    private static <T> CompletionStage<List<T>> toListCompletionStage(Publisher<T> publisher) {
        return Flux.from(publisher)
            .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, PropagatedContext.getOrEmpty()))
            .collectList()
            .toFuture();
    }

    private static <T> CompletionStage<T> toCompletionStage(Publisher<T> publisher) {
        return Mono.from(publisher)
            .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, PropagatedContext.getOrEmpty()))
            .toFuture();
    }

}
