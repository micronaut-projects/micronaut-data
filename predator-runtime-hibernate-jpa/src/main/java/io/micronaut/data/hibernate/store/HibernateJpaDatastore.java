package io.micronaut.data.hibernate.store;

import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.store.Datastore;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import java.io.Serializable;
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
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                q.setParameter(entry.getKey(), entry.getValue());
            }
        }
        try {
            return q.getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
