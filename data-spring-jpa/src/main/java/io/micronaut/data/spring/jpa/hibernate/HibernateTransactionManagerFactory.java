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
package io.micronaut.data.spring.jpa.hibernate;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.spring.tx.DataSourceTransactionManagerFactory;
import org.hibernate.SessionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.hibernate5.HibernateTransactionManager;

import javax.sql.DataSource;

/**
 * Sets up the default hibernate transaction manager.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Factory
@Requires(classes = HibernateTransactionManager.class)
public final class HibernateTransactionManagerFactory {

    /**
     * @param sessionFactory The {@link SessionFactory}
     * @param dataSource     The {@link DataSource}
     * @return The {@link HibernateTransactionManager}
     */
    @Bean
    @Replaces(DataSourceTransactionManager.class)
    @Requires(classes = HibernateTransactionManager.class)
    @EachBean(SessionFactory.class)
    HibernateTransactionManager hibernateTransactionManager(SessionFactory sessionFactory, @Parameter DataSource dataSource) {
        HibernateTransactionManager hibernateTransactionManager = new HibernateTransactionManager(sessionFactory);
        hibernateTransactionManager.setDataSource(DelegatingDataSource.unwrapDataSource(dataSource));
        return hibernateTransactionManager;
    }
}
