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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.BlockingReactorRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The sync Azure Cosmos DB operations implementation.
 *
 * @author radovanradic
 * @since 3.9.0
 */
@Singleton
@Internal
final class SyncCosmosRepositoryOperations implements
    CosmosRepositoryOperations,
    BlockingReactorRepositoryOperations,
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

    @NonNull
    @Override
    public ExecutorAsyncOperations async() {
        ExecutorAsyncOperations executorAsyncOperations = this.asyncOperations;
        if (executorAsyncOperations == null) {
            synchronized (this) { // double check
                executorAsyncOperations = this.asyncOperations;
                if (executorAsyncOperations == null) {
                    executorAsyncOperations = new ExecutorAsyncOperations(
                        this,
                        executorService != null ? executorService : newLocalThreadPool()
                    );
                    this.asyncOperations = executorAsyncOperations;
                }
            }
        }
        return asyncOperations;
    }

    @NonNull
    @Override
    public ReactorReactiveRepositoryOperations reactive() {
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

    @Override
    public ConversionService getConversionService() {
        return reactiveCosmosRepositoryOperations.getConversionService();
    }
}
