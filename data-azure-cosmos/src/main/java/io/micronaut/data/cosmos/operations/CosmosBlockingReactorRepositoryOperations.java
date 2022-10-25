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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.cosmos.config.RequiresReactiveCosmos;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.BlockingReactorRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Cosmos blocking reactive repository operations wrapper being used as default reactive implementation of {@link CosmosRepositoryOperations}.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Singleton
@Requires(bean = DefaultReactiveCosmosRepositoryOperations.class)
@Internal
@RequiresReactiveCosmos
public class CosmosBlockingReactorRepositoryOperations implements
    CosmosRepositoryOperations,
    BlockingReactorRepositoryOperations,
    AsyncCapableRepository,
    MethodContextAwareStoredQueryDecorator,
    PreparedQueryDecorator {

    private final DefaultReactiveCosmosRepositoryOperations reactiveOperations;
    private ExecutorService executorService;
    private ExecutorAsyncOperations asyncOperations;

    /**
     * The default constructor.
     *
     * @param reactiveOperations default reactive operations
     */
    public CosmosBlockingReactorRepositoryOperations(DefaultReactiveCosmosRepositoryOperations reactiveOperations) {
        this.reactiveOperations = reactiveOperations;
    }

    @Override
    public ReactorReactiveRepositoryOperations reactive() {
        return reactiveOperations;
    }

    @Override
    public ConversionService<?> getConversionService() {
        return reactiveOperations.getConversionService();
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return reactiveOperations.getApplicationContext();
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        return reactiveOperations.decorate(context, storedQuery);
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return reactiveOperations.decorate(preparedQuery);
    }

    @NonNull
    @Override
    public ExecutorAsyncOperations async() {
        ExecutorAsyncOperations asyncOperations = this.asyncOperations;
        if (asyncOperations == null) {
            synchronized (this) { // double check
                asyncOperations = this.asyncOperations;
                if (asyncOperations == null) {
                    if (executorService == null) {
                        executorService = Executors.newCachedThreadPool();
                    }
                    asyncOperations = new ExecutorAsyncOperations(this, executorService);
                    this.asyncOperations = asyncOperations;
                }
            }
        }
        return asyncOperations;
    }
}
