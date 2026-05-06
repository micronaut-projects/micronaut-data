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
import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.annotation.Fetch;
import io.micronaut.data.annotation.sql.Procedure;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.ConnectionStatus;
import io.micronaut.data.hibernate.conf.RequiresSyncHibernate;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
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
import io.micronaut.data.runtime.operations.internal.LocalExecutorService;
import io.micronaut.data.runtime.operations.internal.SynchronizedLazyValue;
import io.micronaut.transaction.TransactionOperations;
import jakarta.annotation.PreDestroy;
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
import org.hibernate.query.KeyedPage;
import org.hibernate.query.KeyedResultList;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;
import org.hibernate.query.QueryProducer;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.hibernate.query.KeyedPage.KeyInterpretation.KEY_OF_FIRST_ON_NEXT_PAGE;
import static org.hibernate.query.KeyedPage.KeyInterpretation.KEY_OF_LAST_ON_PREVIOUS_PAGE;
import static org.hibernate.query.Page.page;

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
    private final ConnectionOperations<Session> connectionOperations;
    private final TransactionOperations<Session> transactionOperations;
    private final SynchronizedLazyValue<ExecutorAsyncOperations> asyncOperations = new SynchronizedLazyValue<>();
    private final LocalExecutorService executorService;
    private final boolean uniqueResultOnFindOne;
    private final boolean persistOrMergeOnSave;
    private final Integer defaultFetchSize;

    /**
     * Default constructor.
     *
     * @param sessionFactory        The session factory
     * @param connectionOperations  The connection operations
     * @param transactionOperations The transaction operations
     * @param executorService       The executor service for I/O tasks to use
     * @param runtimeEntityRegistry The runtime entity registry
     * @param dataConversionService The data conversion service
     */
    public HibernateJpaOperations(
        @NonNull @Parameter SessionFactory sessionFactory,
        @NonNull @Parameter ConnectionOperations<Session> connectionOperations,
        @NonNull @Parameter TransactionOperations<Session> transactionOperations,
        @NonNull @Parameter JpaConfiguration jpaConfiguration,
        @Named("io") @Nullable ExecutorService executorService,
        RuntimeEntityRegistry runtimeEntityRegistry,
        DataConversionService dataConversionService) {
        super(runtimeEntityRegistry, dataConversionService);
        ArgumentUtils.requireNonNull("sessionFactory", sessionFactory);
        this.sessionFactory = sessionFactory;
        this.connectionOperations = connectionOperations;
        this.transactionOperations = transactionOperations;
        this.executorService = new LocalExecutorService(executorService);

        ConvertibleValuesMap<Object> convertibleValuesMap = new ConvertibleValuesMap<>(jpaConfiguration.getProperties());
        this.uniqueResultOnFindOne = convertibleValuesMap.get("uniqueResultOnFindOne", boolean.class, false);
        this.persistOrMergeOnSave = convertibleValuesMap.get("persistOrMergeOnSave", boolean.class, false);
        this.defaultFetchSize = convertibleValuesMap.get("defaultFetchSize", Integer.class)
            .orElse(convertibleValuesMap.get("default-fetch-size", Integer.class, 0));
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
    protected void setParameter(CommonQueryContract query, String parameterName, @Nullable Object value) {
        query.setParameter(parameterName, value);
    }

    @Override
    protected void setParameter(CommonQueryContract query, String parameterName, @Nullable Object value, Argument<?> argument) {
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
    protected void setParameter(CommonQueryContract query, int parameterIndex, @Nullable Object value) {
        query.setParameter(parameterIndex, value);
    }

    @Override
    protected void setParameter(CommonQueryContract query, int parameterIndex, @Nullable Object value, Argument<?> argument) {
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
    protected Query<?> createQuery(Session session, String query, @Nullable Class<?> resultType) {
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

    @Override
    public <T> void persist(@NonNull T entity) {
        executeWrite(session -> {
            session.persist(entity);
            return null;
        });
    }

    @Override
    public <T> void refresh(@NonNull T entity) {
        executeWrite(session -> {
            session.refresh(entity);
            return null;
        });
    }

    @Override
    public <T> void remove(@NonNull T entity) {
        executeWrite(session -> {
            session.remove(entity);
            return null;
        });
    }

    @Override
    public <T> void detach(@NonNull T entity) {
        executeWrite(session -> {
            session.detach(entity);
            return null;
        });
    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return executeRead(session -> {
            if (uniqueResultOnFindOne) {
                UniqueResultCollector<R> collector = new UniqueResultCollector<>();
                collectFindOne(session, preparedQuery, collector);
                return collector.result;
            } else {
                // limit does not work with native queries and does not produce expected
                // results with EntityGraph annotation and joins
                boolean limitOne = !preparedQuery.isNative() && !hasEntityGraph(preparedQuery.getAnnotationMetadata());
                FirstResultCollector<R> collector = new FirstResultCollector<>(limitOne);
                collectFindOne(session, preparedQuery, collector);
                return collector.result;
            }
        });
    }

    @Override
    public <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return executeRead(session -> {
            Limit limit = preparedQuery.getQueryLimit();
            if (!limit.isLimited()) {
                limit = Limit.of(1, 0);
            }
            FirstResultCollector<Boolean> collector = new FirstResultCollector<>(true);
            collectResults(session, preparedQuery.getQuery(), preparedQuery, limit, preparedQuery.getSort(), collector);
            return collector.result != null;
        });
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(@NonNull PagedQuery<T> pagedQuery) {
        return executeRead(session -> findPage(session, pagedQuery));
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(@NonNull PagedQuery<T> pagedQuery) {
        return executeRead(session -> {
            int fetchSize = pagedQuery.getAnnotationMetadata().intValue(Fetch.class).orElse(defaultFetchSize);
            StreamResultCollector<T> collector = new StreamResultCollector<>(fetchSize);
            collectPagedResults(session.getCriteriaBuilder(), session, pagedQuery, collector);
            return Objects.requireNonNull(collector.result);
        });
    }

    @Override
    public <R> Page<R> findPage(@NonNull PagedQuery<R> pagedQuery) {
        return executeRead(session -> findPage(session, pagedQuery));
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        return executeRead(session -> countOf(session, pagedQuery, Limit.UNLIMITED));
    }

    private <T, R> Page<R> findPage(Session session, PagedQuery<T> pagedQuery) {
        if (pagedQuery instanceof PreparedQuery<?, ?> pq) {
            PreparedQuery<T, R> preparedQuery = (PreparedQuery<T, R>) pq;
            Pageable pageable = preparedQuery.getPageable();
            if (pageable.getMode() != Pageable.Mode.OFFSET) {
                KeyedResultList<R> keyedResultList = getKeyedResult(preparedQuery, session, pageable);
                List<Pageable.Cursor> cursors =
                    keyedResultList.getKeyList()
                        .stream()
                        .map(key -> Pageable.Cursor.of(key.toArray()))
                        .collect(toList());
                return CursoredPage.of(keyedResultList.getResultList(), pageable, cursors, -1L);
            }
            ListResultCollector<R> resultCollector = new ListResultCollector<>();
            collectFindAll(session, preparedQuery, resultCollector);
            return Page.of(Objects.requireNonNull(resultCollector.result), pageable, -1L);
        }
        ListResultCollector<T> collector = new ListResultCollector<>();
        collectPagedResults(sessionFactory.getCriteriaBuilder(), session, pagedQuery, collector);
        return Page.of(Objects.requireNonNull((List<R>) collector.result), pagedQuery.getPageable(), -1L);
    }

    private <T> Long countOf(Session session, PagedQuery<T> pagedQuery, Limit limit) {
        SingleResultCollector<Long> collector = new SingleResultCollector<>();
        collectCountOf(sessionFactory.getCriteriaBuilder(), session, pagedQuery.getRootEntity(), limit, collector);
        return Objects.requireNonNull(collector.result);
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return executeRead(session -> findAll(preparedQuery, session));
    }

    private <T, R> List<R> findAll(PreparedQuery<T, R> preparedQuery, Session session) {
        Pageable pageable = preparedQuery.getPageable();
        if (pageable.getMode() != Pageable.Mode.OFFSET) {
            return getKeyedResult(preparedQuery, session, pageable).getResultList();
        }
        ListResultCollector<R> resultCollector = new ListResultCollector<>();
        collectFindAll(session, preparedQuery, resultCollector);
        return Objects.requireNonNull(resultCollector.result);
    }

    private <T, R> KeyedResultList<R> getKeyedResult(PreparedQuery<T, R> preparedQuery, Session session, Pageable pageable) {
        KeyedPage<T> keyedPage = getKeyedPage(preparedQuery, pageable);
        KeyedResultListCollector<R> resultCollector = new KeyedResultListCollector<>(keyedPage);
        collectResults(session, preparedQuery.getQuery(), preparedQuery, Limit.UNLIMITED, Sort.UNSORTED, resultCollector);
        return Objects.requireNonNull(resultCollector.result);
    }

    private static <T, R> KeyedPage<T> getKeyedPage(PreparedQuery<T, R> preparedQuery, Pageable pageable) {
        CursoredPageable cursoredPageable = (CursoredPageable) pageable;
        var unkeyedPage =
            page(pageable.getSize(), pageable.getNumber())
                .keyedBy(getOrders(preparedQuery.getSort(), preparedQuery.getRootEntity()));
        return cursoredPageable.cursor()
                .map(_cursor -> {
                    List<?> els = _cursor.elements();
                    var elements = (List<Comparable<?>>) els;
                    return switch (pageable.getMode()) {
                        case CURSOR_NEXT -> unkeyedPage.withKey(elements, KEY_OF_LAST_ON_PREVIOUS_PAGE);
                        case CURSOR_PREVIOUS -> unkeyedPage.withKey(elements, KEY_OF_FIRST_ON_NEXT_PAGE);
                        default -> unkeyedPage;
                    };
                }).orElse(unkeyedPage);
    }

    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
        return executeWrite(session -> {
            if (storedQuery != null) {
                return executeUpdate(operation, session, storedQuery);
            }
            T entity = operation.getEntity();
            if (persistOrMergeOnSave) {
                RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
                if (persistentEntity.hasIdentity() && persistentEntity.getIdentity().getProperty().get(entity) == null) {
                    session.persist(entity);
                } else {
                    entity = session.merge(entity);
                }
            } else {
                session.persist(entity);
            }
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
            if (persistOrMergeOnSave) {
                RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
                for (T entity : operation) {
                    if (persistentEntity.hasIdentity() && persistentEntity.getIdentity().getProperty().get(entity) == null) {
                        session.persist(entity);
                    } else {
                        session.merge(entity);
                    }
                }
            } else {
                for (T entity : operation) {
                    session.persist(entity);
                }
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
       return executeWrite(session -> {
           int deleted = 0;
           if (storedQuery != null) {
               for (T entity : operation) {
                   deleted += executeUpdate(session, storedQuery, operation.getInvocationContext(), entity);
               }
               if (flushIfNecessary(session, operation.getAnnotationMetadata())) {
                   for (T entity : operation) {
                       session.remove(entity);
                   }
               }
           } else {
               for (T entity : operation) {
                   session.remove(entity);
                   deleted++;
               }
           }
           return Optional.of(deleted);
       });
    }

    private <T> int executeUpdate(QueryProducer session, StoredQuery<T, ?> storedQuery, @Nullable InvocationContext<?, ?> invocationContext, T entity) {
        Objects.requireNonNull(invocationContext, "Invocation context is required!");
        MutationQuery query = session.createMutationQuery(storedQuery.getQuery());
        bindParameters(query, storedQuery, invocationContext, entity);
        return query.executeUpdate();
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        Optional<ConnectionStatus<Session>> connectionStatus = connectionOperations.findConnectionStatus();
        if (connectionStatus.isPresent()) {
            int fetchSize = preparedQuery.getAnnotationMetadata().intValue(Fetch.class).orElse(defaultFetchSize);
            StreamResultCollector<R> resultCollector = new StreamResultCollector<>(fetchSize, true);
            collectFindAll(connectionStatus.get().getConnection(), preparedQuery, resultCollector);
            return Objects.requireNonNull(resultCollector.result);
        }
        // No session is present, resolve the list completely
        return executeRead(session -> {
            ListResultCollector<R> resultCollector = new ListResultCollector<>();
            collectFindAll(session, preparedQuery, resultCollector);
            return Objects.requireNonNull(resultCollector.result).stream();
        });
    }

    private <R> R executeRead(Function<Session, R> callback) {
        return transactionOperations.executeRead(status -> callback.apply(status.getConnection()));
    }

    private <R> R executeWrite(Function<Session, R> callback) {
        return transactionOperations.executeWrite(status -> callback.apply(status.getConnection()));
    }

    @NonNull
    @Override
    public ExecutorAsyncOperations async() {
        return asyncOperations.get(() -> new ExecutorAsyncOperationsSupportingCriteria(
            this,
            this,
            executorService.get()
        ));
    }

    @NonNull
    @Override
    public ReactiveRepositoryOperations reactive() {
        if (dataConversionService instanceof DataConversionService asDataConversionService) {
            return new ExecutorReactiveOperationsSupportingCriteria((ExecutorAsyncOperationsSupportingCriteria) async(), asDataConversionService);
        }
        return new ExecutorReactiveOperationsSupportingCriteria((ExecutorAsyncOperationsSupportingCriteria) async(), null);
    }

    @PreDestroy
    public void close() {
        executorService.close();
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

    private final class KeyedResultListCollector<R> extends ResultCollector<R> {

        @Nullable
        private KeyedResultList<R> result;
        private final KeyedPage<?> keyedPage;

        private KeyedResultListCollector(KeyedPage<?> keyedPage) {
            this.keyedPage = keyedPage;
        }

        @Override
        protected void collectTuple(Query<?> query, Function<Tuple, R> fn) {
            KeyedResultList keyedResultList = ((Query) query).getKeyedResultList(keyedPage);
            result =  new KeyedResultList<>(
                keyedResultList.getResultList().stream().map(fn).toList(),
                keyedResultList.getKeyList(),
                keyedResultList.getPage(),
                keyedResultList.getNextPage(),
                keyedResultList.getPreviousPage()
            );
        }

        @Override
        protected void collect(Query<?> query) {
            result = ((Query) query).getKeyedResultList(keyedPage);
        }
    }

    private final class ListResultCollector<R> extends ResultCollector<R> {

        @Nullable
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

        @Nullable
        private Stream<R> result;
        private final int fetchSize;
        private final boolean readOnly;

        private StreamResultCollector(int fetchSize) {
            this.fetchSize = fetchSize;
            this.readOnly = true;
        }

        private StreamResultCollector(int fetchSize, boolean readOnly) {
            this.fetchSize = fetchSize;
            this.readOnly = readOnly;
        }

        @Override
        protected void collectTuple(Query<?> query, Function<Tuple, R> fn) {
            if (fetchSize > 0) {
                try {
                    query.setFetchSize(fetchSize);
                } catch (Throwable ignored) {
                    // Some drivers may not support fetchSize; ignore
                }
            }
            if (readOnly) {
                try {
                    query.setReadOnly(true);
                } catch (Throwable ignored) {
                }
            }
            Stream<Tuple> base = (Stream<Tuple>) query.getResultStream();
            Stream<R> mapped = base.map(fn);
            result = mapped;
        }

        @Override
        protected void collect(Query<?> query) {
            if (fetchSize > 0) {
                try {
                    query.setFetchSize(fetchSize);
                } catch (Throwable ignored) {
                    // Some drivers may not support fetchSize; ignore
                }
            }
            if (readOnly) {
                try {
                    query.setReadOnly(true);
                } catch (Throwable ignored) {
                }
            }
            Stream<R> s = (Stream<R>) query.getResultStream();
            result = s;
        }
    }

    private final class SingleResultCollector<R> extends ResultCollector<R> {

        @Nullable
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

    private final class UniqueResultCollector<R> extends ResultCollector<R> {

        @Nullable
        private R result;

        @Override
        protected void collectTuple(Query<?> query, Function<Tuple, R> fn) {
            Tuple tuple = (Tuple) query.uniqueResult();
            if (tuple != null) {
                this.result = fn.apply(tuple);
            }
        }

        @Override
        protected void collect(Query<?> query) {
            result = (R) query.uniqueResult();
        }
    }

    private final class FirstResultCollector<R> extends ResultCollector<R> {

        private final boolean limitOne;
        @Nullable
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

        @Nullable
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
