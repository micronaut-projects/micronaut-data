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

import io.micronaut.aop.InvocationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.hibernate.conf.RequiresReactiveHibernate;
import io.micronaut.data.hibernate.operations.AbstractHibernateOperations;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.operations.reactive.ReactorCriteriaRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import org.hibernate.SessionFactory;
import org.hibernate.reactive.stage.Stage;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.function.Function;

/**
 * Hibernate reactive implementation of {@link io.micronaut.data.operations.reactive.ReactiveRepositoryOperations}
 * and {@link ReactorReactiveTransactionOperations}.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@RequiresReactiveHibernate
@EachBean(SessionFactory.class)
@Internal
final class DefaultHibernateReactiveRepositoryOperations extends AbstractHibernateOperations<Stage.Session, Stage.AbstractQuery, Stage.SelectionQuery<?>>
        implements HibernateReactorRepositoryOperations, ReactorCriteriaRepositoryOperations {

    private final SessionFactory sessionFactory;
    private final Stage.SessionFactory stageSessionFactory;
    private final ReactiveHibernateHelper helper;
    private final ReactorConnectionOperations<Stage.Session> connectionOperations;
    private final ReactorReactiveTransactionOperations<Stage.Session> transactionOperations;

    DefaultHibernateReactiveRepositoryOperations(SessionFactory sessionFactory,
                                                 RuntimeEntityRegistry runtimeEntityRegistry,
                                                 DataConversionService dataConversionService,
                                                 @Parameter ReactorConnectionOperations<Stage.Session> connectionOperations,
                                                 @Parameter ReactorReactiveTransactionOperations<Stage.Session> transactionOperations) {
        super(runtimeEntityRegistry, dataConversionService);
        this.sessionFactory = sessionFactory;
        this.stageSessionFactory = sessionFactory.unwrap(Stage.SessionFactory.class);
        this.connectionOperations = connectionOperations;
        this.transactionOperations = transactionOperations;
        this.helper = new ReactiveHibernateHelper(stageSessionFactory);
    }

    @Override
    protected void setParameter(Stage.AbstractQuery query, String parameterName, Object value) {
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameter(Stage.AbstractQuery query, String parameterName, Object value, Argument<?> argument) {
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameterList(Stage.AbstractQuery query, String parameterName, Collection<Object> value) {
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameterList(Stage.AbstractQuery query, String parameterName, Collection<Object> value, Argument<?> argument) {
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameter(Stage.AbstractQuery query, int parameterIndex, Object value) {
        query.setParameter(parameterIndex, value);
    }

    @Override
    protected void setParameter(Stage.AbstractQuery query, int parameterIndex, Object value, Argument<?> argument) {
        query.setParameter(parameterIndex, value);
    }

    @Override
    protected void setParameterList(Stage.AbstractQuery query, int parameterIndex, Collection<Object> value) {
        query.setParameter(parameterIndex, value);
    }

    @Override
    protected void setParameterList(Stage.AbstractQuery query, int parameterIndex, Collection<Object> value, Argument<?> argument) {
        query.setParameter(parameterIndex, value);
    }

    @Override
    protected void setHint(Stage.SelectionQuery<?> query, String hintName, Object value) {
        if (value instanceof EntityGraph plan) {
            query.setPlan(plan);
            return;
        }
        throw new IllegalStateException("Unrecognized parameter: " + hintName + " with value: " + value);
    }

    @Override
    protected void setMaxResults(Stage.SelectionQuery<?> query, int max) {
        query.setMaxResults(max);
    }

    @Override
    protected void setOffset(Stage.SelectionQuery<?> query, int offset) {
        query.setFirstResult(offset);
    }

    @Override
    protected <T> EntityGraph<T> getEntityGraph(Stage.Session session, Class<T> entityType, String graphName) {
        return session.getEntityGraph(entityType, graphName);
    }

    @Override
    protected <T> EntityGraph<T> createEntityGraph(Stage.Session session, Class<T> entityType) {
        return session.createEntityGraph(entityType);
    }

    @Override
    protected <T> RuntimePersistentEntity<T> getEntity(Class<T> type) {
        return runtimeEntityRegistry.getEntity(type);
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return stageSessionFactory.getCriteriaBuilder();
    }

    @Override
    public Mono<Void> flush() {
        return withSession(helper::flush);
    }

    @Override
    public Mono<Void> persistAndFlush(Object entity) {
        return operation(session -> helper.persist(session, entity).then(helper.flush(session)));
    }

    @Override
    public <T> Mono<T> findOne(Class<T> type, Object id) {
        return operation(session -> helper.find(session, type, id));
    }

    @Override
    public <T> Mono<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery) {
        return findOne(preparedQuery).hasElement();
    }

    @Override
    protected Stage.SelectionQuery<?> createNativeQuery(Stage.Session session, String query, Class<?> resultType) {
        if (resultType == null) {
            return session.createNativeQuery(query);
        }
        return session.createNativeQuery(query, resultType);
    }

    @Override
    protected Stage.SelectionQuery<?> createQuery(Stage.Session session, String query, Class<?> resultType) {
        if (resultType == null) {
            return session.createQuery(query);
        }
        return session.createQuery(query, resultType);
    }

    @Override
    protected Stage.SelectionQuery<?> createQuery(Stage.Session session, CriteriaQuery<?> criteriaQuery) {
        return session.createQuery(criteriaQuery);
    }

    @Override
    public <T, R> Mono<R> findOne(PreparedQuery<T, R> preparedQuery) {
        return operation(session -> {
            // TODO: Until this issue https://github.com/hibernate/hibernate-reactive/issues/1551 is fixed
            // we should not limit maxResults or else we could start having bugs
            // FirstResultCollector<R> collector = new FirstResultCollector<>(!preparedQuery.isNative());
            FirstResultCollector<R> collector = new FirstResultCollector<>(false);
            collectFindOne(session, preparedQuery, collector);
            return collector.result;
        });
    }

    @Override
    public <T> Mono<T> findOptional(Class<T> type, Object id) {
        return findOne(type, id);
    }

    @Override
    public <T, R> Mono<R> findOptional(PreparedQuery<T, R> preparedQuery) {
        return findOne(preparedQuery);
    }

    @Override
    public <T> Flux<T> findAll(PagedQuery<T> pagedQuery) {
        return operationFlux(session -> findPaged(session, pagedQuery));
    }

    @Override
    public <R> Mono<Page<R>> findPage(PagedQuery<R> pagedQuery) {
        return operation(session -> findPaged(session, pagedQuery).collectList()
                .flatMap(resultList -> countOf(session, pagedQuery.getRootEntity(), pagedQuery.getPageable())
                        .map(total -> Page.of(resultList, pagedQuery.getPageable(), total))));
    }

    @Override
    public <T> Mono<Long> count(PagedQuery<T> pagedQuery) {
        return operation(session -> countOf(session, Long.class, null));
    }

    private <T> Flux<T> findPaged(Stage.Session session, PagedQuery<T> pagedQuery) {
        ListResultCollector<T> collector = new ListResultCollector<>();
        collectPagedResults(sessionFactory.getCriteriaBuilder(), session, pagedQuery, collector);
        return collector.result;
    }

    private <T> Mono<Long> countOf(Stage.Session session, Class<T> entity, @Nullable Pageable pageable) {
        SingleResultCollector<Long> collector = new SingleResultCollector<>();
        collectCountOf(sessionFactory.getCriteriaBuilder(), session, entity, pageable, collector);
        return collector.result;
    }

    @Override
    public <T, R> Flux<R> findAll(PreparedQuery<T, R> preparedQuery) {
        return operationFlux(session -> {
            ListResultCollector<R> resultCollector = new ListResultCollector<>();
            collectFindAll(session, preparedQuery, resultCollector);
            return resultCollector.result;
        });
    }

    @Override
    public <T> Mono<T> persist(InsertOperation<T> operation) {
        return operation(session -> {
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            T entity = operation.getEntity();
            Mono<T> result;
            if (storedQuery != null) {
                result = executeEntityUpdate(session, storedQuery, operation.getInvocationContext(), entity)
                    .thenReturn(entity);
            } else {
                result = helper.persist(session, entity);
            }
            return flushIfNecessary(result, session, operation.getAnnotationMetadata());
        });
    }

    @Override
    public <T> Mono<T> update(UpdateOperation<T> operation) {
        return operation(session -> {
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            T entity = operation.getEntity();
            Mono<T> result;
            if (storedQuery != null) {
                result = executeEntityUpdate(session, storedQuery, operation.getInvocationContext(), entity)
                        .thenReturn(entity);
            } else {
                result = helper.merge(session, entity);
            }
            return flushIfNecessary(result, session, operation.getAnnotationMetadata());
        });
    }

    private <T> Mono<Integer> executeEntityUpdate(Stage.Session session,
                                                  StoredQuery<T, ?> storedQuery,
                                                  InvocationContext<?, ?> invocationContext,
                                                  T entity) {
        Stage.MutationQuery query = session.createMutationQuery(storedQuery.getQuery());
        bindParameters(query, storedQuery, invocationContext, entity);
        return helper.executeUpdate(query);
    }

    @Override
    public <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        return operationFlux(session -> {
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            Flux<T> result;
            if (storedQuery != null) {
                result = Flux.fromIterable(operation)
                        .concatMap(t -> executeEntityUpdate(session, storedQuery, operation.getInvocationContext(), t).thenReturn(t));
            } else {
                result = helper.mergeAll(session, operation);
            }
            return flushIfNecessaryFlux(result, session, operation.getAnnotationMetadata());
        });
    }

    @Override
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        return operationFlux(session -> {
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            Flux<T> result;
            if (storedQuery != null) {
                result = Flux.fromIterable(operation)
                    .concatMap(t -> executeEntityUpdate(session, storedQuery, operation.getInvocationContext(), t).thenReturn(t));
            } else {
                result = helper.persistAll(session, operation);
            }
            return flushIfNecessaryFlux(result, session, operation.getAnnotationMetadata());
        });
    }

    @Override
    public Mono<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return operation(session -> {
            String query = preparedQuery.getQuery();
            Stage.MutationQuery q = session.createMutationQuery(query);
            bindParameters(q, preparedQuery, true);
            Mono<Number> result = helper.executeUpdate(q).cast(Number.class);
            return flushIfNecessary(result, session, preparedQuery.getAnnotationMetadata());
        });
    }

    @Override
    public Mono<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return executeUpdate(preparedQuery);
    }

    @Override
    public <T> Mono<Number> delete(DeleteOperation<T> operation) {
        return operation(session -> {
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            Mono<Number> result;
            if (storedQuery != null) {
                result = executeEntityUpdate(session, storedQuery, operation.getInvocationContext(), operation.getEntity()).cast(Number.class);
            } else {
                result = helper.remove(session, operation.getEntity()).thenReturn(1);
            }
            return flushIfNecessary(result, session, operation.getAnnotationMetadata());
        });
    }

    @Override
    public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return operation(session -> {
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            Mono<Number> result;
            if (storedQuery != null) {
                result = Flux.fromIterable(operation)
                        .concatMap(entity -> executeEntityUpdate(session, storedQuery, operation.getInvocationContext(), entity))
                        .reduce(0, (i1, i2) -> i1 + i2)
                        .cast(Number.class);
            } else {
                result = helper.removeAll(session, operation);
            }
            return flushIfNecessary(result, session, operation.getAnnotationMetadata());
        });
    }

    private <T> Mono<T> flushIfNecessary(Mono<T> m, Stage.Session session, AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasAnnotation(QueryHint.class)) {
            FlushModeType flushModeType = getFlushModeType(annotationMetadata);
            if (flushModeType == FlushModeType.AUTO) {
                return m.flatMap(t -> helper.flush(session).thenReturn(t));
            }
        }
        return m;
    }

    private <T> Flux<T> flushIfNecessaryFlux(Flux<T> flux, Stage.Session session, AnnotationMetadata annotationMetadata) {
        return flushIfNecessary(flux.collectList(), session, annotationMetadata).flatMapMany(Flux::fromIterable);
    }

    private <T> Mono<T> operation(Function<Stage.Session, Mono<T>> work) {
        return transactionOperations.withTransactionMono(tx -> work.apply(tx.getConnection()));
    }

    private <T> Flux<T> operationFlux(Function<Stage.Session, Flux<T>> work) {
        return transactionOperations.withTransactionFlux(tx -> work.apply(tx.getConnection()));
    }

    @Override
    public <T> Mono<T> withSession(Function<Stage.Session, Mono<T>> work) {
        return connectionOperations.withConnectionMono(status -> work.apply(status.getConnection()));
    }

    @Override
    public <T> Flux<T> withSessionFlux(Function<Stage.Session, Flux<T>> work) {
        return connectionOperations.withConnectionFlux(status -> work.apply(status.getConnection()));
    }

    @Override
    public ConversionService getConversionService() {
        return dataConversionService;
    }

    @Override
    public <R> Mono<R> findOne(CriteriaQuery<R> query) {
        return withSession(session -> helper.monoFromCompletionStage(() -> session.createQuery(query).getSingleResult()));
    }

    @Override
    public Publisher<Boolean> exists(CriteriaQuery<?> query) {
        return withSession(session -> helper.monoFromCompletionStage(() -> session.createQuery(query).setMaxResults(1).getResultList().thenApply(l -> !l.isEmpty())));
    }

    @Override
    public <T> Flux<T> findAll(CriteriaQuery<T> query) {
        return withSession(session -> helper.monoFromCompletionStage(() -> session.createQuery(query).getResultList()))
            .flatMapIterable(res -> res);
    }

    @Override
    public <T> Flux<T> findAll(CriteriaQuery<T> query, int offset, int limit) {
        return withSession(session -> helper.monoFromCompletionStage(() -> {
            Stage.SelectionQuery<T> sessionQuery = session.createQuery(query);
            if (offset > 0) {
                sessionQuery = sessionQuery.setFirstResult(offset);
            }
            if (limit > 0) {
                sessionQuery = sessionQuery.setMaxResults(limit);
            }
            return sessionQuery.getResultList();
        })).flatMapIterable(res -> res);
    }

    @Override
    public Mono<Number> updateAll(CriteriaUpdate<Number> query) {
        return withSession(session -> helper.monoFromCompletionStage(() -> session.createQuery(query).executeUpdate()).map(n -> n));
    }

    @Override
    public Mono<Number> deleteAll(CriteriaDelete<Number> query) {
        return withSession(session -> helper.monoFromCompletionStage(() -> session.createQuery(query).executeUpdate()).map(n -> n));
    }

    private final class ListResultCollector<R> extends ResultCollector<R> {

        private Flux<R> result;

        @Override
        protected void collectTuple(Stage.SelectionQuery<?> query, Function<Tuple, R> fn) {
            Flux<Tuple> tuples = (Flux<Tuple>) helper.list(query);
            result = tuples.map(fn);
        }

        @Override
        protected void collect(Stage.SelectionQuery<?> query) {
            result = (Flux<R>) helper.list(query);
        }
    }

    private final class SingleResultCollector<R> extends ResultCollector<R> {

        private Mono<R> result;

        @Override
        protected void collectTuple(Stage.SelectionQuery<?> query, Function<Tuple, R> fn) {
            result = ((Mono<Tuple>) helper.singleResult(query)).map(fn);
        }

        @Override
        protected void collect(Stage.SelectionQuery<?> query) {
            result = (Mono<R>) helper.singleResult(query);
        }

    }

    private final class FirstResultCollector<R> extends ResultCollector<R> {

        private final boolean limitOne;
        private Mono<R> result;

        private FirstResultCollector(boolean limitOne) {
            this.limitOne = limitOne;
        }

        @Override
        protected void collectTuple(Stage.SelectionQuery<?> query, Function<Tuple, R> fn) {
            result = getFirst((Stage.SelectionQuery<Tuple>) query).map(fn);
        }

        @Override
        protected void collect(Stage.SelectionQuery<?> query) {
            result = getFirst((Stage.SelectionQuery<R>) query);
        }

        private <T> Mono<T> getFirst(Stage.SelectionQuery<T> q) {
            if (limitOne) {
                q.setMaxResults(1);
            }
            return helper.list(q).next();
        }

    }

}
