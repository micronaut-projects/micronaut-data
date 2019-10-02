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
package io.micronaut.data.hibernate.runtime;

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.*;
import io.micronaut.transaction.hibernate5.MicronautSessionContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.cfg.AvailableSettings;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class replaces the {@link StandardServiceRegistry} configured by {@link io.micronaut.configuration.hibernate.jpa.EntityManagerFactoryBean}, decoupling it from Spring transaction management.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Factory
public class DataEntityManagerFactoryBean {
    private final JpaConfiguration jpaConfiguration;
    private final BeanLocator beanLocator;


    /**
     * @param jpaConfiguration The JPA configuration
     * @param beanLocator      The bean locator
     */
    public DataEntityManagerFactoryBean(
            JpaConfiguration jpaConfiguration,
            BeanLocator beanLocator) {

        this.jpaConfiguration = jpaConfiguration;
        this.beanLocator = beanLocator;
    }

    /**
     * Builds the {@link StandardServiceRegistry} bean for the given {@link DataSource}.
     *
     * @param dataSourceName The data source name
     * @param dataSource     The data source
     * @return The {@link StandardServiceRegistry}
     */
    @EachBean(DataSource.class)
    @Replaces(
            factory = io.micronaut.configuration.hibernate.jpa.EntityManagerFactoryBean.class,
            bean = StandardServiceRegistry.class)
    @Requires(missingClasses = "org.springframework.orm.hibernate5.HibernateTransactionManager")
    protected StandardServiceRegistry hibernateStandardServiceRegistry(
            @Parameter String dataSourceName,
            DataSource dataSource) {

        Map<String, Object> additionalSettings = new LinkedHashMap<>();
        additionalSettings.put(AvailableSettings.DATASOURCE, dataSource);
        additionalSettings.put(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, MicronautSessionContext.class.getName());
        additionalSettings.put(AvailableSettings.SESSION_FACTORY_NAME, dataSourceName);
        additionalSettings.put(AvailableSettings.SESSION_FACTORY_NAME_IS_JNDI, false);
        JpaConfiguration jpaConfiguration = beanLocator.findBean(JpaConfiguration.class, Qualifiers.byName(dataSourceName))
                .orElse(this.jpaConfiguration);
        return jpaConfiguration.buildStandardServiceRegistry(
                additionalSettings
        );
    }
}
