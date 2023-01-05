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
package io.micronaut.configuration.hibernate.jpa;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import jakarta.inject.Inject;
import org.hibernate.integrator.spi.Integrator;

import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Configuration for JPA and Hibernate.
 *
 * @author graemerocher
 * @since 1.0
 */
@EachProperty(value = JpaConfiguration.PREFIX, primary = JpaConfiguration.PRIMARY)
public class JpaConfiguration {
    public static final String PREFIX = "jpa";
    public static final String PRIMARY = "default";

    private final String name;
    private final ApplicationContext applicationContext;
    private final Integrator integrator;
    private Map<String, Object> jpaProperties = new HashMap<>(10);
    private List<String> mappingResources = new ArrayList<>();
    private EntityScanConfiguration entityScanConfiguration;

    private boolean compileTimeHibernateProxies;
    private boolean reactive;

    /**
     * @param applicationContext The application context
     * @param integrator         The {@link Integrator}
     */
    public JpaConfiguration(ApplicationContext applicationContext, @Nullable Integrator integrator) {
        this(PRIMARY, integrator, applicationContext, new EntityScanConfiguration(applicationContext.getEnvironment()));
    }

    /**
     * @param name The name
     * @param applicationContext The application context
     * @param integrator         The {@link Integrator}
     */
    public JpaConfiguration(@Parameter String name, ApplicationContext applicationContext, @Nullable Integrator integrator) {
        this(name, integrator, applicationContext, new EntityScanConfiguration(applicationContext.getEnvironment()));
    }

    /**
     * @param name                    The name
     * @param integrator              The integrator
     * @param applicationContext      The application context
     * @param entityScanConfiguration The entity scan configuration
     */
    @Inject
    protected JpaConfiguration(@Parameter String name,
                               @Nullable Integrator integrator,
                               ApplicationContext applicationContext,
                               @Nullable EntityScanConfiguration entityScanConfiguration) {
        this.name = name;
        this.entityScanConfiguration = entityScanConfiguration != null ? entityScanConfiguration : new EntityScanConfiguration(applicationContext.getEnvironment());
        this.applicationContext = applicationContext;
        this.integrator = integrator;
    }

    /**
     * @return The configuration name
     */
    public String getName() {
        return name;
    }

    /**
     * @return The entity scan configuration
     */
    public EntityScanConfiguration getEntityScanConfiguration() {
        return entityScanConfiguration;
    }

    /**
     * Sets the packages to scan.
     *
     * @param packagesToScan The packages to scan
     */
    public void setPackagesToScan(String... packagesToScan) {
        if (ArrayUtils.isNotEmpty(packagesToScan)) {
            EntityScanConfiguration entityScanConfiguration = new EntityScanConfiguration(applicationContext.getEnvironment());
            entityScanConfiguration.setPackages(packagesToScan);
            this.entityScanConfiguration = entityScanConfiguration;
        }
    }

    /**
     * @return The packages to scan
     */
    public String[] getPackagesToScan() {
        return entityScanConfiguration.getPackages();
    }

    /**
     * Sets the JPA properties to be passed to the JPA implementation.
     *
     * @param jpaProperties The JPA properties
     */
    public final void setProperties(@MapFormat(transformation = MapFormat.MapTransformation.FLAT, keyFormat = StringConvention.RAW)
                                    @NonNull Map<String, Object> jpaProperties) {
        this.jpaProperties = jpaProperties;
    }

    /**
     * @return The JPA properties
     */
    @NonNull
    public Map<String, Object> getProperties() {
        return jpaProperties;
    }

    /**
     * Mapping resources (equivalent to "mapping-file" entries in persistence.xml).
     *
     * @return The mapping resources
     */
    @NonNull
    public List<String> getMappingResources() {
        return this.mappingResources;
    }

    /**
     * Sets additional mapping resources.
     *
     * @param mappingResources list of mapping files
     */
    public void setMappingResources(List<String> mappingResources) {
        this.mappingResources = mappingResources;
    }

    /**
     * Compile time Hibernate proxies.
     *
     * @return true if compile time proxies enabled
     */
    public boolean isCompileTimeHibernateProxies() {
        return compileTimeHibernateProxies;
    }

    /**
     * Enable compile time Hibernate proxies.
     *
     * @param compileTimeHibernateProxies true to enable compile time proxies
     */
    public void setCompileTimeHibernateProxies(boolean compileTimeHibernateProxies) {
        this.compileTimeHibernateProxies = compileTimeHibernateProxies;
    }

    /**
     * @return is reactive
     */
    public boolean isReactive() {
        return reactive;
    }

    /**
     * @param reactive the reactive value
     */
    public void setReactive(boolean reactive) {
        this.reactive = reactive;
    }

    /**
     * Copies current configuration.
     *
     * @param name A new name
     * @return A copy of current configuration
     */
    public JpaConfiguration copy(String name) {
        JpaConfiguration jpaConfiguration = new JpaConfiguration(name, integrator, applicationContext, entityScanConfiguration);
        jpaConfiguration.setProperties(new HashMap<>(this.getProperties()));
        jpaConfiguration.setMappingResources(new ArrayList<>(this.getMappingResources()));
        jpaConfiguration.setCompileTimeHibernateProxies(compileTimeHibernateProxies);
        jpaConfiguration.setReactive(reactive);
        return jpaConfiguration;
    }

    /**
     * The entity scan configuration.
     */
    @ConfigurationProperties("entity-scan")
    public static class EntityScanConfiguration implements Toggleable {
        private boolean enabled = true;
        private String[] packages = StringUtils.EMPTY_STRING_ARRAY;

        private final Environment environment;

        /**
         * Default constructor.
         *
         * @param environment The environment
         */
        public EntityScanConfiguration(Environment environment) {
            this.environment = environment;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Set whether entity scan is enabled. Defaults to true.
         *
         * @param enabled True if it is enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * The packages to limit the scan to.
         *
         * @return The packages to limit the scan to
         */
        public String[] getPackages() {
            return packages;
        }

        /**
         * @param packages The packages
         */
        public void setPackages(String[] packages) {
            this.packages = packages;
        }

        /**
         * Find entities for the current configuration.
         *
         * @return The entities
         */
        public Collection<Class<?>> findEntities() {
            Collection<Class<?>> entities = new HashSet<>();
            if (isEnabled()) {
                if (ArrayUtils.isNotEmpty(packages)) {
                    entities.addAll(environment.scan(Entity.class, packages).collect(Collectors.toSet()));
                } else {
                    entities.addAll(BeanIntrospector.SHARED.findIntrospections(Entity.class)
                            .stream().map(BeanIntrospection::getBeanType)
                                            .collect(Collectors.toSet()));
                }
            }
            return Collections.unmodifiableCollection(entities);
        }
    }
}
