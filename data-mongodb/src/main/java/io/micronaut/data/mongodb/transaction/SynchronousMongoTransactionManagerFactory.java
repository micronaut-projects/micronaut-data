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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.mongodb.conf.RequiresSyncMongo;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.async.AsyncUsingSyncTransactionOperations;
import io.micronaut.transaction.interceptor.CoroutineTxHelper;
import jakarta.inject.Singleton;

/**
 * Build additional transaction managers to support using synchronous transaction manager with async methods.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@RequiresSyncMongo
@Factory
final class SynchronousMongoTransactionManagerFactory {

    @Requires(missingProperty = MongoSettings.MONGODB_SERVERS)
    @Primary
    @Singleton
    <T> AsyncUsingSyncTransactionOperations<T> buildPrimaryAsyncTransactionOperations(SynchronousTransactionManager<T> synchronousTransactionManager,
                                                                                      @Nullable CoroutineTxHelper coroutineTxHelper) {
        return new AsyncUsingSyncTransactionOperations<>(synchronousTransactionManager, coroutineTxHelper);
    }

    @EachBean(NamedMongoConfiguration.class)
    <T> AsyncUsingSyncTransactionOperations<T> buildAsyncTransactionOperations(@Parameter SynchronousTransactionManager<T> synchronousTransactionManager,
                                                                               @Nullable CoroutineTxHelper coroutineTxHelper) {
        return new AsyncUsingSyncTransactionOperations<>(synchronousTransactionManager, coroutineTxHelper);
    }

}
