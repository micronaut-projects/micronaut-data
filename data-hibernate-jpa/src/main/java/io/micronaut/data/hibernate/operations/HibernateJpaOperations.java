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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
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
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.jdbc.spring.HibernatePresenceCondition;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Named;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.graph.RootGraph;
import org.hibernate.query.Query;
import org.hibernate.type.Type;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.Tuple;
import javax.persistence.criteria.CriteriaQuery;
import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the {@link JpaRepositoryOperations} interface for Hibernate.
 *
 * @author graemerocher
 * @since 1.0
 */
@EachBean(DataSource.class)
@TypeHint(HibernatePresenceCondition.class)
public class HibernateJpaOperations extends AbstractHibernateOperations<Session, Query<?>>
        implements JpaRepositoryOperations, AsyncCapableRepository, ReactiveCapableRepository {

    private final SessionFactory sessionFactory;
    private final TransactionOperations<Connection> transactionOperations;
    private ExecutorAsyncOperations asyncOperations;
    private ExecutorService executorService;

    /**
     * Default constructor.
     *
     * @param sessionFactory        The session factory
     * @param transactionOperations The transaction operations
     * @param executorService       The executor service for I/O tasks to use
     * @param runtimeEntityRegistry The runtime entity registry
     */
    @Deprecated
    protected HibernateJpaOperations(
            @NonNull SessionFactory sessionFactory,
            @NonNull @Parameter TransactionOperations<Connection> transactionOperations,
            @Named("io") @Nullable ExecutorService executorService,
            RuntimeEntityRegistry runtimeEntityRegistry) {
        this(sessionFactory, transactionOperations, executorService, runtimeEntityRegistry, null);
    }

    /**
     * Default constructor.
     *
     * @param sessionFactory        The session factory
     * @param transactionOperations The transaction operations
     * @param executorService       The executor service for I/O tasks to use
     * @param runtimeEntityRegistry The runtime entity registry
     * @param dataConversionService The data conversion service
     */
    @Creator
    public HibernateJpaOperations(
            @NonNull @Parameter SessionFactory sessionFactory,
            @NonNull @Parameter TransactionOperations<Connection> transactionOperations,
            @Named("io") @Nullable ExecutorService executorService,
            RuntimeEntityRegistry runtimeEntityRegistry,
            DataConversionService<?> dataConversionService) {
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
    public ConversionService<?> getConversionService() {
        return super.getConversionService();
    }

    @Override
    protected void setParameter(Query<?> query, String parameterName, Object value) {
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameter(Query<?> query, String parameterName, Object value, Argument argument) {
        if (value == null) {
            Type valueType = sessionFactory.getTypeHelper().heuristicType(argument.getType().getName());
            query.setParameter(parameterName, null, valueType);
        } else {
            query.setParameter(parameterName, value);
        }
    }

    @Override
    protected void setParameterList(Query<?> query, String parameterName, Collection<Object> value) {
        query.setParameterList(parameterName, value);
    }

    @Override
    protected void setParameterList(Query<?> query, String parameterName, Collection<Object> value, Argument argument) {
        Type valueType = sessionFactory.getTypeHelper().heuristicType(argument.getType().getName());
        if (value == null) {
            value = Collections.emptyList();
        }
        if (valueType != null) {
            query.setParameterList(parameterName, value, valueType);
        } else {
            query.setParameterList(parameterName, value);
        }
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
        if (resultType == null) {
            return session.createQuery(query);
        }
        return session.createQuery(query, resultType);
    }

    @Override
    protected Query<?> createNativeQuery(Session session, String query, Class<?> resultType) {
        if (resultType == null) {
            return session.createNativeQuery(query);
        }
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

    @NonNull
    @Override
    public Map<String, Object> getQueryHints(@NonNull StoredQuery<?, ?> storedQuery) {
        return super.getQueryHints(storedQuery);
    }

    @Nullable
    @Override
    public <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return transactionOperations.executeRead(status -> sessionFactory.getCurrentSession().byId(type).load(id));
    }

    @NonNull
    @Override
    public <T> T load(@NonNull Class<T> type, @NonNull Serializable id) {
        return transactionOperations.executeRead(status -> sessionFactory.getCurrentSession().load(type, id));
    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return transactionOperations.executeRead(status -> {
            FirstResultCollector<R> collector = new FirstResultCollector<>(!preparedQuery.isNative());
            collectFindOne(sessionFactory.getCurrentSession(), preparedQuery, collector);
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
        return transactionOperations.executeRead(status -> findPaged(getCurrentSession(), pagedQuery));
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(@NonNull PagedQuery<T> pagedQuery) {
        return transactionOperations.executeRead(status -> {
            StreamResultCollector<T> collector = new StreamResultCollector<>();
            collectPagedResults(sessionFactory.getCriteriaBuilder(), getCurrentSession(), pagedQuery, collector);
            return collector.result;
        });

    }

    @Override
    public <R> Page<R> findPage(@NonNull PagedQuery<R> pagedQuery) {
        return transactionOperations.executeRead(status -> {
            Session session = getCurrentSession();
            return Page.of(
                    findPaged(session, pagedQuery),
                    pagedQuery.getPageable(),
                    countOf(session, pagedQuery, pagedQuery.getPageable())
            );
        });
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        return transactionOperations.executeRead(status -> countOf(getCurrentSession(), pagedQuery, null));
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
        return transactionOperations.executeRead(status -> {
            ListResultCollector<R> resultCollector = new ListResultCollector<>();
            collectFindAll(sessionFactory.getCurrentSession(), preparedQuery, resultCollector);
            return resultCollector.result;
        });
    }

    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        return transactionOperations.executeWrite(status -> {
            T entity = operation.getEntity();

            EntityManager entityManager = sessionFactory.getCurrentSession();
            entityManager.persist(entity);
            flushIfNecessary(entityManager, operation.getAnnotationMetadata());
            return entity;
        });
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        return transactionOperations.executeWrite(status -> {
            EntityManager session = sessionFactory.getCurrentSession();
            if (storedQuery != null) {
                executeEntityUpdate(storedQuery, operation.getEntity());
                if (flushIfNecessary(session, operation.getAnnotationMetadata())) {
                    session.remove(operation.getEntity());
                }
                return operation.getEntity();
            }
            T entity = operation.getEntity();
            entity = session.merge(entity);
            flushIfNecessary(session, operation.getAnnotationMetadata());
            return entity;
        });
    }

    @NonNull
    @Override
    public <T> Iterable<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        return transactionOperations.executeWrite(status -> {
            EntityManager entityManager = sessionFactory.getCurrentSession();
            if (storedQuery != null) {
                for (T entity : operation) {
                    executeEntityUpdate(storedQuery, entity);
                }
                if (flushIfNecessary(entityManager, operation.getAnnotationMetadata())) {
                    for (T entity : operation) {
                        entityManager.remove(entity);
                    }
                }
                return operation;
            }
            List<T> results = new ArrayList<>();
            for (T entity : operation) {
                T merge = entityManager.merge(entity);
                results.add(merge);
            }
            flushIfNecessary(entityManager, operation.getAnnotationMetadata());
            return results;
        });
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public <T> Iterable<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return transactionOperations.executeWrite(status -> {
            if (operation != null) {
                EntityManager entityManager = sessionFactory.getCurrentSession();
                for (T entity : operation) {
                    entityManager.persist(entity);
                }
                flushIfNecessary(entityManager, operation.getAnnotationMetadata());
                return operation;
            } else {
                return Collections.emptyList();
            }
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
        return transactionOperations.executeWrite(status -> {
            String query = preparedQuery.getQuery();
            Query<?> q = getCurrentSession().createQuery(query);
            bindParameters(q, preparedQuery);
            int numAffected = q.executeUpdate();
            flushIfNecessary(sessionFactory.getCurrentSession(), preparedQuery.getAnnotationMetadata(), true);
            return Optional.of(numAffected);
        });
    }

    @Override
    public <T> int delete(@NonNull DeleteOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        return transactionOperations.executeWrite(status -> {
            Session session = getCurrentSession();
            if (storedQuery != null) {
                int numAffected = executeEntityUpdate(storedQuery, operation.getEntity());
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
        Integer result = transactionOperations.executeWrite(status -> {
            Session session = getCurrentSession();
            if (storedQuery != null) {
                int i = 0;
                for (T entity : operation) {
                    i += executeEntityUpdate(storedQuery, entity);
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

    private int executeEntityUpdate(StoredQuery<?, ?> storedQuery, Object entity) {
        Query<?> query = getCurrentSession().createQuery(storedQuery.getQuery());
        for (QueryParameterBinding queryParameterBinding : storedQuery.getQueryBindings()) {
            query.setParameter(queryParameterBinding.getRequiredName(), getParameterValue(queryParameterBinding.getRequiredPropertyPath(), entity));
        }
        return query.executeUpdate();
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return transactionOperations.executeRead(status -> {
            StreamResultCollector<R> resultCollector = new StreamResultCollector<>();
            collectFindAll(sessionFactory.getCurrentSession(), preparedQuery, resultCollector);
            return resultCollector.result;
        });
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
        ExecutorAsyncOperations asyncOperations = this.asyncOperations;
        if (asyncOperations == null) {
            synchronized (this) { // double check
                asyncOperations = this.asyncOperations;
                if (asyncOperations == null) {
                    asyncOperations = new ExecutorAsyncOperations(
                            this,
                            executorService != null ? executorService : newLocalThreadPool()
                    );
                    this.asyncOperations = asyncOperations;
                }
            }
        }
        return asyncOperations;
    }

    @NonNull
    @Override
    public ReactiveRepositoryOperations reactive() {
        if (dataConversionService instanceof DataConversionService) {
            return new ExecutorReactiveOperations(async(), (DataConversionService) dataConversionService);
        }
        return new ExecutorReactiveOperations(async(), null);
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
        transactionOperations.executeWrite((status) -> {
                    sessionFactory.getCurrentSession().flush();
                    return null;
                }
        );
    }

    private final class ListResultCollector<R> extends ResultCollector<R> {

        private List<R> result;

        @Override
        protected void collectTuple(Query<?> query, Function<Tuple, R> fn) {
            result = ((List<Tuple>) query.list()).stream().map(fn).collect(Collectors.toList());
        }

        @Override
        protected void collect(Query<?> query) {
            result = (List<R>) query.list();
        }
    }

    private final class StreamResultCollector<R> extends ResultCollector<R> {

        private Stream<R> result;

        @Override
        protected void collectTuple(Query<?> query, Function<Tuple, R> fn) {
            result = ((Stream<Tuple>) query.stream()).map(fn);
        }

        @Override
        protected void collect(Query<?> query) {
            result = (Stream<R>) query.stream();
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
            Tuple tuple = (Tuple) getFirst(query);
            if (tuple != null) {
                this.result = fn.apply(tuple);
            }
        }

        @Override
        protected void collect(Query<?> query) {
            result = (R) getFirst(query);
        }

        private <T> T getFirst(Query<T> q) {
            if (limitOne) {
                q.setMaxResults(1);
            }
            Iterator<T> iterator = q.list().iterator();
            if (iterator.hasNext()) {
                return iterator.next();
            }
            return null;
        }
    }

}
