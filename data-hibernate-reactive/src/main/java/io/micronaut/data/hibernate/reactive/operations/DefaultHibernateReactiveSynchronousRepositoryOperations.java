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
package io.micronaut.data.hibernate.reactive.operations;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.reactive.BlockingExecutorReactorRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.operations.AsyncFromReactiveAsyncRepositoryOperation;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.StoredQueryDecorator;
import jakarta.annotation.PreDestroy;
import org.hibernate.SessionFactory;
import reactor.core.publisher.Mono;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.io.Closeable;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Hibernate reactive implementation of {@link JpaRepositoryOperations}.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class DefaultHibernateReactiveSynchronousRepositoryOperations implements BlockingExecutorReactorRepositoryOperations,
        JpaRepositoryOperations, AsyncCapableRepository, HintsCapableRepository, Closeable, PreparedQueryDecorator, StoredQueryDecorator {

    private final ApplicationContext applicationContext;
    private final DefaultHibernateReactiveRepositoryOperations reactiveRepositoryOperations;
    private AsyncRepositoryOperations asyncRepositoryOperations;
    private ExecutorService executorService;

    public DefaultHibernateReactiveSynchronousRepositoryOperations(ApplicationContext applicationContext,
                                                                   SessionFactory sessionFactory,
                                                                   @Parameter String name,
                                                                   RuntimeEntityRegistry runtimeEntityRegistry,
                                                                   DataConversionService dataConversionService) {
        this.applicationContext = applicationContext;
        this.reactiveRepositoryOperations = new DefaultHibernateReactiveRepositoryOperations(sessionFactory, runtimeEntityRegistry, dataConversionService, name);
    }

    @Override
    public <T> T block(Function<ReactorReactiveRepositoryOperations, Mono<T>> supplier) {
        throw blockingNotSupported();
    }

    @Override
    public <T> Optional<T> blockOptional(Function<ReactorReactiveRepositoryOperations, Mono<T>> supplier) {
        throw blockingNotSupported();
    }

    @Override
    public EntityManager getCurrentEntityManager() {
        return notSupported();
    }

    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        return notSupported();
    }

    @Override
    public <T> T load(Class<T> type, Serializable id) {
        return notSupported();
    }

    @Override
    public void flush() {
        notSupported();
    }

    @Override
    public AsyncRepositoryOperations async() {
        if (asyncRepositoryOperations == null) {
            if (executorService == null) {
                executorService = Executors.newCachedThreadPool();
            }
            asyncRepositoryOperations = new AsyncFromReactiveAsyncRepositoryOperation(reactiveRepositoryOperations, executorService);
        }
        return asyncRepositoryOperations;
    }

    @Override
    public DefaultHibernateReactiveRepositoryOperations reactive() {
        return reactiveRepositoryOperations;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public Map<String, Object> getQueryHints(StoredQuery<?, ?> storedQuery) {
        return reactive().getQueryHints(storedQuery);
    }

    @PreDestroy
    @Override
    public void close() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private IllegalStateException blockingNotSupported() {
        return new IllegalStateException("Blocking isn't supported for Hibernate Reactive");
    }

    private <T> T notSupported() {
        throw new IllegalStateException("Method isn't supported for Hibernate Reactive");
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return reactiveRepositoryOperations.decorate(preparedQuery);
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(StoredQuery<E, R> storedQuery) {
        return reactiveRepositoryOperations.decorate(storedQuery);
    }

    @Override
    public ConversionService getConversionService() {
        return reactiveRepositoryOperations.getConversionService();
    }
}
