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
package io.micronaut.data.r2dbc.connection;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.connection.async.AsyncUsingReactiveConnectionOperations;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.connection.sync.SynchronousConnectionOperationsFromReactiveConnectionOperations;
import io.micronaut.scheduling.TaskExecutors;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.inject.Named;

import java.util.concurrent.ExecutorService;

/**
 * Build additional connection managers to support using reactive connection manager with async and synchronous methods.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Factory
final class R2dbcConnectionManagerFactory {

    @EachBean(ConnectionFactory.class)
    <T> SynchronousConnectionOperationsFromReactiveConnectionOperations<T> buildSynchronousTransactionManager(@Parameter ReactorConnectionOperations<T> reactorConnectionOperations,
                                                                                                              @Named(TaskExecutors.IO) ExecutorService executorService) {
        return new SynchronousConnectionOperationsFromReactiveConnectionOperations<>(reactorConnectionOperations, executorService);
    }

    @EachBean(ConnectionFactory.class)
    <T> AsyncUsingReactiveConnectionOperations<T> buildAsyncTransactionOperations(@Parameter ReactorConnectionOperations<T> reactorConnectionOperations) {
        return new AsyncUsingReactiveConnectionOperations<>(reactorConnectionOperations);
    }

}
