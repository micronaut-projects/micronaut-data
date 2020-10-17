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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.jpa.annotation.EntityGraph;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.model.runtime.*;
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
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;

import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.io.Serializable;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private ExecutorAsyncOperations asyncOperations;
    private ExecutorService executorService;

    /**
     * Default constructor.
     *
     * @param sessionFactory        The session factory
     * @param transactionOperations The transaction operations
     * @param executorService       The executor service for I/O tasks to use
     */
    protected HibernateJpaOperations(
            @NonNull SessionFactory sessionFactory,
            @NonNull @Parameter TransactionOperations<Connection> transactionOperations,
            @Named("io") @Nullable ExecutorService executorService) {
        ArgumentUtils.requireNonNull("sessionFactory", sessionFactory);
        this.sessionFactory = sessionFactory;
        this.transactionOperations = transactionOperations;
        this.executorService = executorService;
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
                    q = currentSession
                            .createNativeQuery(query, Tuple.class);

                } else {
                    q = currentSession
                            .createQuery(query, Tuple.class);
                }
                bindParameters(q, preparedQuery, query);
                bindQueryHints(q, preparedQuery, currentSession);
                q.setMaxResults(1);
                return q.uniqueResultOptional()
                        .map(tuple -> ((BeanIntrospectionMapper<Tuple, R>) Tuple::get).map(tuple, resultType))
                        .orElse(null);
            } else {
                Query<R> q;

                if (preparedQuery.isNative()) {
                    if (DataType.ENTITY.equals(preparedQuery.getResultDataType())) {
                        q = currentSession
                                .createNativeQuery(query, resultType);
                    } else {
                        q = currentSession
                                .createNativeQuery(query);
                    }
                } else {
                    q = currentSession
                            .createQuery(query, resultType);
                }
                bindParameters(q, preparedQuery, query);
                bindQueryHints(q, preparedQuery, currentSession);
                q.setMaxResults(1);
                return q.uniqueResultOptional().orElse(null);
            }
        });
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
                String[] indexedParameterPaths = preparedQuery.getIndexedParameterPaths();
                String propertyPath = i < indexedParameterPaths.length ? indexedParameterPaths[i] : null;
                if (propertyPath != null) {
                    String lastUpdatedProperty = preparedQuery.getLastUpdatedProperty();
                    if (lastUpdatedProperty != null && lastUpdatedProperty.equals(propertyPath)) {
                        Class<?> lastUpdatedType = preparedQuery.getLastUpdatedType();
                        if (lastUpdatedType == null) {
                            throw new IllegalStateException("Could not establish last updated time for entity: " + preparedQuery.getRootEntity());
                        }
                        Object timestamp = ConversionService.SHARED.convert(OffsetDateTime.now(), lastUpdatedType).orElse(null);
                        if (timestamp == null) {
                            throw new IllegalStateException("Unsupported date type: " + lastUpdatedType);
                        }
                        value = timestamp;
                    } else {
                        int j = propertyPath.indexOf('.');
                        if (j > -1) {
                            String subProp = propertyPath.substring(j + 1);
                            value = parameterArray[Integer.valueOf(propertyPath.substring(0, j))];
                            value = BeanWrapper.getWrapper(value).getRequiredProperty(subProp, Argument.OBJECT_ARGUMENT);
                        } else {
                            throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
                        }
                    }
                } else {
                    throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at name: " + parameterName);
                }
            }
            q.setParameter(parameterName, value);
        }
    }

    @Override
    public <T, R> boolean exists(@NonNull PreparedQuery<T, R> preparedQuery) {
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
                    q = entityManager
                            .createNativeQuery(queryStr, Tuple.class);

                } else {
                    q = entityManager
                            .createQuery(queryStr, Tuple.class);
                }

                bindPreparedQuery(q, preparedQuery, entityManager, queryStr);
                return q.stream()
                        .map(tuple -> ((BeanIntrospectionMapper<Tuple, R>) Tuple::get).map(tuple, preparedQuery.getResultType()))
                        .collect(Collectors.toList());
            } else {
                Class<R> wrapperType = ReflectionUtils.getWrapperType(preparedQuery.getResultType());
                Query<R> q;
                if (preparedQuery.isNative()) {
                    q = entityManager
                            .createNativeQuery(queryStr, wrapperType);

                } else {
                    q = entityManager
                            .createQuery(queryStr, wrapperType);
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
                        String[] paths = (String[]) value;
                        if (ArrayUtils.isNotEmpty(paths)) {
                            RootGraph<T> entityGraph = session.createEntityGraph(preparedQuery.getRootEntity());
                            entityGraph.addAttributeNodes(paths);
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
        return transactionOperations.executeWrite(status -> {
            T entity = operation.getEntity();
            EntityManager session = sessionFactory.getCurrentSession();
            entity = session.merge(entity);
            flushIfNecessary(session, operation.getAnnotationMetadata());
            return entity;
        });
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public <T> Iterable<T> persistAll(@NonNull BatchOperation<T> operation) {
        return transactionOperations.executeWrite(status -> {
            if (operation != null) {
                EntityManager entityManager = sessionFactory.getCurrentSession();
                for (T entity : operation) {
                    entityManager.persist(entity);
                }
                AnnotationMetadata annotationMetadata =
                        operation.getAnnotationMetadata();
                flushIfNecessary(entityManager, annotationMetadata);
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
        //noinspection ConstantConditions
        return transactionOperations.executeWrite(status -> {
            String query = preparedQuery.getQuery();
            Query<?> q = getCurrentSession().createQuery(query);
            bindParameters(q, preparedQuery, query);
            return Optional.of(q.executeUpdate());
        });
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull BatchOperation<T> operation) {
        if (operation.all()) {
            return transactionOperations.executeWrite(status -> {
                Class<T> entityType = operation.getRootEntity();
                Session session = getCurrentSession();
                CriteriaDelete<T> criteriaDelete = session.getCriteriaBuilder().createCriteriaDelete(entityType);
                criteriaDelete.from(entityType);
                Query query = session.createQuery(
                        criteriaDelete
                );
                return Optional.of(query.executeUpdate());
            });
        } else {
            Integer result = transactionOperations.executeWrite(status -> {
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
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        //noinspection ConstantConditions
        return transactionOperations.executeRead(status -> {
            String query = preparedQuery.getQuery();
            Map<String, Object> parameterValues = preparedQuery.getParameterValues();
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
