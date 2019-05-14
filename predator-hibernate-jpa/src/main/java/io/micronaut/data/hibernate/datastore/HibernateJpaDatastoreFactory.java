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
