package io.micronaut.data.hibernate.store;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.store.Datastore;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import java.io.Serializable;
import java.util.Collections;
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
     * @param sessionFactory The session factory
     */
    protected HibernateJpaDatastore(@Nonnull SessionFactory sessionFactory) {
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
    public <T> T findOne(@Nonnull Class<T> type, @Nonnull Serializable id) {
        return readTransactionTemplate.execute(status ->
                sessionFactory.getCurrentSession().byId(type).load(id)
        );
    }

    @Nullable
    @Override
    public <T> T findOne(@Nonnull Class<T> resultType, @Nonnull String query, @Nonnull Map<String, Object> parameters) {
        return readTransactionTemplate.execute(status -> {
            Query<T> q = sessionFactory.getCurrentSession().createQuery(query, ReflectionUtils.getWrapperType(resultType));
            bindParameters(q, parameters);
            q.setMaxResults(1);
            try {
                return q.getSingleResult();
            } catch (NoResultException e) {
                return null;
            }
        });
    }

    @Nonnull
    @Override
    public <T> Iterable<T> findAll(@Nonnull Class<T> rootEntity, @Nonnull Pageable pageable) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Session session = sessionFactory.getCurrentSession();
            CriteriaQuery<T> query = session.getCriteriaBuilder().createQuery(rootEntity);
            query.from(rootEntity);
            Query<T> q = session.createQuery(
                    query
            );
            bindPageable(q, pageable);

            return q.list();
        });
    }

    @Override
    public <T> long count(@Nonnull Class<T> rootEntity, @Nonnull Pageable pageable) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Session session = sessionFactory.getCurrentSession();
            CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
            CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
            query = query.select(criteriaBuilder.count(query.from(rootEntity)));
            Query<Long> q = session.createQuery(
                    query
            );
            bindPageable(q, pageable);

            return q.getSingleResult();
        });
    }

    @Nonnull
    @Override
    public <T> Iterable<T> findAll(
            @Nonnull Class<T> resultType,
            @Nonnull String query,
            @Nonnull Map<String, Object> parameterValues,
            @Nonnull Pageable pageable) {
        //noinspection ConstantConditions
        return readTransactionTemplate.execute(status -> {
            Query<T> q = sessionFactory.getCurrentSession().createQuery(query, ReflectionUtils.getWrapperType(resultType));
            bindParameters(q, parameterValues);
            bindPageable(q, pageable);

            return q.list();
        });
    }

    @Override
    public <T> T persist(@Nonnull T entity) {
        return writeTransactionTemplate.execute(status -> {
            sessionFactory.getCurrentSession().persist(entity);
            return entity;
        });
    }

    @Override
    public <T> Iterable<T> persistAll(@Nonnull Iterable<T> entities) {
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
    public <T> void deleteAll(@Nonnull Class<T> entityType, @Nonnull Iterable<? extends T> entities) {
        writeTransactionTemplate.execute(status -> {
            Session session = sessionFactory.getCurrentSession();
            for (T entity : entities) {
                session.remove(entity);
            }
            return null;
        });
    }

    @Override
    public <T> void deleteAll(@Nonnull Class<T> entityType) {
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

    private <T> void bindParameters(@Nonnull Query<T> query, Map<String, Object> parameters) {
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    private <T> void bindPageable(Query<T> q, @Nonnull Pageable pageable) {
        int max = pageable.getSize();
        if (max > 0) {
            q.setMaxResults(max);
        }
        long offset = pageable.getOffset();
        if (offset > 0) {
            q.setFirstResult((int) offset);
        }
    }
}
