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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.mapper.BeanIntrospectionMapper;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.jdbc.spring.HibernatePresenceCondition;
import io.micronaut.transaction.TransactionOperations;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.graph.RootGraph;
import org.hibernate.graph.SubGraph;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.hibernate.type.Type;

import jakarta.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.Tuple;
import javax.persistence.TupleElement;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.io.Serializable;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the {@link JpaRepositoryOperations} interface for Hibernate.
 *
 * @author graemerocher
 * @since 1.0
 */
@EachBean(SessionFactory.class)
@TypeHint(HibernatePresenceCondition.class)
public class HibernateJpaOperations implements JpaRepositoryOperations, AsyncCapableRepository, ReactiveCapableRepository {

    private static final String ENTITY_GRAPH_FETCH = "javax.persistence.fetchgraph";
    private static final String ENTITY_GRAPH_LOAD = "javax.persistence.loadgraph";
    private static final JpaQueryBuilder QUERY_BUILDER = new JpaQueryBuilder();
    private final SessionFactory sessionFactory;
    private final TransactionOperations<Connection> transactionOperations;
    private final RuntimeEntityRegistry runtimeEntityRegistry;
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
    protected HibernateJpaOperations(
            @NonNull SessionFactory sessionFactory,
            @NonNull @Parameter TransactionOperations<Connection> transactionOperations,
            @Named("io") @Nullable ExecutorService executorService,
            RuntimeEntityRegistry runtimeEntityRegistry) {
        ArgumentUtils.requireNonNull("sessionFactory", sessionFactory);
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.sessionFactory = sessionFactory;
        this.transactionOperations = transactionOperations;
        this.executorService = executorService;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return runtimeEntityRegistry.getApplicationContext();
    }

    @NonNull
    @Override
    public Map<String, Object> getQueryHints(@NonNull StoredQuery<?, ?> storedQuery) {
        AnnotationMetadata annotationMetadata = storedQuery.getAnnotationMetadata();
        if (annotationMetadata.hasAnnotation(EntityGraph.class)) {
            String hint = annotationMetadata.stringValue(EntityGraph.class, "hint").orElse(ENTITY_GRAPH_FETCH);
            String[] paths = annotationMetadata.stringValues(EntityGraph.class, "attributePaths");
            if (ArrayUtils.isNotEmpty(paths)) {
                return Collections.singletonMap(hint, paths);
            }
        }
        return Collections.emptyMap();
    }

    @Nullable
    @Override
    public <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return transactionOperations.executeRead(status -> {
            final Session session = sessionFactory.getCurrentSession();
            return session.byId(type).load(id);
        });
    }

    @NonNull
    @Override
    public <T> T load(@NonNull Class<T> type, @NonNull Serializable id) {
        return transactionOperations.executeRead(status -> {
            final Session session = sessionFactory.getCurrentSession();
            return session.load(type, id);
        });
    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return transactionOperations.executeRead(status -> {
            Class<R> resultType = preparedQuery.getResultType();
            String query = preparedQuery.getQuery();

            Session currentSession = sessionFactory.getCurrentSession();
            if (preparedQuery.isDtoProjection()) {
                Query<Tuple> q;
                if (preparedQuery.isNative()) {
                    q = currentSession.createNativeQuery(query, Tuple.class);
                } else if (query.toLowerCase(Locale.ENGLISH).startsWith("select new ")) {
                    Query<R> dtoQuery = currentSession.createQuery(query, resultType);
                    bindParameters(dtoQuery, preparedQuery, query);
                    bindQueryHints(dtoQuery, preparedQuery, currentSession);
                    return dtoQuery.uniqueResult();
                } else {
                    q = currentSession.createQuery(query, Tuple.class);
                }
                bindParameters(q, preparedQuery, query);
                bindQueryHints(q, preparedQuery, currentSession);

                Tuple tuple = first(q.list().iterator());
                if (tuple != null) {
                    return ((BeanIntrospectionMapper<Tuple, R>) Tuple::get).map(tuple, resultType);
                }
                return null;
            } else {
                Query<R> q;
                if (preparedQuery.isNative()) {
                    if (DataType.ENTITY.equals(preparedQuery.getResultDataType())) {
                        q = currentSession.createNativeQuery(query, resultType);
                    } else {
                        q = currentSession.createNativeQuery(query);
                    }
                } else {
                    q = currentSession.createQuery(query, resultType);
                }
                bindParameters(q, preparedQuery, query);
                bindQueryHints(q, preparedQuery, currentSession);

                return first(q.list().iterator());
            }
        });
    }

    private <T> T first(Iterator<T> iterator) {
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }

    private <T, R> void bindParameters(Query<?> q, @NonNull PreparedQuery<T, R> preparedQuery, String query) {
        String[] parameterNames = preparedQuery.getParameterNames();
        Object[] parameterArray = preparedQuery.getParameterArray();
        int[] indexedParameterBinding = preparedQuery.getIndexedParameterBinding();
        for (int i = 0; i < parameterNames.length; i++) {
            String parameterName = parameterNames[i];
            int parameterIndex = indexedParameterBinding[i];
            Object value;
            if (parameterIndex > -1) {
                value = parameterArray[parameterIndex];
            } else {
                String[] indexedParameterAutoPopulatedPropertyPaths = preparedQuery.getIndexedParameterAutoPopulatedPropertyPaths();
                String[] indexedParameterPaths = preparedQuery.getIndexedParameterPaths();
                String propertyPath = i < indexedParameterPaths.length ? indexedParameterPaths[i] : null;
                String autoPopulatedPropertyPath = indexedParameterAutoPopulatedPropertyPaths[i];
                if (autoPopulatedPropertyPath != null) {
                    RuntimePersistentEntity<T> persistentEntity = getEntity(preparedQuery.getRootEntity());
                    RuntimePersistentProperty<T> persistentProperty = persistentEntity.getPropertyByName(autoPopulatedPropertyPath);
                    if (persistentProperty == null) {
                        throw new IllegalStateException("Cannot find auto populated property: " + autoPopulatedPropertyPath);
                    }
                    Object previousValue = null;
                    if (propertyPath != null) {
                        previousValue = resolveQueryParameterByPath(query, i, parameterArray, propertyPath);
                    }
                    value = runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
                } else if (propertyPath != null) {
                    value = resolveQueryParameterByPath(query, i, parameterArray, propertyPath);
                } else {
                    throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at name: " + parameterName);
                }
            }
            if (preparedQuery.isNative()) {
                Argument<?> argument = preparedQuery.getArguments()[parameterIndex];
                Class<?> argumentType = argument.getType();
                if (Collection.class.isAssignableFrom(argumentType)) {
                    Type valueType = sessionFactory.getTypeHelper().heuristicType(argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT).getType().getName());
                    if (valueType != null) {
                        q.setParameterList(parameterName, value == null ? Collections.emptyList() : (Collection<?>) value, valueType);
                        return;
                    }
                } else if (Object[].class.isAssignableFrom(argumentType)) {
                    q.setParameterList(parameterName, value == null ? ArrayUtils.EMPTY_OBJECT_ARRAY : (Object[]) value);
                    return;
                } else if (value == null) {
                    Type type = sessionFactory.getTypeHelper().heuristicType(argumentType.getName());
                    if (type != null) {
                        q.setParameter(parameterName, null, type);
                        return;
                    }
                }
            }
            q.setParameter(parameterName, value);
        }
    }

    private Object resolveQueryParameterByPath(String query, int i, Object[] queryParameters, String propertyPath) {
        int j = propertyPath.indexOf('.');
        if (j > -1) {
            String subProp = propertyPath.substring(j + 1);
            Object indexedValue = queryParameters[Integer.parseInt(propertyPath.substring(0, j))];
            return BeanWrapper.getWrapper(indexedValue).getRequiredProperty(subProp, Argument.OBJECT_ARGUMENT);
        } else {
            throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
        }
    }

    @Override
    public <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return findOne(preparedQuery) != null;
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(@NonNull PagedQuery<T> query) {
        //noinspection ConstantConditions
        return transactionOperations.executeRead(status -> {
            Session session = getCurrentSession();
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            Query<T> q = buildCriteriaQuery(session, query.getRootEntity(), criteriaBuilder, query.getPageable());
            bindQueryHints(q, query, session);
            return q.list();
        });
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        //noinspection ConstantConditions
        return transactionOperations.executeRead(status -> {
            Session session = getCurrentSession();
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
            Root<T> root = query.from(pagedQuery.getRootEntity());
            query = query.select(criteriaBuilder.count(root));
            Query<Long> q = session.createQuery(
                    query
            );
            Pageable pageable = pagedQuery.getPageable();
            bindCriteriaSort(query, root, criteriaBuilder, pageable);
            bindPageable(q, pageable);
            bindQueryHints(q, pagedQuery, session);

            return q.getSingleResult();
        });
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        //noinspection ConstantConditions
        return transactionOperations.executeRead(status -> {
            Session entityManager = sessionFactory.getCurrentSession();
            String queryStr = preparedQuery.getQuery();
            Pageable pageable = preparedQuery.getPageable();
            if (pageable != Pageable.UNPAGED) {
                Sort sort = pageable.getSort();
                if (sort.isSorted()) {
                    queryStr += QUERY_BUILDER.buildOrderBy(getEntity(preparedQuery.getRootEntity()), sort).getQuery();
                }
            }
            if (preparedQuery.isDtoProjection()) {
                Query<Tuple> q;
                if (preparedQuery.isNative()) {
                    q = entityManager.createNativeQuery(queryStr, Tuple.class);
                } else if (queryStr.toLowerCase(Locale.ENGLISH).startsWith("select new ")) {
                    Class<R> wrapperType = ReflectionUtils.getWrapperType(preparedQuery.getResultType());
                    Query<R> query = entityManager.createQuery(queryStr, wrapperType);
                    bindPreparedQuery(query, preparedQuery, entityManager, queryStr);
                    return query.list();
                } else {
                    q = entityManager.createQuery(queryStr, Tuple.class);
                }
                bindPreparedQuery(q, preparedQuery, entityManager, queryStr);
                return q.stream()
                        .map(tuple -> {
                            Set<String> properties = tuple.getElements().stream().map(TupleElement::getAlias).collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
                            return ((BeanIntrospectionMapper<Tuple, R>) (tuple1, alias) -> {
                                if (!properties.contains(alias)) {
                                    return null;
                                }
                                return tuple1.get(alias);
                            }).map(tuple, preparedQuery.getResultType());
                        })
                        .collect(Collectors.toList());
            } else {
                Class<R> wrapperType = ReflectionUtils.getWrapperType(preparedQuery.getResultType());
                Query<R> q;
                if (preparedQuery.isNative()) {
                    q = entityManager.createNativeQuery(queryStr, wrapperType);

                } else {
                    q = entityManager.createQuery(queryStr, wrapperType);
                }
                bindPreparedQuery(q, preparedQuery, entityManager, queryStr);
                return q.list();
            }
        });
    }

    private <T, R> void bindPreparedQuery(Query<?> q, @NonNull PreparedQuery<T, R> preparedQuery, Session currentSession, String query) {
        bindParameters(q, preparedQuery, query);
        bindPageable(q, preparedQuery.getPageable());
        bindQueryHints(q, preparedQuery, currentSession);
    }

    private <T> void bindQueryHints(Query<?> q, @NonNull PagedQuery<T> preparedQuery, @NonNull Session session) {
        Map<String, Object> queryHints = preparedQuery.getQueryHints();
        if (CollectionUtils.isNotEmpty(queryHints)) {
            for (Map.Entry<String, Object> entry : queryHints.entrySet()) {
                String hintName = entry.getKey();
                Object value = entry.getValue();
                if (ENTITY_GRAPH_FETCH.equals(hintName) || ENTITY_GRAPH_LOAD.equals(hintName)) {
                    String graphName = preparedQuery.getAnnotationMetadata().stringValue(EntityGraph.class).orElse(null);
                    if (graphName != null) {
                        RootGraph<?> entityGraph = session.getEntityGraph(graphName);
                        q.setHint(hintName, entityGraph);
                    } else if (value instanceof String[]) {
                        String[] pathsDefinitions = (String[]) value;
                        if (ArrayUtils.isNotEmpty(pathsDefinitions)) {
                            RootGraph<T> entityGraph = session.createEntityGraph(preparedQuery.getRootEntity());
                            for (String pathsDefinition : pathsDefinitions) {
                                String[] paths = pathsDefinition.split("\\.");
                                if (paths.length == 1) {
                                    entityGraph.addAttributeNode(paths[0]);
                                } else {
                                    SubGraph<T> subGraph = null;
                                    for (int i = 0; i < paths.length; i++) {
                                        String path = paths[i];
                                        if (subGraph == null) {
                                            if (i + 1 == paths.length) {
                                                entityGraph.addAttributeNode(path);
                                            } else {
                                                subGraph = entityGraph.addSubGraph(path);
                                            }
                                        } else {
                                            if (i + 1 == paths.length) {
                                                subGraph.addAttributeNode(path);
                                            } else {
                                                subGraph = subGraph.addSubGraph(path);
                                            }
                                        }
                                    }
                                }
                            }
                            q.setHint(hintName, entityGraph);
                        }
                    }
                } else {
                    q.setHint(hintName, value);
                }
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        return transactionOperations.executeWrite(status -> {
            T entity = operation.getEntity();

            EntityManager entityManager = sessionFactory.getCurrentSession();
            entityManager.persist(entity);
            flushIfNecessary(
                    entityManager,
                    operation.getAnnotationMetadata()
            );
            return entity;
        });
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        String queryString = annotationMetadata.stringValue(io.micronaut.data.annotation.Query.class).orElse(null);
        return transactionOperations.executeWrite(status -> {
            if (queryString != null) {
                executeEntityUpdate(annotationMetadata, queryString, operation.getEntity());
                return operation.getEntity();
            }
            T entity = operation.getEntity();
            EntityManager session = sessionFactory.getCurrentSession();
            entity = session.merge(entity);
            flushIfNecessary(session, operation.getAnnotationMetadata());
            return entity;
        });
    }

    @NonNull
    @Override
    public <T> Iterable<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        String queryString = annotationMetadata.stringValue(io.micronaut.data.annotation.Query.class).orElse(null);
        return transactionOperations.executeWrite(status -> {
            if (queryString != null) {
                for (T entity : operation) {
                    executeEntityUpdate(annotationMetadata, queryString, entity);
                }
                return operation;
            }
            EntityManager entityManager = sessionFactory.getCurrentSession();
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

    private void flushIfNecessary(
            EntityManager entityManager,
            AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasAnnotation(QueryHint.class)) {
            FlushModeType flushModeType = getFlushModeType(annotationMetadata);
            if (flushModeType == FlushModeType.AUTO) {
                entityManager.flush();
            }
        }
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return transactionOperations.executeWrite(status -> {
            String query = preparedQuery.getQuery();
            Query<?> q = getCurrentSession().createQuery(query);
            bindParameters(q, preparedQuery, query);
            return Optional.of(q.executeUpdate());
        });
    }

    @Override
    public <T> int delete(@NonNull DeleteOperation<T> operation) {
        AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        String queryString = annotationMetadata.stringValue(io.micronaut.data.annotation.Query.class).orElse(null);
        return transactionOperations.executeWrite(status -> {
            if (queryString != null) {
                return executeEntityUpdate(annotationMetadata, queryString, operation.getEntity());
            }
            getCurrentSession().remove(operation.getEntity());
            return 1;
        });
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        String queryString = annotationMetadata.stringValue(io.micronaut.data.annotation.Query.class).orElse(null);
        Integer result = transactionOperations.executeWrite(status -> {
            if (queryString != null) {
                int i = 0;
                for (T entity : operation) {
                    i += executeEntityUpdate(annotationMetadata, queryString, entity);
                }
                return i;
            }
            int i = 0;
            Session session = getCurrentSession();
            for (T entity : operation) {
                session.remove(entity);
                i++;
            }
            return i;
        });
        return Optional.ofNullable(result);
    }

    private int executeEntityUpdate(AnnotationMetadata annotationMetadata, String queryString, Object entity) {
        String[] parameters = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
        Query query = getCurrentSession().createQuery(queryString);
        for (String parameter : parameters) {
            query.setParameter(parameter, getParameterValue(parameter, entity));
        }
        return query.executeUpdate();
    }

    private Object getParameterValue(String propertyName, Object value) {
        for (String property : propertyName.split("\\.")) {
            value = BeanWrapper.getWrapper(value).getRequiredProperty(property, Argument.OBJECT_ARGUMENT);
        }
        return value;
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        //noinspection ConstantConditions
        return transactionOperations.executeRead(status -> {
            String query = preparedQuery.getQuery();
            Pageable pageable = preparedQuery.getPageable();
            Session currentSession = getCurrentSession();
            Class<R> resultType = preparedQuery.getResultType();
            boolean isNativeQuery = preparedQuery.isNative();
            if (preparedQuery.isDtoProjection()) {
                Query<Tuple> q;

                if (isNativeQuery) {
                    q = currentSession
                            .createNativeQuery(query, Tuple.class);
                } else {
                    q = currentSession
                            .createQuery(query, Tuple.class);
                }
                bindParameters(q, preparedQuery, query);
                bindPageable(q, pageable);
                return q.stream()
                        .map(tuple -> ((BeanIntrospectionMapper<Tuple, R>) Tuple::get).map(tuple, resultType));

            } else {

                Query<R> q;
                @SuppressWarnings("unchecked")
                Class<R> wrapperType = ReflectionUtils.getWrapperType(resultType);
                if (isNativeQuery) {
                    Class<T> rootEntity = preparedQuery.getRootEntity();
                    if (wrapperType != rootEntity) {
                        NativeQuery<Tuple> nativeQuery = currentSession.createNativeQuery(query, Tuple.class);
                        bindParameters(nativeQuery, preparedQuery, query);
                        bindPageable(nativeQuery, pageable);
                        return nativeQuery.stream()
                                .map(tuple -> {
                                    Object o = tuple.get(0);
                                    if (wrapperType.isInstance(o)) {
                                        return (R) o;
                                    } else {
                                        return ConversionService.SHARED.convertRequired(
                                                o,
                                                wrapperType
                                        );
                                    }
                                });
                    } else {
                        q = currentSession.createNativeQuery(query, wrapperType);
                    }
                } else {
                    q = currentSession.createQuery(query, wrapperType);
                }
                bindParameters(q, preparedQuery, query);
                bindPageable(q, pageable);

                return q.stream();
            }
        });
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(@NonNull PagedQuery<T> pagedQuery) {
        Session session = getCurrentSession();
        Class<T> entity = pagedQuery.getRootEntity();
        CriteriaQuery<T> query = session.getCriteriaBuilder().createQuery(entity);
        query.from(entity);
        Query<T> q = session.createQuery(
                query
        );
        bindPageable(q, pagedQuery.getPageable());

        return q.stream();
    }

    @Override
    public <R> Page<R> findPage(@NonNull PagedQuery<R> query) {
        //noinspection ConstantConditions
        return transactionOperations.executeRead(status -> {
            Session session = getCurrentSession();
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            Class<R> entity = query.getRootEntity();
            Pageable pageable = query.getPageable();
            Query<R> q = buildCriteriaQuery(session, entity, criteriaBuilder, pageable);
            List<R> resultList = q.list();
            CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
            countQuery.select(criteriaBuilder.count(countQuery.from(entity)));
            Long total = session.createQuery(countQuery).getSingleResult();
            return Page.of(resultList, pageable, total);
        });
    }

    private Session getCurrentSession() {
        return sessionFactory.getCurrentSession();
    }

    private FlushModeType getFlushModeType(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.getAnnotationValuesByType(QueryHint.class)
                .stream()
                .filter(av -> FlushModeType.class.getName().equals(av.stringValue("name").orElse(null)))
                .map(av -> av.enumValue("value", FlushModeType.class))
                .findFirst()
                .orElse(Optional.empty()).orElse(null);
    }

    private <T> Query<T> buildCriteriaQuery(Session session, @NonNull Class<T> rootEntity, CriteriaBuilder criteriaBuilder, @NonNull Pageable pageable) {
        CriteriaQuery<T> query = criteriaBuilder.createQuery(rootEntity);
        Root<T> root = query.from(rootEntity);
        bindCriteriaSort(query, root, criteriaBuilder, pageable);
        Query<T> q = session.createQuery(
                query
        );
        bindPageable(q, pageable);
        return q;
    }

    private <T> void bindPageable(Query<T> q, @NonNull Pageable pageable) {
        if (pageable == Pageable.UNPAGED) {
            // no pagination
            return;
        }

        int max = pageable.getSize();
        if (max > 0) {
            q.setMaxResults(max);
        }
        long offset = pageable.getOffset();
        if (offset > 0) {
            q.setFirstResult((int) offset);
        }
    }

    private <T> void bindCriteriaSort(CriteriaQuery<T> criteriaQuery, Root<?> root, CriteriaBuilder builder, @NonNull Sort sort) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort.getOrderBy()) {
            Path<String> path = root.get(order.getProperty());
            Expression expression = order.isIgnoreCase() ? builder.lower(path) : path;
            switch (order.getDirection()) {

                case DESC:
                    orders.add(builder.desc(expression));
                    continue;
                default:
                case ASC:
                    orders.add(builder.asc(expression));
            }
        }
        criteriaQuery.orderBy(orders);
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
        return new ExecutorReactiveOperations(async());
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

}
