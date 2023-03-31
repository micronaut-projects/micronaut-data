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
package io.micronaut.data.r2dbc.transaction;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.transaction.async.AsyncUsingReactiveTransactionOperations;
import io.micronaut.transaction.interceptor.CoroutineTxHelper;
import io.micronaut.transaction.interceptor.ReactorCoroutineTxHelper;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.micronaut.transaction.sync.SynchronousFromReactiveTransactionManager;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.inject.Named;

import java.util.concurrent.ExecutorService;

/**
 * Build additional transaction managers to support using reactive transaction manager with async and synchronous methods.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Factory
final class R2dbcTransactionManagerFactory {

    @EachBean(ConnectionFactory.class)
    <T> SynchronousFromReactiveTransactionManager<T> buildSynchronousTransactionManager(@Parameter String dataSourceName,
                                                                                        @Parameter ReactorReactiveTransactionOperations<T> reactiveTransactionOperations,
                                                                                        @Named(TaskExecutors.IO) ExecutorService executorService) {
        return new SynchronousFromReactiveTransactionManager<>(reactiveTransactionOperations, executorService);
    }

    @EachBean(ConnectionFactory.class)
    <T> AsyncUsingReactiveTransactionOperations<T> buildAsyncTransactionOperations(@Parameter String dataSourceName,
                                                                                   @Parameter ReactorReactiveTransactionOperations<T> reactiveTransactionOperations,
                                                                                   @Nullable CoroutineTxHelper coroutineTxHelper,
                                                                                   @Nullable ReactorCoroutineTxHelper reactorCoroutineTxHelper) {
        return new AsyncUsingReactiveTransactionOperations<>(reactiveTransactionOperations, coroutineTxHelper, reactorCoroutineTxHelper);
    }

}
