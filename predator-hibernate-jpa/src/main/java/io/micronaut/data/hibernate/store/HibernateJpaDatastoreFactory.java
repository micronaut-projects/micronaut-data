package io.micronaut.data.hibernate.store;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import org.hibernate.SessionFactory;

@Factory
public class HibernateJpaDatastoreFactory {

    @EachBean(SessionFactory.class)
    HibernateJpaDatastore hibernateJpaDatastore(SessionFactory sessionFactory) {
        return new HibernateJpaDatastore(sessionFactory);
    }
}
