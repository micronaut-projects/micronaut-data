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
package io.micronaut.data.mongodb.transaction;

import io.micronaut.configuration.mongo.core.MongoSettings;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.mongodb.conf.RequiresReactiveMongo;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.transaction.async.AsyncUsingReactiveTransactionOperations;
import io.micronaut.transaction.interceptor.CoroutineTxHelper;
import io.micronaut.transaction.interceptor.ReactorCoroutineTxHelper;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.micronaut.transaction.sync.SynchronousFromReactiveTransactionManager;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;

/**
 * Build additional transaction managers to support using reactive transaction manager with async and synchronous methods.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@RequiresReactiveMongo
@Factory
@Internal
final class MongoReactiveTransactionManagerFactory {

    @Requires(missingProperty = MongoSettings.MONGODB_SERVERS)
    @Primary
    @Singleton
    <T> SynchronousFromReactiveTransactionManager<T> buildPrimarySynchronousTransactionManager(@Primary ReactorReactiveTransactionOperations<T> reactiveTransactionOperations,
                                                                                               @Named(TaskExecutors.IO) ExecutorService executorService) {
        return new SynchronousFromReactiveTransactionManager<>(reactiveTransactionOperations, executorService);
    }

    @EachBean(NamedMongoConfiguration.class)
    <T> SynchronousFromReactiveTransactionManager<T> buildSynchronousTransactionManager(@Parameter ReactorReactiveTransactionOperations<T> reactiveTransactionOperations,
                                                                                        @Named(TaskExecutors.IO) ExecutorService executorService) {
        return new SynchronousFromReactiveTransactionManager<>(reactiveTransactionOperations, executorService);
    }

    @Requires(missingProperty = MongoSettings.MONGODB_SERVERS)
    @Primary
    @Singleton
    <T> AsyncUsingReactiveTransactionOperations<T> buildPrimaryAsyncTransactionOperations(@Primary ReactorReactiveTransactionOperations<T> reactiveTransactionOperations,
                                                                                          @Nullable CoroutineTxHelper coroutineTxHelper,
                                                                                          @Nullable ReactorCoroutineTxHelper reactiveHibernateHelper) {
        return new AsyncUsingReactiveTransactionOperations<>(reactiveTransactionOperations, coroutineTxHelper, reactiveHibernateHelper);
    }

    @EachBean(NamedMongoConfiguration.class)
    <T> AsyncUsingReactiveTransactionOperations<T> buildAsyncTransactionOperations(@Parameter ReactorReactiveTransactionOperations<T> reactiveTransactionOperations,
                                                                                   @Nullable CoroutineTxHelper coroutineTxHelper,
                                                                                   @Nullable ReactorCoroutineTxHelper reactiveHibernateHelper) {
        return new AsyncUsingReactiveTransactionOperations<>(reactiveTransactionOperations, coroutineTxHelper, reactiveHibernateHelper);
    }

}
