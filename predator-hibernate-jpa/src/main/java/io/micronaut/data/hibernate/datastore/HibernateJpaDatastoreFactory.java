package io.micronaut.data.hibernate.datastore;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import org.hibernate.SessionFactory;

/**
 * Factory that creates a {@link HibernateJpaDatastore} for each configured {@link SessionFactory}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Factory
public class HibernateJpaDatastoreFactory {

    /**
     * Creates the {@link HibernateJpaDatastore}.
     * @param sessionFactory The session factory
     * @return The hibernate datastore
     */
    @EachBean(SessionFactory.class)
    protected @NonNull HibernateJpaDatastore hibernateJpaDatastore(@NonNull SessionFactory sessionFactory) {
        return new HibernateJpaDatastore(sessionFactory);
    }
}
