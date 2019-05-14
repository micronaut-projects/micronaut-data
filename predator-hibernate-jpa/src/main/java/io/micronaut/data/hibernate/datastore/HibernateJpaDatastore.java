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
package io.micronaut.data.hibernate.datastore;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.Sort;
import io.micronaut.data.runtime.datastore.Datastore;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.persistence.criteria.*;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implementation of the {@link Datastore} interface for Hibernate.
 *
 * @author graemerocher
 * @since 1.0
 */
public class HibernateJpaDatastore implements Datastore {

    private final SessionFactory sessionFactory;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;

    /**
     * Default constructor.
     *
     * @param sessionFactory The session factory
     */
    protected HibernateJpaDatastore(@NonNull SessionFactory sessionFactory) {
        ArgumentUtils.requireNonNull("sessionFactory", sessionFactory);
        this.sessionFactory = sessionFactory;
        HibernateTransactionManager transactionManager = new HibernateTransactionManager(sessionFactory);
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setReadOnly(true);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager, def);
    }

    @Nullable
    @Override
    public <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return readTransactionTemplate.execute(status ->
                sessionFactory.getCurrentSession().byId(type).load(id)
        );
    }

    @Nullable
    @Override
    public <T> T findOne(@NonNull Class<T> resultType, @NonNull String query, @NonNull Map<String, Object> parameters) {
        return readTransactionTemplate.execute(status -> {
            Class wrapperType = ReflectionUtils.getWrapperType(resultType);
            Query<T> q = sessionFactory.getCurrentSession()
                                       .createQuery(query, wrapperType);
            bindParameters(q, parameters);
            q.setMaxResults(1);
            return q.uniqueResultOptional().orElse(null);
        });
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(@NonNull Class<T> rootEntity, @NonNull Pageable pageable) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Session session = sessionFactory.getCurrentSession();
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            CriteriaQuery<T> query = criteriaBuilder.createQuery(rootEntity);
            Root<T> root = query.from(rootEntity);
            Query<T> q = session.createQuery(
                    query
            );
            bindCriteriaSort(query, root, criteriaBuilder, pageable);
            bindPageable(q, pageable);

            return q.list();
        });
    }

    @Override
    public <T> long count(@NonNull Class<T> rootEntity, @NonNull Pageable pageable) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Session session = sessionFactory.getCurrentSession();
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
            Root<T> root = query.from(rootEntity);
            query = query.select(criteriaBuilder.count(root));
            Query<Long> q = session.createQuery(
                    query
            );
            bindCriteriaSort(query, root, criteriaBuilder, pageable);
            bindPageable(q, pageable);

            return q.getSingleResult();
        });
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(
            @NonNull Class<T> resultType,
            @NonNull String query,
            @NonNull Map<String, Object> parameterValues,
            @NonNull Pageable pageable) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Query<T> q = sessionFactory.getCurrentSession().createQuery(query, ReflectionUtils.getWrapperType(resultType));
            bindParameters(q, parameterValues);
            bindPageable(q, pageable);
            return q.list();
        });
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public <T> T persist(@NonNull T entity) {
        return writeTransactionTemplate.execute(status -> {
            sessionFactory.getCurrentSession().persist(entity);
            return entity;
        });
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public <T> Iterable<T> persistAll(@NonNull Iterable<T> entities) {
        return writeTransactionTemplate.execute(status -> {
            if (entities != null) {
                Session session = sessionFactory.getCurrentSession();
                for (T entity : entities) {
                    session.persist(entity);
                }
                return entities;
            } else {
                return Collections.emptyList();
            }
        });
    }

    @Override
    public Optional<Number> executeUpdate(String query, Map<String, Object> parameterValues) {
        //noinspection ConstantConditions
        return writeTransactionTemplate.execute(status -> {
            Query<?> q = sessionFactory.getCurrentSession().createQuery(query);
            bindParameters(q, parameterValues);
            return Optional.of(q.executeUpdate());
        });
    }

    @Override
    public <T> void deleteAll(@NonNull Class<T> entityType, @NonNull Iterable<? extends T> entities) {
        writeTransactionTemplate.execute(status -> {
            Session session = sessionFactory.getCurrentSession();
            for (T entity : entities) {
                session.remove(entity);
            }
            return null;
        });
    }

    @Override
    public <T> void deleteAll(@NonNull Class<T> entityType) {
        writeTransactionTemplate.execute(status -> {
            Session session = sessionFactory.getCurrentSession();
            CriteriaDelete<T> criteriaDelete = session.getCriteriaBuilder().createCriteriaDelete(entityType);
            criteriaDelete.from(entityType);
            Query query = session.createQuery(
                    criteriaDelete
            );
            query.executeUpdate();
            return null;
        });
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(
            @NonNull Class<T> resultType,
            @NonNull String query,
            @NonNull Map<String, Object> parameterValues,
            @NonNull Pageable pageable) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Query<T> q = sessionFactory.getCurrentSession().createQuery(query, ReflectionUtils.getWrapperType(resultType));
            bindParameters(q, parameterValues);
            bindPageable(q, pageable);

            return q.stream();
        });
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(@NonNull Class<T> entity, @NonNull Pageable pageable) {
        Session session = sessionFactory.getCurrentSession();
        CriteriaQuery<T> query = session.getCriteriaBuilder().createQuery(entity);
        query.from(entity);
        Query<T> q = session.createQuery(
                query
        );
        bindPageable(q, pageable);

        return q.stream();
    }

    @Override
    public <T> Page<T> findPage(@NonNull Class<T> entity, @NonNull Pageable pageable) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Session session = sessionFactory.getCurrentSession();
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            CriteriaQuery<T> query = criteriaBuilder.createQuery(entity);
            Root<T> root = query.from(entity);
            Query<T> q = session.createQuery(
                    query
            );
            bindCriteriaSort(query, root, criteriaBuilder, pageable);
            bindPageable(q, pageable);

            List<T> resultList = q.list();
            CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
            countQuery.select(criteriaBuilder.count(countQuery.from(entity)));
            Long total = session.createQuery(countQuery).getSingleResult();
            return Page.of(resultList, pageable, total);
        });
    }

    private <T> void bindParameters(@NonNull Query<T> query, Map<String, Object> parameters) {
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    private <T> void bindPageable(Query<T> q, @NonNull Pageable pageable) {
        if (pageable  == Pageable.UNPAGED) {
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
                            builder.asc(expression)
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
}
