/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.mapper.IntrospectedDataMapper;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.Sort;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.Tuple;
import javax.persistence.criteria.*;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the {@link JpaRepositoryOperations} interface for Hibernate.
 *
 * @author graemerocher
 * @since 1.0
 */
public class HibernateJpaOperations implements JpaRepositoryOperations, AsyncCapableRepository, ReactiveCapableRepository {

    private final SessionFactory sessionFactory;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final ExecutorAsyncOperations asyncOperations;

    /**
     * Default constructor.
     *
     * @param sessionFactory  The session factory
     * @param executorService The executor service for I/O tasks to use
     */
    protected HibernateJpaOperations(
            @NonNull SessionFactory sessionFactory,
            @NonNull ExecutorService executorService) {
        ArgumentUtils.requireNonNull("sessionFactory", sessionFactory);
        ArgumentUtils.requireNonNull("executorService", executorService);
        this.sessionFactory = sessionFactory;
        HibernateTransactionManager transactionManager = new HibernateTransactionManager(sessionFactory);
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setReadOnly(true);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager, def);
        this.asyncOperations = new ExecutorAsyncOperations(
                this,
                executorService
        );
    }

    @Nullable
    @Override
    public <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return readTransactionTemplate.execute(status ->
                getCurrentSession().byId(type).load(id)
        );
    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return readTransactionTemplate.execute(status -> {
            Class<R> resultType = preparedQuery.getResultType();
            String query = preparedQuery.getQuery();
            Map<String, Object> parameters = preparedQuery.getParameterValues();
            Session currentSession = getCurrentSession();
            if (preparedQuery.isDtoProjection()) {
                Query<Tuple> q;
                if (preparedQuery.isNative()) {
                    q = currentSession
                            .createNativeQuery(query, Tuple.class);

                } else {
                    q = currentSession
                            .createQuery(query, Tuple.class);
                }
                bindParameters(q, parameters);
                bindQueryHints(q, preparedQuery);
                q.setMaxResults(1);
                return q.uniqueResultOptional()
                        .map(tuple -> ((IntrospectedDataMapper<Tuple>) Tuple::get)
                                .map(tuple, resultType))
                        .orElse(null);
            } else {
                Class<R> wrapperType = ReflectionUtils.getWrapperType(resultType);
                Query<R> q;

                if (preparedQuery.isNative()) {
                    q = currentSession
                            .createNativeQuery(query, wrapperType);
                } else {
                    q = currentSession
                            .createQuery(query, wrapperType);
                }
                bindParameters(q, parameters);
                bindQueryHints(q, preparedQuery);
                q.setMaxResults(1);
                return q.uniqueResultOptional().orElse(null);
            }
        });
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(@NonNull PagedQuery<T> query) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Session session = getCurrentSession();
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            Query<T> q = buildCriteriaQuery(session, query.getRootEntity(), criteriaBuilder, query.getPageable());

            return q.list();
        });
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
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

            return q.getSingleResult();
        });
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Session currentSession = getCurrentSession();
            if (preparedQuery.isDtoProjection()) {
                Query<Tuple> q;

                if (preparedQuery.isNative()) {
                    q = currentSession
                            .createNativeQuery(preparedQuery.getQuery(), Tuple.class);

                } else {
                    q = currentSession
                            .createQuery(preparedQuery.getQuery(), Tuple.class);
                }

                bindPreparedQuery(q, preparedQuery);
                return q.stream()
                        .map(tuple -> ((IntrospectedDataMapper<Tuple>) Tuple::get)
                                .map(tuple, preparedQuery.getResultType()))
                        .collect(Collectors.toList());
            } else {
                Class<R> wrapperType = ReflectionUtils.getWrapperType(preparedQuery.getResultType());
                Query<R> q;
                if (preparedQuery.isNative()) {
                    q = currentSession
                            .createNativeQuery(preparedQuery.getQuery(), wrapperType);

                } else {
                    q = currentSession
                            .createQuery(preparedQuery.getQuery(), wrapperType);
                }
                bindPreparedQuery(q, preparedQuery);
                return q.list();
            }
        });
    }

    private <T, R> void bindPreparedQuery(Query<?> q, @NonNull PreparedQuery<T, R> preparedQuery) {
        bindParameters(q, preparedQuery.getParameterValues());
        bindPageable(q, preparedQuery.getPageable());
        bindQueryHints(q, preparedQuery);
    }

    private <T, R> void bindQueryHints(Query<?> q, @NonNull PreparedQuery<T, R> preparedQuery) {
        Map<String, String> queryHints = preparedQuery.getQueryHints();
        if (CollectionUtils.isNotEmpty(queryHints)) {
            for (Map.Entry<String, String> entry : queryHints.entrySet()) {
                q.setHint(entry.getKey(), entry.getValue());
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        return writeTransactionTemplate.execute(status -> {
            T entity = operation.getEntity();
            Session session = getCurrentSession();
            session.persist(entity);
            flushIfNecessary(session, operation.getAnnotationMetadata());
            return entity;
        });
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public <T> Iterable<T> persistAll(@NonNull BatchOperation<T> operation) {
        return writeTransactionTemplate.execute(status -> {
            if (operation != null) {
                Session session = getCurrentSession();
                for (T entity : operation) {
                    session.persist(entity);
                }
                AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
                flushIfNecessary(session, annotationMetadata);
                return operation;
            } else {
                return Collections.emptyList();
            }
        });
    }

    private void flushIfNecessary(Session session, AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasAnnotation(QueryHint.class)) {
            FlushModeType flushModeType = getFlushModeType(annotationMetadata);
            if (flushModeType == FlushModeType.AUTO) {
                session.flush();
            }
        }
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        //noinspection ConstantConditions
        return writeTransactionTemplate.execute(status -> {
            Query<?> q = getCurrentSession().createQuery(preparedQuery.getQuery());
            bindParameters(q, preparedQuery.getParameterValues());
            return Optional.of(q.executeUpdate());
        });
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull BatchOperation<T> operation) {
        if (operation.all()) {
            return writeTransactionTemplate.execute(status -> {
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
            Integer result = writeTransactionTemplate.execute(status -> {
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
        return readTransactionTemplate.execute(status -> {
            String query = preparedQuery.getQuery();
            Map<String, Object> parameterValues = preparedQuery.getParameterValues();
            Pageable pageable = preparedQuery.getPageable();
            Session currentSession = getCurrentSession();
            if (preparedQuery.isDtoProjection()) {
                Query<Tuple> q;

                if (preparedQuery.isNative()) {
                    q = currentSession
                            .createNativeQuery(query, Tuple.class);
                } else {
                    q = currentSession
                            .createQuery(query, Tuple.class);
                }
                bindParameters(q, parameterValues);
                bindPageable(q, pageable);
                return q.stream()
                        .map(tuple -> ((IntrospectedDataMapper<Tuple>) Tuple::get)
                                .map(tuple, preparedQuery.getResultType()));

            } else {
                Query<R> q;
                @SuppressWarnings("unchecked")
                Class<R> wrapperType = ReflectionUtils.getWrapperType(preparedQuery.getResultType());
                if (preparedQuery.isNative()) {
                    q = currentSession.createNativeQuery(query, wrapperType);
                } else {
                    q = currentSession.createQuery(query, wrapperType);
                }
                bindParameters(q, parameterValues);
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
        return readTransactionTemplate.execute(status -> {
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

    private <T> void bindParameters(@NonNull Query<T> query, Map<String, Object> parameters) {
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }
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
        for (Sort.Order order : sort.getOrderBy()) {
            Path<String> path = root.get(order.getProperty());
            Expression expression = order.isIgnoreCase() ? builder.lower(path) : path;
            switch (order.getDirection()) {

                case DESC:
                    criteriaQuery.orderBy(
                            builder.desc(expression)
                    );
                    continue;
                default:
                case ASC:
                    criteriaQuery.orderBy(
                            builder.asc(expression)
                    );
            }
        }
    }

    @NonNull
    @Override
    public AsyncRepositoryOperations async() {
        return asyncOperations;
    }

    @NonNull
    @Override
    public ReactiveRepositoryOperations reactive() {
        return new ExecutorReactiveOperations(asyncOperations);
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
        writeTransactionTemplate.execute((status) -> {
                sessionFactory.getCurrentSession().flush();
                return null;
            }
        );
    }
}
