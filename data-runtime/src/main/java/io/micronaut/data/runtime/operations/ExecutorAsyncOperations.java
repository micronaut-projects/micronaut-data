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
package io.micronaut.data.runtime.operations;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.model.Page;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * An implementation of {@link AsyncRepositoryOperations} that delegates to a blocking operations and specified {@link Executor}.
 * This can be used in absence of true asynchronous support at the driver level.
 *
 * <p>If a backing implementation provides a async API then the backing implementation should not use this class and instead directly implement the {@link AsyncRepositoryOperations} interface.</p>
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ExecutorAsyncOperations implements AsyncRepositoryOperations {

    private final RepositoryOperations datastore;
    private final Executor executor;

    /**
     * Default constructor.
     *
     * @param operations The target operations
     * @param executor   The executor to use.
     */
    public ExecutorAsyncOperations(@NonNull RepositoryOperations operations, @NonNull Executor executor) {
        ArgumentUtils.requireNonNull("operations", operations);
        ArgumentUtils.requireNonNull("executor", executor);
        this.datastore = operations;
        this.executor = executor;
    }

    @Override
    public Executor getExecutor() {
        return executor;
    }

    @NonNull
    @Override
    public <T> CompletableFuture<T> findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return CompletableFuture.supplyAsync(() -> {
                    T r = datastore.findOne(type, id);
                    if (r != null) {
                        return r;
                    } else {
                        throw new EmptyResultException();
                    }
                }, executor
        );
    }

    @Override
    public <T> CompletableFuture<Boolean> exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return CompletableFuture.supplyAsync(() -> datastore.exists(preparedQuery), executor);
    }

    @NonNull
    @Override
    public <T, R> CompletableFuture<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return CompletableFuture.supplyAsync(() -> {
                    R r = datastore.findOne(preparedQuery);
                    if (r != null) {
                        return r;
                    } else {
                        throw new EmptyResultException();
                    }
                }, executor
        );
    }

    @NonNull
    @Override
    public <T> CompletableFuture<T> findOptional(@NonNull Class<T> type, @NonNull Serializable id) {
        return CompletableFuture.supplyAsync(() -> {
                    T r = datastore.findOne(type, id);
                    if (r != null) {
                        return r;
                    } else {
                        throw new EmptyResultException();
                    }
                }, executor
        );
    }

    @NonNull
    @Override
    public <T, R> CompletableFuture<R> findOptional(@NonNull PreparedQuery<T, R> preparedQuery) {
        return CompletableFuture.supplyAsync(() ->
                datastore.findOne(preparedQuery), executor
        );
    }

    @NonNull
    @Override
    public <T> CompletableFuture<Iterable<T>> findAll(@NonNull PagedQuery<T> pagedQuery) {
        return CompletableFuture.supplyAsync(() ->
                datastore.findAll(pagedQuery), executor
        );
    }

    @Override
    public <T> CompletableFuture<Long> count(@NonNull PagedQuery<T> pagedQuery) {
        return CompletableFuture.supplyAsync(() ->
                datastore.count(pagedQuery), executor
        );
    }

    @NonNull
    @Override
    public <T, R> CompletableFuture<Iterable<R>> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return CompletableFuture.supplyAsync(() ->
                datastore.findAll(preparedQuery), executor
        );
    }

    @NonNull
    @Override
    public <T> CompletableFuture<T> persist(@NonNull InsertOperation<T> entity) {
        return CompletableFuture.supplyAsync(() ->
                datastore.persist(entity), executor
        );
    }

    @NonNull
    @Override
    public <T> CompletableFuture<T> update(@NonNull UpdateOperation<T> operation) {
        return CompletableFuture.supplyAsync(() ->
                datastore.update(operation), executor
        );
    }

    @NonNull
    @Override
    public <T> CompletableFuture<Iterable<T>> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        return CompletableFuture.supplyAsync(() ->
                datastore.updateAll(operation), executor
        );
    }

    @NonNull
    @Override
    public <T> CompletableFuture<Number> delete(@NonNull DeleteOperation<T> operation) {
        return CompletableFuture.supplyAsync(() ->
                datastore.delete(operation), executor
        );
    }

    @NonNull
    @Override
    public <T> CompletableFuture<Iterable<T>> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return CompletableFuture.supplyAsync(() -> datastore.persistAll(operation), executor);
    }

    @NonNull
    @Override
    public CompletableFuture<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return CompletableFuture.supplyAsync(() -> datastore.executeUpdate(preparedQuery).orElse(0), executor);
    }

    @Override
    public CompletionStage<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return CompletableFuture.supplyAsync(() -> datastore.executeDelete(preparedQuery).orElse(0), executor);
    }

    @NonNull
    @Override
    public <T> CompletableFuture<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        return CompletableFuture.supplyAsync(() ->
                datastore.deleteAll(operation).orElse(0),
                executor
        );
    }

    @Override
    public <R> CompletableFuture<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery) {
        return CompletableFuture.supplyAsync(() -> datastore.findPage(pagedQuery), executor);
    }
}
