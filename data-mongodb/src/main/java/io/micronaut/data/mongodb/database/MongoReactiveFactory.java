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
package io.micronaut.data.mongodb.database;

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.mongodb.conf.RequiresReactiveMongo;
import io.micronaut.data.mongodb.operations.DefaultReactiveMongoRepositoryOperations;
import io.micronaut.data.mongodb.operations.MongoRepositoryOperations;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.BlockingReactorRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.query.PreparedQueryResolver;
import io.micronaut.data.runtime.query.StoredQueryResolver;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MongoDB reactive beans factory.
 */
@RequiresReactiveMongo
@Internal
@Factory
final class MongoReactiveFactory {

    @Primary
    @Singleton
    ReactiveMongoDatabaseFactory primaryMongoDatabaseFactory(DefaultMongoConfiguration mongoConfiguration, @Primary MongoClient mongoClient) {
        return mongoConfiguration.getConnectionString()
                .map(ConnectionString::getDatabase)
                .filter(StringUtils::isNotEmpty)
                .<ReactiveMongoDatabaseFactory>map(databaseName -> new SimpleReactiveMongoDatabaseFactory(mongoClient, databaseName))
                .orElseGet(UnknownReactiveMongoDatabaseFactory::new);
    }

    @EachBean(NamedMongoConfiguration.class)
    @Singleton
    ReactiveMongoDatabaseFactory namedMongoDatabaseFactory(NamedMongoConfiguration mongoConfiguration, @Parameter MongoClient mongoClient) {
        return mongoConfiguration.getConnectionString()
                .map(ConnectionString::getDatabase)
                .filter(StringUtils::isNotEmpty)
                .<ReactiveMongoDatabaseFactory>map(databaseName -> new SimpleReactiveMongoDatabaseFactory(mongoClient, databaseName))
                .orElseGet(UnknownReactiveMongoDatabaseFactory::new);
    }

    @EachBean(DefaultReactiveMongoRepositoryOperations.class)
    @Singleton
    MongoRepositoryOperations syncOperations(DefaultReactiveMongoRepositoryOperations reactiveOperations) {
        return new MongoReactiveBlockingRepositoryOperations(reactiveOperations);
    }

    private static final class MongoReactiveBlockingRepositoryOperations implements
            MongoRepositoryOperations,
            BlockingReactorRepositoryOperations,
            AsyncCapableRepository,
            HintsCapableRepository,
            StoredQueryResolver,
            PreparedQueryResolver {

        private final DefaultReactiveMongoRepositoryOperations reactiveOperations;
        private ExecutorService executorService;
        private ExecutorAsyncOperations asyncOperations;

        private MongoReactiveBlockingRepositoryOperations(DefaultReactiveMongoRepositoryOperations reactiveOperations) {
            this.reactiveOperations = reactiveOperations;
        }

        @Override
        public ConversionService<?> getConversionService() {
            return reactiveOperations.getConversionService();
        }

        @Override
        public Map<String, Object> getQueryHints(StoredQuery<?, ?> storedQuery) {
            return reactiveOperations.getQueryHints(storedQuery);
        }

        @Override
        public ReactorReactiveRepositoryOperations reactive() {
            return reactiveOperations;
        }

        @Override
        public ApplicationContext getApplicationContext() {
            return reactiveOperations.getApplicationContext();
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

        @Override
        public <E, R> PreparedQuery<E, R> resolveQuery(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery, Pageable pageable) {
            return reactiveOperations.resolveQuery(context, storedQuery, pageable);
        }

        @Override
        public <E, R> PreparedQuery<E, R> resolveCountQuery(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery, Pageable pageable) {
            return reactiveOperations.resolveCountQuery(context, storedQuery, pageable);
        }

        @Override
        public <E, R> StoredQuery<E, R> resolveQuery(MethodInvocationContext<?, ?> context, Class<E> entityClass, Class<R> resultType) {
            return reactiveOperations.resolveQuery(context, entityClass, resultType);
        }

        @Override
        public <E, R> StoredQuery<E, R> resolveCountQuery(MethodInvocationContext<?, ?> context, Class<E> entityClass, Class<R> resultType) {
            return reactiveOperations.resolveCountQuery(context, entityClass, resultType);
        }

        @Override
        public <E, QR> StoredQuery<E, QR> createStoredQuery(ExecutableMethod<?, ?> executableMethod,
                                                            DataMethod.OperationType operationType,
                                                            String name, AnnotationMetadata annotationMetadata,
                                                            Class<Object> rootEntity, String query, String update,
                                                            String[] queryParts, List<QueryParameterBinding> queryParameters,
                                                            boolean hasPageable, boolean isSingleResult) {
            return reactiveOperations.createStoredQuery(executableMethod, operationType, name, annotationMetadata, rootEntity, query, update, queryParts, queryParameters, hasPageable, isSingleResult);
        }

        @Override
        public StoredQuery<Object, Long> createCountStoredQuery(ExecutableMethod<?, ?> executableMethod,
                                                                DataMethod.OperationType operationType,
                                                                String name, AnnotationMetadata annotationMetadata,
                                                                Class<Object> rootEntity, String query, String[] queryParts,
                                                                List<QueryParameterBinding> queryParameters) {
            return reactiveOperations.createCountStoredQuery(executableMethod, operationType, name, annotationMetadata, rootEntity, query, queryParts, queryParameters);
        }
    }

}
