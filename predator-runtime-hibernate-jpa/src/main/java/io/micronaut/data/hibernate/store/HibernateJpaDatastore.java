package io.micronaut.data.hibernate.store;

import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.store.Datastore;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.validation.constraints.Min;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * Implementation of the {@link Datastore} interface for Hibernate.
 *
 * @author graemerocher
 * @since 1.0
 */
public class HibernateJpaDatastore implements Datastore {

    private final SessionFactory sessionFactory;

    /**
     * Default constructor.
     * @param sessionFactory The session factory
     */
    protected HibernateJpaDatastore(@Nonnull SessionFactory sessionFactory) {
        ArgumentUtils.requireNonNull("sessionFactory", sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @Nullable
    @Override
    public <T> T findOne(@Nonnull Class<T> type, @Nonnull Serializable id) {
        return sessionFactory.getCurrentSession().byId(type).load(id);
    }

    @Nullable
    @Override
    public <T> T findOne(@Nonnull Class<T> type, @Nonnull String query, @Nonnull Map<String, Object> parameters) {
        Query<T> q = sessionFactory.getCurrentSession().createQuery(query, type);
        bindParameters(q, parameters);
        q.setMaxResults(1);
        try {
            return q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Nonnull
    @Override
    public <T> Iterable<T> findAll(
            @Nonnull Class<T> rootEntity,
            @Nonnull String query,
            @Nonnull Map<String, Object> parameterValues,
            @Nonnull Pageable pageable) {

        Query<T> q = sessionFactory.getCurrentSession().createQuery(query, rootEntity);
        bindParameters(q, parameterValues);
        int max = pageable.getMax();
        if (max > 0) {
            q.setMaxResults(max);
        }
        long offset = pageable.getOffset();
        if (offset > 0) {
            q.setFirstResult((int) offset);
        }

        return q.list();
    }

    @Override
    public <T> T persist(@Nonnull T entity) {
        sessionFactory.getCurrentSession().persist(entity);
        return entity;
    }

    @Override
    public <T> Iterable<T> persistAll(@Nonnull Iterable<T> entities) {
        if (entities != null) {
            Session session = sessionFactory.getCurrentSession();
            for (T entity : entities) {
                session.persist(entity);
            }
            return entities;
        } else {
            return Collections.emptyList();
        }
    }

    private <T> void bindParameters(@Nonnull Query<T> query, Map<String, Object> parameters) {
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }
    }
}
