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

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.hibernate.operations.HibernateJpaOperations;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.async.AsyncUsingReactiveTransactionOperations;
import io.micronaut.transaction.interceptor.CoroutineTxHelper;
import io.micronaut.transaction.interceptor.ReactorCoroutineTxHelper;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import jakarta.inject.Named;
import org.hibernate.SessionFactory;

import java.util.concurrent.ExecutorService;

/**
 * The factory builds appropriate repository operations and reactive transaction manager.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
@Factory
final class HibernateRepositoryOperationsFactory {

    @EachBean(SessionFactory.class)
    @Replaces(HibernateJpaOperations.class)
    JpaRepositoryOperations operations(SessionFactory sessionFactory,
                                       @Parameter @Nullable JpaConfiguration jpaConfiguration,
                                       @Named("io") @Nullable ExecutorService executorService,
                                       RuntimeEntityRegistry runtimeEntityRegistry,
                                       DataConversionService dataConversionService,
                                       BeanProvider<SynchronousTransactionManager> transactionOperations,
                                       Qualifier<SynchronousTransactionManager> qualifier,
                                       ApplicationContext applicationContext,
                                       @Parameter String name
    ) {
        if (jpaConfiguration == null || !jpaConfiguration.isReactive()) {
            return new HibernateJpaOperations(
                sessionFactory,
                transactionOperations.find(qualifier).orElseThrow(),
                executorService,
                runtimeEntityRegistry,
                dataConversionService
            );
        }
        return new DefaultHibernateReactiveSynchronousRepositoryOperations(
            applicationContext,
            sessionFactory,
            name,
            runtimeEntityRegistry,
            dataConversionService
        );
    }

    @EachBean(SessionFactory.class)
    HibernateReactorRepositoryOperations reactiveRepositoryOperations(@Parameter JpaRepositoryOperations syncRepositoryOperations) {
        if (syncRepositoryOperations instanceof DefaultHibernateReactiveSynchronousRepositoryOperations) {
            return ((DefaultHibernateReactiveSynchronousRepositoryOperations) syncRepositoryOperations).reactive();
        }
        throw new IllegalStateException("Not Hibernate Reactive session factory!");
    }

    @EachBean(SessionFactory.class)
    <T> AsyncUsingReactiveTransactionOperations<T> buildAsyncTransactionOperations(@Parameter ReactorReactiveTransactionOperations<T> operations,
                                                                                   @Nullable CoroutineTxHelper coroutineTxHelper,
                                                                                   @Nullable ReactorCoroutineTxHelper reactiveHibernateHelper) {
        return new AsyncUsingReactiveTransactionOperations<>(operations, coroutineTxHelper, reactiveHibernateHelper);
    }

}
