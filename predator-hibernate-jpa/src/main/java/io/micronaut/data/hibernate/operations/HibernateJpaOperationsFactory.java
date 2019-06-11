/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.scheduling.TaskExecutors;
import org.hibernate.SessionFactory;

import javax.inject.Named;
import java.util.concurrent.ExecutorService;

/**
 * Factory that creates a {@link HibernateJpaOperations} for each configured {@link SessionFactory}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Factory
public class HibernateJpaOperationsFactory {

    /**
     * Creates the {@link HibernateJpaOperations}.
     * @param sessionFactory The session factory
     * @param executorService The executor service
     * @return The hibernate operations
     */
    @EachBean(SessionFactory.class)
    protected @NonNull HibernateJpaOperations hibernateOperations(
            @NonNull SessionFactory sessionFactory,
            @Named(TaskExecutors.IO) ExecutorService executorService) {
        return new HibernateJpaOperations(sessionFactory, executorService);
    }
}
