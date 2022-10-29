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
package io.micronaut.data.cosmos.operations;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The sync Azure Cosmos DB operations implementation.
 */
@Singleton
@Internal
final class SyncCosmosRepositoryOperations implements
    CosmosRepositoryOperations,
    AsyncCapableRepository,
    ReactiveCapableRepository,
    MethodContextAwareStoredQueryDecorator,
    PreparedQueryDecorator {

    private final DefaultReactiveCosmosRepositoryOperations reactiveCosmosRepositoryOperations;
    private ExecutorService executorService;
    private ExecutorAsyncOperations asyncOperations;

    /**
     * Default constructor.
     *
     * @param reactiveCosmosRepositoryOperations    The reactive cosmos repository operations
     * @param executorService                       The executor service
     */
    protected SyncCosmosRepositoryOperations(DefaultReactiveCosmosRepositoryOperations reactiveCosmosRepositoryOperations,
                                             @Named("io") @Nullable ExecutorService executorService) {
        this.reactiveCosmosRepositoryOperations = reactiveCosmosRepositoryOperations;
        this.executorService = executorService;
    }

    @Override
    public <T> T findOne(@NonNull Class<T> type, Serializable id) {
        return reactiveCosmosRepositoryOperations.findOne(type, id).block();
    }

    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return reactiveCosmosRepositoryOperations.findOne(preparedQuery).block();
    }

    @Override
    public <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return reactiveCosmosRepositoryOperations.exists(preparedQuery).block();
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(@NonNull PagedQuery<T> query) {
        throw new UnsupportedOperationException("The findAll method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        throw new UnsupportedOperationException("The count method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return reactiveCosmosRepositoryOperations.findAll(preparedQuery).toIterable();
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return StreamSupport.stream(findAll(preparedQuery).spliterator(), true);
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(@NonNull PagedQuery<T> query) {
        throw new UnsupportedOperationException("The findStream method without an explicit query is not supported. Use findStream(PreparedQuery) instead");
    }

    @Override
    public <R> Page<R> findPage(@NonNull PagedQuery<R> query) {
        throw new UnsupportedOperationException("The findPage method without an explicit query is not supported. Use findPage(PreparedQuery) instead");
    }

    @NonNull
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        return reactiveCosmosRepositoryOperations.persist(operation).block();
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        return reactiveCosmosRepositoryOperations.update(operation).block();

    }

    @NonNull
    @Override
    public <T> Iterable<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        return reactiveCosmosRepositoryOperations.updateAll(operation).toIterable();
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return Optional.of(reactiveCosmosRepositoryOperations.executeUpdate(preparedQuery).block());
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        return reactiveCosmosRepositoryOperations.delete(operation).block().intValue();
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return Optional.of(reactiveCosmosRepositoryOperations.deleteAll(operation).block());
    }

    @NonNull
    @Override
    public Optional<Number> executeDelete(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return Optional.of(reactiveCosmosRepositoryOperations.executeDelete(preparedQuery).block());
    }

    @NonNull
    @Override
    public ExecutorAsyncOperations async() {
        ExecutorAsyncOperations asyncOperations = this.asyncOperations;
        if (asyncOperations == null) {
            synchronized (this) { // double check
                asyncOperations = this.asyncOperations;
                if (asyncOperations == null) {
                    asyncOperations = new ExecutorAsyncOperations(
                        this,
                        executorService != null ? executorService : newLocalThreadPool()
                    );
                    this.asyncOperations = asyncOperations;
                }
            }
        }
        return asyncOperations;
    }

    @NonNull
    @Override
    public ReactiveRepositoryOperations reactive() {
        return reactiveCosmosRepositoryOperations;
    }

    @NonNull
    private ExecutorService newLocalThreadPool() {
        this.executorService = Executors.newCachedThreadPool();
        return executorService;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return reactiveCosmosRepositoryOperations.getApplicationContext();
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        return reactiveCosmosRepositoryOperations.decorate(context, storedQuery);
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return reactiveCosmosRepositoryOperations.decorate(preparedQuery);
    }
}
