package io.micronaut.data.hibernate.runtime;

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.*;
import io.micronaut.data.hibernate.transaction.hibernate5.MicronautSessionContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.orm.hibernate5.HibernateTransactionManager;

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
    @Requires(missing = HibernateTransactionManager.class)
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
