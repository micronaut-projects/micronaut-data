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

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.hibernate.operations.AbstractHibernateOperations;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import org.hibernate.SessionFactory;
import org.hibernate.reactive.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import javax.persistence.EntityGraph;
import javax.persistence.FlushModeType;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ParameterExpression;
import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

/**
 * Hibernate reactive implementation of {@link io.micronaut.data.operations.reactive.ReactiveRepositoryOperations}
 * and {@link ReactorReactiveTransactionOperations}.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class DefaultHibernateReactiveRepositoryOperations extends AbstractHibernateOperations<Stage.Session, Stage.Query<?>>
        implements HibernateReactorRepositoryOperations, ReactorReactiveTransactionOperations<Stage.Session> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHibernateReactiveRepositoryOperations.class);

    private static final String MANAGER_NAME = "HibernateReactive";
    private final SessionFactory sessionFactory;
    private final Stage.SessionFactory stageSessionFactory;
    private final ReactiveHibernateHelper helper;
    private final String txStatusKey;
    private final String txDefinitionKey;
    private final String currentSessionKey;
    private final String name;

    DefaultHibernateReactiveRepositoryOperations(SessionFactory sessionFactory,
                                                 RuntimeEntityRegistry runtimeEntityRegistry,
                                                 DataConversionService dataConversionService,
                                                 @Parameter String name) {
        super(runtimeEntityRegistry, dataConversionService);
        this.sessionFactory = sessionFactory;
        this.stageSessionFactory = sessionFactory.unwrap(Stage.SessionFactory.class);
        this.helper = new ReactiveHibernateHelper(stageSessionFactory);
        if (name == null) {
            name = "default";
        }
        this.name = name;
        this.txStatusKey = ReactorReactiveTransactionOperations.TRANSACTION_STATUS_KEY_PREFIX + "." + MANAGER_NAME + "." + name;
        this.txDefinitionKey = ReactorReactiveTransactionOperations.TRANSACTION_DEFINITION_KEY_PREFIX + "." + MANAGER_NAME + "." + name;
        this.currentSessionKey = "io.micronaut.hibernate.reactive.session." + name;
    }

    @Override
    public ReactiveTransactionStatus<Stage.Session> getTransactionStatus(ContextView contextView) {
        return contextView.getOrDefault(txStatusKey, null);
    }

    @Override
    public TransactionDefinition getTransactionDefinition(ContextView contextView) {
        return contextView.getOrDefault(txDefinitionKey, null);
    }

    private Stage.Session getSession(ContextView contextView) {
        return contextView.getOrDefault(currentSessionKey, null);
    }

    @Override
    protected void setParameter(Stage.Query<?> query, String parameterName, Object value) {
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameter(Stage.Query<?> query, String parameterName, Object value, Argument argument) {
        if (value == null) {
            ParameterExpression parameter = sessionFactory.getCriteriaBuilder().parameter(argument.getType(), parameterName);
            query.setParameter(parameter, null);
        } else {
            query.setParameter(parameterName, value);
        }
    }

    @Override
    protected void setParameterList(Stage.Query<?> query, String parameterName, Collection<Object> value) {
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameterList(Stage.Query<?> query, String parameterName, Collection<Object> value, Argument argument) {
        //QueryParameterBindings
        ParameterExpression parameter = sessionFactory.getCriteriaBuilder().parameter(argument.getType(), parameterName);
        query.setParameter(parameter, value);
    }

    @Override
    protected void setHint(Stage.Query<?> query, String hintName, Object value) {
        if (value instanceof EntityGraph) {
            query.setPlan((EntityGraph) value);
            return;
        }
        throw new IllegalStateException("Unrecognized parameter: " + hintName + " with value: " + value);
    }

    @Override
    protected void setMaxResults(Stage.Query<?> query, int max) {
        query.setMaxResults(max);
    }

    @Override
    protected void setOffset(Stage.Query<?> query, int offset) {
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
    public <T> Mono<T> findOne(Class<T> type, Serializable id) {
        return operation(session -> helper.find(session, type, id));
    }

    @Override
    public <T> Mono<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery) {
        return findOne(preparedQuery).hasElement();
    }

    @Override
    protected Stage.Query<?> createNativeQuery(Stage.Session session, String query, Class<?> resultType) {
        if (resultType == null) {
            return session.createNativeQuery(query);
        }
        return session.createNativeQuery(query, resultType);
    }

    @Override
    protected Stage.Query<?> createQuery(Stage.Session session, String query, Class<?> resultType) {
        if (resultType == null) {
            return session.createQuery(query);
        }
        return session.createQuery(query, resultType);
    }

    @Override
    protected Stage.Query<?> createQuery(Stage.Session session, CriteriaQuery<?> criteriaQuery) {
        return session.createQuery(criteriaQuery);
    }

    @Override
    public <T, R> Mono<R> findOne(PreparedQuery<T, R> preparedQuery) {
        return operation(session -> {
            FirstResultCollector<R> collector = new FirstResultCollector<>(!preparedQuery.isNative());
            collectFindOne(session, preparedQuery, collector);
            return collector.result;
        });
    }

    @Override
    public <T> Mono<T> findOptional(Class<T> type, Serializable id) {
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
            T entity = operation.getEntity();
            Mono<T> result = helper.persist(session, entity);
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
                result = executeEntityUpdate(session, storedQuery, entity)
                        .thenReturn(entity);
            } else {
                result = helper.merge(session, entity);
            }
            return flushIfNecessary(result, session, operation.getAnnotationMetadata());
        });
    }

    private Mono<Integer> executeEntityUpdate(Stage.Session session, StoredQuery<?, ?> storedQuery, Object entity) {
        Stage.Query<Object> query = session.createQuery(storedQuery.getQuery());
        for (QueryParameterBinding queryParameterBinding : storedQuery.getQueryBindings()) {
            query.setParameter(queryParameterBinding.getRequiredName(), getParameterValue(queryParameterBinding.getRequiredPropertyPath(), entity));
        }
        return helper.executeUpdate(query);
    }

    @Override
    public <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        return operationFlux(session -> {
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            Flux<T> result;
            if (storedQuery != null) {
                result = Flux.fromIterable(operation)
                        .concatMap(t -> executeEntityUpdate(session, storedQuery, t).thenReturn(t));
            } else {
                result = helper.mergeAll(session, operation);
            }
            return flushIfNecessaryFlux(result, session, operation.getAnnotationMetadata());
        });
    }

    @Override
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        return operationFlux(session -> {
            Flux<T> result = helper.persistAll(session, operation);
            return flushIfNecessaryFlux(result, session, operation.getAnnotationMetadata());
        });
    }

    @Override
    public Mono<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return operation(session -> {
            String query = preparedQuery.getQuery();
            Stage.Query<Object> q = session.createQuery(query);
            bindParameters(q, preparedQuery);
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
                result = executeEntityUpdate(session, storedQuery, operation.getEntity()).cast(Number.class);
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
                        .concatMap(entity -> executeEntityUpdate(session, storedQuery, entity))
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
        return withTransactionMono(tx -> work.apply(tx.getConnection()));
    }

    private <T> Flux<T> operationFlux(Function<Stage.Session, Flux<T>> work) {
        return withTransactionFlux(tx -> work.apply(tx.getConnection()));
    }

    @Override
    public <T> Mono<T> withSession(Function<Stage.Session, Mono<T>> work) {
        return Mono.deferContextual(contextView -> {
            Stage.Session currentSession = getSession(contextView);
            if (currentSession != null) {
                LOG.debug("Reusing existing session for configuration: {}", name);
                return work.apply(currentSession);
            }
            LOG.debug("Opening a new session for configuration: {}", name);
            return helper.withSession(session -> work.apply(session).contextWrite(ctx -> ctx.put(currentSessionKey, session)));
        });
    }

    @Override
    public <T> Flux<T> withSessionFlux(Function<Stage.Session, Flux<T>> work) {
        return Flux.deferContextual(contextView -> {
            Stage.Session currentSession = getSession(contextView);
            if (currentSession != null) {
                LOG.debug("Reusing existing session for configuration: {}", name);
                return work.apply(currentSession);
            }
            LOG.debug("Opening a new session for configuration: {}", name);
            return helper.withSession(session -> work.apply(session).contextWrite(ctx -> ctx.put(currentSessionKey, session)).collectList())
                    .flatMapMany(Flux::fromIterable);
        });
    }

    @Override
    public <T> Flux<T> withTransactionFlux(@NonNull TransactionDefinition definition, @NonNull Function<ReactiveTransactionStatus<Stage.Session>, Flux<T>> handler) {
        return withTransactionMono(definition, status -> handler.apply(status).collectList()).flatMapMany(Flux::fromIterable);
    }

    @Override
    public <T> Flux<T> withTransactionFlux(@NonNull Function<ReactiveTransactionStatus<Stage.Session>, Flux<T>> handler) {
        return withTransactionFlux(TransactionDefinition.DEFAULT, handler);
    }

    @Override
    public <T> Flux<T> withTransaction(@NonNull TransactionDefinition definition, @NonNull ReactiveTransactionOperations.TransactionalCallback<Stage.Session, T> handler) {
        return withTransactionFlux(definition, status -> {
            try {
                return Flux.from(handler.doInTransaction(status));
            } catch (Exception e) {
                return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
            }
        });
    }

    @Override
    public <T> Mono<T> withTransactionMono(@NonNull Function<ReactiveTransactionStatus<Stage.Session>, Mono<T>> handler) {
        return withTransactionMono(TransactionDefinition.DEFAULT, handler);
    }

    @Override
    public <T> Mono<T> withTransactionMono(@NonNull TransactionDefinition definition, @NonNull Function<ReactiveTransactionStatus<Stage.Session>, Mono<T>> handler) {
        Objects.requireNonNull(definition, "Transaction definition cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");

        return Mono.deferContextual(contextView -> {
            ReactiveTransactionStatus<Stage.Session> currentTx = getTransactionStatus(contextView);
            if (currentTx != null && definition.getPropagationBehavior() != TransactionDefinition.Propagation.REQUIRES_NEW) {
                LOG.debug("Reusing existing transaction for configuration: {}", name);
                TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
                if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED || propagationBehavior == TransactionDefinition.Propagation.NEVER) {
                    return Mono.error(new TransactionUsageException("Found an existing transaction but propagation behaviour doesn't support it: " + propagationBehavior));
                }
                return doInTransaction(handler, currentTx);
            }
            Stage.Session session = getSession(contextView);
            return newTransaction(session, definition, handler);
        });
    }

    private <T> Mono<T> newTransaction(@Nullable Stage.Session session, TransactionDefinition definition, Function<ReactiveTransactionStatus<Stage.Session>, Mono<T>> handler) {
        if (session != null && definition.getPropagationBehavior() != TransactionDefinition.Propagation.REQUIRES_NEW) {
            Stage.Transaction currentTransaction = session.currentTransaction();
            if (currentTransaction != null) {
                LOG.debug("Found existing transaction in session for configuration: {}", name);
                DefaultReactiveTransactionStatus status = new DefaultReactiveTransactionStatus(session, currentTransaction, false);
                return doInTransaction(handler, status)
                        .contextWrite(context -> context.put(txStatusKey, status).put(txDefinitionKey, definition));
            }
        }
        if (definition.getPropagationBehavior() == TransactionDefinition.Propagation.MANDATORY) {
            return Mono.error(new NoTransactionException("Expected an existing transaction, but none was found in the Reactive context."));
        }
        if (definition.getIsolationLevel() != TransactionDefinition.DEFAULT.getIsolationLevel()) {
            return Mono.error(new TransactionUsageException("Isolation level not supported"));
        }
        if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
            return Mono.error(new TransactionUsageException("Timeout not supported"));
        }
        if (session != null && definition.getPropagationBehavior() != TransactionDefinition.Propagation.REQUIRES_NEW) {
            LOG.debug("Creating a new transaction for configuration: {} with definition: {}", name, definition);
            return helper.withTransaction(session, transaction -> {
                        DefaultReactiveTransactionStatus status = new DefaultReactiveTransactionStatus(session, transaction, true);
                        return doInTransaction(handler, status)
                                .contextWrite(context -> context.put(currentSessionKey, session).put(txStatusKey, status).put(txDefinitionKey, definition));
                    }
            );
        }
        LOG.debug("Creating a new session and transaction for configuration: {} with definition: {}", name, definition);
        return helper.withSessionAndTransaction((newSession, transaction) -> {
                    DefaultReactiveTransactionStatus status = new DefaultReactiveTransactionStatus(newSession, transaction, true);
                    return doInTransaction(handler, status)
                            .contextWrite(context -> context.put(currentSessionKey, newSession).put(txStatusKey, status).put(txDefinitionKey, definition));
                }
        );
    }

    private <T> Mono<T> doInTransaction(Function<ReactiveTransactionStatus<Stage.Session>, Mono<T>> handler, ReactiveTransactionStatus<Stage.Session> status) {
        try {
            return Mono.from(handler.apply(status));
        } catch (Exception e) {
            if (e instanceof TransactionSystemException) {
                return Mono.error(e);
            }
            return Mono.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
        }
    }

    @Override
    public ConversionService getConversionService() {
        return dataConversionService;
    }

    private final class ListResultCollector<R> extends ResultCollector<R> {

        private Flux<R> result;

        @Override
        protected void collectTuple(Stage.Query<?> query, Function<Tuple, R> fn) {
            Flux<Tuple> tuples = (Flux<Tuple>) helper.list(query);
            result = tuples.map(fn);
        }

        @Override
        protected void collect(Stage.Query<?> query) {
            result = (Flux<R>) helper.list(query);
        }
    }

    private final class SingleResultCollector<R> extends ResultCollector<R> {

        private Mono<R> result;

        @Override
        protected void collectTuple(Stage.Query<?> query, Function<Tuple, R> fn) {
            result = ((Mono<Tuple>) helper.singleResult(query)).map(fn);
        }

        @Override
        protected void collect(Stage.Query<?> query) {
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
        protected void collectTuple(Stage.Query<?> query, Function<Tuple, R> fn) {
            result = getFirst((Stage.Query<Tuple>) query).map(fn);
        }

        @Override
        protected void collect(Stage.Query<?> query) {
            result = getFirst((Stage.Query<R>) query);
        }

        private <T> Mono<T> getFirst(Stage.Query<T> q) {
            if (limitOne) {
                q.setMaxResults(1);
            }
            return helper.list(q).next();
        }

    }

    /**
     * Represents the current reactive transaction status.
     */
    private static final class DefaultReactiveTransactionStatus implements ReactiveTransactionStatus<Stage.Session> {
        private final Stage.Session session;
        private final Stage.Transaction transaction;
        private final boolean isNew;

        private DefaultReactiveTransactionStatus(Stage.Session session, Stage.Transaction transaction, boolean isNew) {
            this.session = session;
            this.transaction = transaction;
            this.isNew = isNew;
        }

        @Override
        public Stage.Session getConnection() {
            return session;
        }

        @Override
        public boolean isNewTransaction() {
            return isNew;
        }

        @Override
        public void setRollbackOnly() {
            transaction.markForRollback();
        }

        @Override
        public boolean isRollbackOnly() {
            return transaction.isMarkedForRollback();
        }

        @Override
        public boolean isCompleted() {
            return false;
        }
    }

}
