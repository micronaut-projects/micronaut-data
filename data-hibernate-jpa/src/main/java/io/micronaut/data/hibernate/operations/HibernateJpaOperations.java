/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.hibernate.operations;

import io.micronaut.aop.InvocationContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.annotation.sql.Procedure;
import io.micronaut.data.hibernate.conf.RequiresSyncHibernate;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.EntityInstanceOperation;
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
import io.micronaut.data.operations.CriteriaRepositoryOperations;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperationsSupportingCriteria;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperationsSupportingCriteria;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.graph.RootGraph;
import org.hibernate.procedure.ProcedureCall;
import org.hibernate.query.CommonQueryContract;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Implementation of the {@link JpaRepositoryOperations} interface for Hibernate.
 *
 * @author graemerocher
 * @since 1.0
 */
@RequiresSyncHibernate
@EachBean(DataSource.class)
final class HibernateJpaOperations extends AbstractHibernateOperations<Session, CommonQueryContract, Query<?>>
    implements JpaRepositoryOperations, AsyncCapableRepository, ReactiveCapableRepository, CriteriaRepositoryOperations {

    private final SessionFactory sessionFactory;
    private final TransactionOperations<Session> transactionOperations;
    private ExecutorAsyncOperations asyncOperations;
    private ExecutorService executorService;

    /**
     * Default constructor.
     *
     * @param sessionFactory        The session factory
     * @param transactionOperations The transaction operations
     * @param executorService       The executor service for I/O tasks to use
     * @param runtimeEntityRegistry The runtime entity registry
     * @param dataConversionService The data conversion service
     */
    public HibernateJpaOperations(
        @NonNull @Parameter SessionFactory sessionFactory,
        @NonNull @Parameter TransactionOperations<Session> transactionOperations,
        @Named("io") @Nullable ExecutorService executorService,
        RuntimeEntityRegistry runtimeEntityRegistry,
        DataConversionService dataConversionService) {
        super(runtimeEntityRegistry, dataConversionService);
        ArgumentUtils.requireNonNull("sessionFactory", sessionFactory);
        this.sessionFactory = sessionFactory;
        this.transactionOperations = transactionOperations;
        this.executorService = executorService;
    }

    @Override
    public <T> RuntimePersistentEntity<T> getEntity(Class<T> type) {
        return runtimeEntityRegistry.getEntity(type);
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return super.getApplicationContext();
    }

    @Override
    public ConversionService getConversionService() {
        return super.getConversionService();
    }

    @Override
    protected void setParameter(CommonQueryContract query, String parameterName, Object value) {
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameter(CommonQueryContract query, String parameterName, Object value, Argument<?> argument) {
        // How to provide type, if needed at all? Was needed prior to Hibernate 6
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameterList(CommonQueryContract query, String parameterName, Collection<Object> value) {
        if (value == null) {
            value = Collections.emptyList();
        }
        // Passing collection as param like this as well, before Hibernate 6 there was other method to pass collection
        query.setParameterList(parameterName, value);
    }

    @Override
    protected void setParameterList(CommonQueryContract query, String parameterName, Collection<Object> value, Argument<?> argument) {
        if (value == null) {
            value = Collections.emptyList();
        }
        // Can we ignore type? Was needed before Hibernate 6
        query.setParameterList(parameterName, value);
    }

    @Override
    protected void setParameter(CommonQueryContract query, int parameterIndex, Object value) {
        query.setParameter(parameterIndex, value);
    }

    @Override
    protected void setParameter(CommonQueryContract query, int parameterIndex, Object value, Argument<?> argument) {
        query.setParameter(parameterIndex, value);
    }

    @Override
    protected void setParameterList(CommonQueryContract query, int parameterIndex, Collection<Object> value) {
        if (value == null) {
            value = Collections.emptyList();
        }
        query.setParameterList(parameterIndex, value);
    }

    @Override
    protected void setParameterList(CommonQueryContract query, int parameterIndex, Collection<Object> value, Argument<?> argument) {
        if (value == null) {
            value = Collections.emptyList();
        }
        // Can we ignore type? Was needed before Hibernate 6
        query.setParameterList(parameterIndex, value);
    }

    @Override
    protected void setHint(Query<?> query, String hintName, Object value) {
        query.setHint(hintName, value);
    }

    @Override
    protected <T> RootGraph<T> getEntityGraph(Session session, Class<T> entityType, String graphName) {
        return (RootGraph<T>) session.getEntityGraph(graphName);
    }

    @Override
    protected <T> RootGraph<T> createEntityGraph(Session session, Class<T> entityType) {
        return session.createEntityGraph(entityType);
    }

    @Override
    protected Query<?> createQuery(Session session, String query, Class<?> resultType) {
        return session.createQuery(query, resultType);
    }

    @Override
    protected Query<?> createNativeQuery(Session session, String query, Class<?> resultType) {
        return session.createNativeQuery(query, resultType);
    }

    @Override
    protected Query<?> createQuery(Session session, CriteriaQuery<?> criteriaQuery) {
        return session.createQuery(criteriaQuery);
    }

    @Override
    protected void setOffset(Query<?> query, int offset) {
        query.setFirstResult(offset);
    }

    @Override
    protected void setMaxResults(Query<?> query, int max) {
        query.setMaxResults(max);
    }

    @Nullable
    @Override
    public <T> T findOne(@NonNull Class<T> type, @NonNull Object id) {
        return executeRead(session -> session.byId(type).load(id));
    }

    @NonNull
    @Override
    public <T> T load(@NonNull Class<T> type, @NonNull Object id) {
        return executeRead(session -> session.getReference(type, id));
    }

    @Override
    public <T> T merge(T entity) {
        return executeWrite(session -> session.merge(entity));
    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return executeRead(session -> {
            // limit does not work with native queries and does not produce expected
            // results with EntityGraph annotation and joins
            boolean limitOne = !preparedQuery.isNative() && !hasEntityGraph(preparedQuery.getAnnotationMetadata());
            FirstResultCollector<R> collector = new FirstResultCollector<>(limitOne);
            collectFindOne(session, preparedQuery, collector);
            return collector.result;
        });
    }

    @Override
    public <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return findOne(preparedQuery) != null;
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(@NonNull PagedQuery<T> pagedQuery) {
        return executeRead(session -> findPaged(session, pagedQuery));
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(@NonNull PagedQuery<T> pagedQuery) {
        return executeRead(session -> {
            StreamResultCollector<T> collector = new StreamResultCollector<>();
            collectPagedResults(sessionFactory.getCriteriaBuilder(), session, pagedQuery, collector);
            return collector.result;
        });
    }

    @Override
    public <R> Page<R> findPage(@NonNull PagedQuery<R> pagedQuery) {
        return executeRead(session -> Page.of(
            findPaged(session, pagedQuery),
            pagedQuery.getPageable(),
            countOf(session, pagedQuery, pagedQuery.getPageable())
        ));
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        return executeRead(session -> countOf(session, pagedQuery, null));
    }

    private <T> List<T> findPaged(Session session, PagedQuery<T> pagedQuery) {
        ListResultCollector<T> collector = new ListResultCollector<>();
        collectPagedResults(sessionFactory.getCriteriaBuilder(), session, pagedQuery, collector);
        return collector.result;
    }

    private <T> Long countOf(Session session, PagedQuery<T> pagedQuery, @Nullable Pageable pageable) {
        SingleResultCollector<Long> collector = new SingleResultCollector<>();
        collectCountOf(sessionFactory.getCriteriaBuilder(), session, pagedQuery.getRootEntity(), pageable, collector);
        return collector.result;
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return executeRead(session -> {
            ListResultCollector<R> resultCollector = new ListResultCollector<>();
            collectFindAll(session, preparedQuery, resultCollector);
            return resultCollector.result;
        });
    }

    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        return executeWrite(session -> {
            if (storedQuery != null) {
                return executeUpdate(operation, session, storedQuery);
            }
            T entity = operation.getEntity();
            session.persist(entity);
            flushIfNecessary(session, operation.getAnnotationMetadata());
            return entity;
        });
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        return executeWrite(session -> {
            if (storedQuery != null) {
                return executeUpdate(operation, session, storedQuery);
            }
            T entity = operation.getEntity();
            entity = session.merge(entity);
            flushIfNecessary(session, operation.getAnnotationMetadata());
            return entity;
        });
    }

    private <T> T executeUpdate(EntityInstanceOperation<T> operation, Session session, StoredQuery<T, ?> storedQuery) {
        executeUpdate(session, storedQuery, operation.getInvocationContext(), operation.getEntity());
        if (flushIfNecessary(session, operation.getAnnotationMetadata())) {
            session.remove(operation.getEntity());
        }
        return operation.getEntity();
    }

    @NonNull
    @Override
    public <T> Iterable<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        return executeWrite(session -> {
            if (storedQuery != null) {
                return executeUpdate(operation, session, storedQuery);
            }
            List<T> results = new ArrayList<>();
            for (T entity : operation) {
                T merge = session.merge(entity);
                results.add(merge);
            }
            flushIfNecessary(session, operation.getAnnotationMetadata());
            return results;
        });
    }

    private <T> BatchOperation<T> executeUpdate(BatchOperation<T> operation, Session session, StoredQuery<T, ?> storedQuery) {
        for (T entity : operation) {
            executeUpdate(session, storedQuery, operation.getInvocationContext(), entity);
        }
        if (flushIfNecessary(session, operation.getAnnotationMetadata())) {
            for (T entity : operation) {
                session.remove(entity);
            }
        }
        return operation;
    }

    @NonNull
    @Override
    public <T> Iterable<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        return executeWrite(session -> {
            if (storedQuery != null) {
                return executeUpdate(operation, session, storedQuery);
            }
            for (T entity : operation) {
                session.persist(entity);
            }
            flushIfNecessary(session, operation.getAnnotationMetadata());
            return operation;
        });
    }

    private boolean flushIfNecessary(EntityManager entityManager, AnnotationMetadata annotationMetadata) {
        return flushIfNecessary(entityManager, annotationMetadata, false);
    }

    private boolean flushIfNecessary(EntityManager entityManager, AnnotationMetadata annotationMetadata, boolean clear) {
        if (annotationMetadata.hasAnnotation(QueryHint.class)) {
            FlushModeType flushModeType = getFlushModeType(annotationMetadata);
            if (flushModeType == FlushModeType.AUTO) {
                entityManager.flush();
                if (clear) {
                    entityManager.clear();
                }
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return executeWrite(session -> {
            String query = preparedQuery.getQuery();
            MutationQuery q = preparedQuery.isNative() ? session.createNativeMutationQuery(query) : session.createMutationQuery(query);
            bindParameters(q, preparedQuery, true);
            int numAffected = q.executeUpdate();
            flushIfNecessary(session, preparedQuery.getAnnotationMetadata(), true);
            return Optional.of(numAffected);
        });
    }

    @Override
    public <R> List<R> execute(PreparedQuery<?, R> preparedQuery) {
        return executeWrite(session -> {
            boolean needsOutRegistered = false;
            if (preparedQuery.isProcedure()) {
                Optional<String> named = preparedQuery.getAnnotationMetadata().stringValue(Procedure.class, "named");
                ProcedureCall procedureQuery;
                if (named.isPresent()) {
                    procedureQuery = session.createNamedStoredProcedureQuery(named.get());
                } else {
                    String procedureName = preparedQuery.getAnnotationMetadata().stringValue(Procedure.class).orElseGet(preparedQuery::getName);
                    if (preparedQuery.getResultArgument().isVoid()) {
                        procedureQuery = session.createStoredProcedureQuery(procedureName);
                    } else {
                        procedureQuery = session.createStoredProcedureQuery(
                                procedureName,
                                preparedQuery.getResultArgument().getType()
                        );
                        needsOutRegistered = true;
                    }
                    int index = 1;
                    for (QueryParameterBinding queryBinding : preparedQuery.getQueryBindings()) {
                        int parameterIndex = queryBinding.getParameterIndex();
                        Argument<?> argument = preparedQuery.getArguments()[parameterIndex];
                        procedureQuery.registerStoredProcedureParameter(
                                index++,
                                argument.getType(),
                                ParameterMode.IN);
                    }
                    if (needsOutRegistered) {
                        procedureQuery.registerStoredProcedureParameter(
                                index,
                                preparedQuery.getResultArgument().getType(),
                                ParameterMode.OUT);
                    }
                }
                boolean bindNamed = procedureQuery.getRegisteredParameters().stream().anyMatch(p -> p.getName() != null);
                bindParameters(procedureQuery, preparedQuery, bindNamed);
                procedureQuery.execute();
                if (preparedQuery.getResultArgument().isVoid()) {
                    flushIfNecessary(session, preparedQuery.getAnnotationMetadata(), true);
                    return List.of();
                }
                jakarta.persistence.Parameter procedureParameter = procedureQuery.getRegisteredParameters().stream().filter(p -> p.getMode() == ParameterMode.OUT)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Cannot determine the output parameter!"));
                Object result;
                if (bindNamed) {
                    result = procedureQuery.getOutputParameterValue(procedureParameter.getName());
                } else {
                    result = procedureQuery.getOutputParameterValue(preparedQuery.getQueryBindings().size() + 1);
                }
                return List.of((R) result);
            } else {
                if (preparedQuery.isNative()) {
                    Iterable<?> result = findAll(preparedQuery);
                    return (List<R>) result;
                }
                throw new IllegalStateException("Only native query supports update RETURNING operations.");
            }
        });
    }

    @Override
    public <T> int delete(@NonNull DeleteOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        return executeWrite(session -> {
            if (storedQuery != null) {
                int numAffected = executeUpdate(session, storedQuery, operation.getInvocationContext(), operation.getEntity());
                if (flushIfNecessary(session, operation.getAnnotationMetadata())) {
                    session.remove(operation.getEntity());
                }
                return numAffected;
            }
            session.remove(operation.getEntity());
            return 1;
        });
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        Integer result = executeWrite(session -> {
            if (storedQuery != null) {
                int i = 0;
                for (T entity : operation) {
                    i += executeUpdate(session, storedQuery, operation.getInvocationContext(), entity);
                }
                if (flushIfNecessary(session, operation.getAnnotationMetadata())) {
                    for (T entity : operation) {
                        session.remove(entity);
                    }
                }
                return i;
            }
            int i = 0;
            for (T entity : operation) {
                session.remove(entity);
                i++;
            }
            return i;
        });
        return Optional.ofNullable(result);
    }

    private <T> int executeUpdate(Session session, StoredQuery<T, ?> storedQuery, InvocationContext<?, ?> invocationContext, T entity) {
        MutationQuery query = session.createMutationQuery(storedQuery.getQuery());
        bindParameters(query, storedQuery, invocationContext, entity);
        return query.executeUpdate();
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return executeRead(session -> {
            StreamResultCollector<R> resultCollector = new StreamResultCollector<>();
            collectFindAll(session, preparedQuery, resultCollector);
            return resultCollector.result;
        });
    }

    private <R> R executeRead(Function<Session, R> callback) {
        return transactionOperations.executeRead(status -> callback.apply(getCurrentSession()));
    }

    private <R> R executeWrite(Function<Session, R> callback) {
        return transactionOperations.executeWrite(status -> callback.apply(getCurrentSession()));
    }

    private Session getCurrentSession() {
        return sessionFactory.getCurrentSession();
    }

    @NonNull
    private ExecutorService newLocalThreadPool() {
        this.executorService = Executors.newCachedThreadPool();
        return executorService;
    }

    @NonNull
    @Override
    public ExecutorAsyncOperations async() {
        ExecutorAsyncOperations executorAsyncOperations = this.asyncOperations;
        if (executorAsyncOperations == null) {
            synchronized (this) { // double check
                executorAsyncOperations = this.asyncOperations;
                if (executorAsyncOperations == null) {
                    executorAsyncOperations = new ExecutorAsyncOperationsSupportingCriteria(
                        this,
                        this,
                        executorService != null ? executorService : newLocalThreadPool()
                    );
                    this.asyncOperations = executorAsyncOperations;
                }
            }
        }
        return executorAsyncOperations;
    }

    @NonNull
    @Override
    public ReactiveRepositoryOperations reactive() {
        if (dataConversionService instanceof DataConversionService asDataConversionService) {
            return new ExecutorReactiveOperationsSupportingCriteria((ExecutorAsyncOperationsSupportingCriteria) async(), asDataConversionService);
        }
        return new ExecutorReactiveOperationsSupportingCriteria((ExecutorAsyncOperationsSupportingCriteria) async(), null);
    }

    @NonNull
    @Override
    public EntityManager getCurrentEntityManager() {
        return sessionFactory.getCurrentSession();
    }

    @NonNull
    @Override
    public EntityManagerFactory getEntityManagerFactory() {
        return this.sessionFactory;
    }

    @Override
    public void flush() {
        executeWrite(session -> {
                session.flush();
                return null;
            }
        );
    }

    private boolean hasEntityGraph(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasAnnotation(EntityGraph.class);
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return sessionFactory.getCriteriaBuilder();
    }

    @Override
    public boolean exists(CriteriaQuery<?> query) {
        return executeRead(session -> {
            try (Stream<?> stream = session.createQuery(query).stream()) {
                return stream.findAny().isPresent();
            }
        });
    }

    @Override
    public <R> R findOne(CriteriaQuery<R> query) {
        return executeRead(session -> session.createQuery(query).uniqueResult());
    }

    @Override
    public <T> List<T> findAll(CriteriaQuery<T> query) {
        return executeRead(session -> session.createQuery(query).getResultList());
    }

    @Override
    public <T> List<T> findAll(CriteriaQuery<T> query, int offset, int limit) {
        return executeRead(session -> {
            Query<T> sessionQuery = session.createQuery(query);
            if (offset > 0) {
                sessionQuery = sessionQuery.setFirstResult(offset);
            }
            if (limit > 0) {
                sessionQuery = sessionQuery.setMaxResults(limit);
            }
            return sessionQuery.getResultList();
        });
    }

    @Override
    public Optional<Number> updateAll(CriteriaUpdate<Number> query) {
        return Optional.ofNullable(executeWrite(session -> session.createMutationQuery(query).executeUpdate()));
    }

    @Override
    public Optional<Number> deleteAll(CriteriaDelete<Number> query) {
        return Optional.ofNullable(executeWrite(session -> session.createMutationQuery(query).executeUpdate()));
    }

    private final class ListResultCollector<R> extends ResultCollector<R> {

        private List<R> result;

        @Override
        protected void collectTuple(Query<?> query, Function<Tuple, R> fn) {
            result = ((List<Tuple>) query.getResultList()).stream().map(fn).toList();
        }

        @Override
        protected void collect(Query<?> query) {
            result = (List<R>) query.getResultList();
        }
    }

    private final class StreamResultCollector<R> extends ResultCollector<R> {

        private Stream<R> result;

        @Override
        protected void collectTuple(Query<?> query, Function<Tuple, R> fn) {
            result = ((Stream<Tuple>) query.getResultStream()).map(fn);
        }

        @Override
        protected void collect(Query<?> query) {
            result = (Stream<R>) query.getResultStream();
        }
    }

    private final class SingleResultCollector<R> extends ResultCollector<R> {

        private R result;

        @Override
        protected void collectTuple(Query<?> query, Function<Tuple, R> fn) {
            Tuple tuple = (Tuple) query.getSingleResult();
            if (tuple != null) {
                this.result = fn.apply(tuple);
            }
        }

        @Override
        protected void collect(Query<?> query) {
            result = (R) query.getSingleResult();
        }
    }

    private final class FirstResultCollector<R> extends ResultCollector<R> {

        private final boolean limitOne;
        private R result;

        private FirstResultCollector(boolean limitOne) {
            this.limitOne = limitOne;
        }

        @Override
        protected void collectTuple(Query<?> query, Function<Tuple, R> fn) {
            Tuple tuple = getFirst(query);
            if (tuple != null) {
                this.result = fn.apply(tuple);
            }
        }

        @Override
        protected void collect(Query<?> query) {
            result = getFirst(query);
        }

        private <T> T getFirst(Query<?> q) {
            if (limitOne) {
                q.setMaxResults(1);
            }
            Iterator<T> iterator = (Iterator<T>) q.getResultList().iterator();
            if (iterator.hasNext()) {
                return iterator.next();
            }
            return null;
        }
    }

}
