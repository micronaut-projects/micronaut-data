/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.operations;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.io.Serializable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * An implementation of {@link AsyncRepositoryOperations} that delegates to a reactive operation.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Experimental
public final class AsyncFromReactorReactiveAsyncRepositoryOperation implements AsyncRepositoryOperations {

    private final ReactorReactiveRepositoryOperations reactiveOperations;
    private final Executor executor;

    public AsyncFromReactorReactiveAsyncRepositoryOperation(ReactorReactiveRepositoryOperations reactiveOperations,
                                                            Executor executor) {
        this.reactiveOperations = reactiveOperations;
        this.executor = executor;
    }

    @NonNull
    @Override
    public Executor getExecutor() {
        return executor;
    }

    @NonNull
    @Override
    public <T> CompletionStage<T> findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return toCompletionStage(reactiveOperations.findOne(type, id));
    }

    @Override
    public <T> CompletionStage<Boolean> exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return toCompletionStage(reactiveOperations.exists(preparedQuery));
    }

    @NonNull
    @Override
    public <T, R> CompletionStage<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return toCompletionStage(reactiveOperations.findOne(preparedQuery));
    }

    @NonNull
    @Override
    public <T> CompletionStage<T> findOptional(@NonNull Class<T> type, @NonNull Serializable id) {
        return toCompletionStage(reactiveOperations.findOptional(type, id));
    }

    @NonNull
    @Override
    public <T, R> CompletionStage<R> findOptional(@NonNull PreparedQuery<T, R> preparedQuery) {
        return toCompletionStage(reactiveOperations.findOptional(preparedQuery));
    }

    @NonNull
    @Override
    public <T> CompletionStage<Iterable<T>> findAll(PagedQuery<T> pagedQuery) {
        return toCompletionStage(reactiveOperations.findAll(pagedQuery));
    }

    @NonNull
    @Override
    public <T> CompletionStage<Long> count(PagedQuery<T> pagedQuery) {
        return toCompletionStage(reactiveOperations.count(pagedQuery));
    }

    @NonNull
    @Override
    public <T, R> CompletionStage<Iterable<R>> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return toCompletionStage(reactiveOperations.findAll(preparedQuery));
    }

    @NonNull
    @Override
    public <T> CompletionStage<T> persist(@NonNull InsertOperation<T> operation) {
        return toCompletionStage(reactiveOperations.persist(operation));
    }

    @NonNull
    @Override
    public <T> CompletionStage<T> update(@NonNull UpdateOperation<T> operation) {
        return toCompletionStage(reactiveOperations.update(operation));
    }

    @NonNull
    @Override
    public <T> CompletionStage<Number> delete(@NonNull DeleteOperation<T> operation) {
        return toCompletionStage(reactiveOperations.delete(operation));
    }

    @NonNull
    @Override
    public <T> CompletionStage<Iterable<T>> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return toCompletionStage(reactiveOperations.persistAll(operation));
    }

    @NonNull
    @Override
    public CompletionStage<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return toCompletionStage(reactiveOperations.executeUpdate(preparedQuery));
    }

    @Override
    public CompletionStage<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return toCompletionStage(reactiveOperations.executeDelete(preparedQuery));
    }

    @NonNull
    @Override
    public <T> CompletionStage<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        return toCompletionStage(reactiveOperations.deleteAll(operation));
    }

    @NonNull
    @Override
    public <R> CompletionStage<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery) {
        return toCompletionStage(reactiveOperations.findPage(pagedQuery));
    }

    @NonNull
    @Override
    public <T> CompletionStage<Iterable<T>> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        return toCompletionStage(reactiveOperations.updateAll(operation));
    }

    private <T> CompletionStage<Iterable<T>> toCompletionStage(Flux<T> flux) {
        ContextView contextView = (ContextView) TransactionSynchronizationManager.getResource(ContextView.class);
        if (contextView != null) {
            flux = flux.contextWrite(contextView);
        }
        return flux.collectList().<Iterable<T>>map(list -> list).toFuture();

    }

    private <T> CompletionStage<T> toCompletionStage(Mono<T> mono) {
        ContextView contextView = (ContextView) TransactionSynchronizationManager.getResource(ContextView.class);
        if (contextView != null) {
            mono = mono.contextWrite(contextView);
        }
        return mono.toFuture();
    }

}
