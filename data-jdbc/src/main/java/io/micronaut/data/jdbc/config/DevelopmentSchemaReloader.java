/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.jdbc.config;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
import io.micronaut.data.runtime.config.SchemaGenerate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the schema that {@link SchemaGenerator} generates in step with the entities in development mode, as far as
 * the configured schema generation allows. It exists only in development mode, so nothing of it is on the path of a
 * query, nor of the schema generation of an application in production.
 *
 * <ul>
 *     <li>A change applied in place that retires a classloader generates the schema again, as the application did as
 *     it started, by recreating the schema generator.</li>
 *     <li>A change that restarts the application, or that is applied in place and retires a classloader, warns about
 *     each entity whose table definition changed, for each data source that generates its schema in
 *     {@link SchemaGenerate#CREATE} mode. {@code CREATE} only creates the tables that do not exist, so the table of
 *     the previous version of an entity, without the columns it gained, is kept in a database that outlives the
 *     application: a retained connection pool, an in-memory database kept open, or a database server. Nothing is
 *     dropped: the warning recommends {@link SchemaGenerate#CREATE_DROP} for the development environment, whose
 *     schema follows the entities on each restart, at the cost of the rows.</li>
 * </ul>
 *
 * <p>A change that redefines classes in place, without retiring a classloader, is ignored: it changes method bodies
 * only, and the entities keep their properties.</p>
 *
 * <p>It holds the context only, never a data source nor an entity.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Context
@DevelopmentActive
final class DevelopmentSchemaReloader {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentSchemaReloader.class);

    private final BeanContext beanContext;

    /**
     * @param beanContext The context, watched when it can be
     */
    DevelopmentSchemaReloader(BeanContext beanContext) {
        this.beanContext = beanContext;
        if (beanContext instanceof WatchableBeanContext watchable) {
            watchable.watchClassChanges(this::onClassChange);
        }
    }

    private void onClassChange(ClassChangeEvent change) {
        boolean restart = change.strategy() == ReloadStrategy.RESTART;
        if (!restart && change.retiredLoaders().isEmpty()) {
            return;
        }
        List<DataJdbcConfiguration> configurations = new ArrayList<>();
        for (DataJdbcConfiguration configuration : beanContext.getBeansOfType(DataJdbcConfiguration.class)) {
            if (configuration.isEnabled() && configuration.getSchemaGenerate() == SchemaGenerate.CREATE) {
                configurations.add(configuration);
            }
        }
        if (!configurations.isEmpty() && !change.changes().isEmpty()) {
            warnChangedTables(configurations, beanContext.getClassLoader(), change.newLoader());
        }
        if (!restart) {
            regenerate();
        }
    }

    /**
     * Warns about the entities whose table definition differs between the two generations, whose tables a schema
     * generation in create mode keeps as they were.
     *
     * @param configurations The configurations that generate their schema in create mode
     * @param oldLoader The loader of the running generation
     * @param newLoader The loader of the new generation
     */
    @SuppressWarnings("ReferenceEquality") // a generation is its loader
    private void warnChangedTables(List<DataJdbcConfiguration> configurations, ClassLoader oldLoader, ClassLoader newLoader) {
        if (oldLoader == newLoader) {
            return;
        }
        BeanIntrospector previousIntrospector = BeanIntrospector.forClassLoader(oldLoader);
        BeanIntrospector currentIntrospector = BeanIntrospector.forClassLoader(newLoader);
        Map<String, BeanIntrospection<Object>> before = entities(previousIntrospector);
        Map<String, BeanIntrospection<Object>> after = entities(currentIntrospector);
        List<DefinitionProvider> definitionProviders = new ArrayList<>(beanContext.getBeansOfType(DefinitionProvider.class));
        for (DataJdbcConfiguration configuration : configurations) {
            SqlQueryBuilder builder = new SqlQueryBuilder(configuration.getDialect(), configuration.getDialectOptions().getVersion());
            for (Map.Entry<String, BeanIntrospection<Object>> entry : before.entrySet()) {
                BeanIntrospection<Object> current = after.get(entry.getKey());
                // a removed entity keeps its table, as the schema generation of an application that starts does
                if (current == null || !inPackages(configuration, entry.getKey())) {
                    continue;
                }
                try {
                    RuntimePersistentEntity<Object> previousEntity = new GenerationEntity<>(entry.getValue(), previousIntrospector);
                    if (!Arrays.equals(builder.buildCreateTableStatements(previousEntity, definitionProviders),
                        builder.buildCreateTableStatements(new GenerationEntity<>(current, currentIntrospector), definitionProviders))) {
                        LOG.warn("""
                            The table [{}] of entity {} changed, but data source [{}] generates its schema in CREATE mode, \
                            which only creates missing tables: a database that outlives the application keeps the previous table. \
                            To have the schema follow entity changes in development, set datasources.{}.schema-generate=CREATE_DROP \
                            in application-dev.properties (this drops and recreates the tables, and their rows, on each restart), \
                            or drop the table yourself.""",
                            previousEntity.getPersistedName(), entry.getKey(), configuration.getName(), configuration.getName());
                    }
                } catch (RuntimeException | LinkageError e) {
                    LOG.debug("Cannot compare the table of entity {}", entry.getKey(), e);
                }
            }
        }
    }

    private static boolean inPackages(DataJdbcConfiguration configuration, String className) {
        List<String> packages = configuration.getPackages();
        if (CollectionUtils.isEmpty(packages)) {
            return true;
        }
        for (String packageName : packages) {
            if (className.startsWith(packageName + '.')) {
                return true;
            }
        }
        return false;
    }

    /**
     * The entities a generation has that the schema generation creates a table for, by class name.
     *
     * @param introspector The introspector of the generation
     * @return The introspections of the entities
     */
    private static Map<String, BeanIntrospection<Object>> entities(BeanIntrospector introspector) {
        Map<String, BeanIntrospection<Object>> entities = new LinkedHashMap<>();
        Collection<BeanIntrospection<Object>> introspections = introspector.findIntrospections(MappedEntity.class);
        for (BeanIntrospection<Object> introspection : introspections) {
            Class<Object> type = introspection.getBeanType();
            // as the schema generator selects them: no inner or abstract classes, and no JSON sub-views
            if (type.getName().contains("$") || Modifier.isAbstract(type.getModifiers()) || introspection.hasAnnotation(JsonSubView.class)) {
                continue;
            }
            entities.put(type.getName(), introspection);
        }
        return entities;
    }

    /**
     * Generates the schema again, by recreating the schema generator, or by creating it when a bean it depends on
     * was recreated before it, which destroyed it with its dependents.
     */
    private void regenerate() {
        if (!(beanContext instanceof WatchableBeanContext context)) {
            return;
        }
        List<Object> generators = new ArrayList<>();
        for (BeanRegistration<SchemaGenerator> registration : beanContext.getActiveBeanRegistrations(SchemaGenerator.class)) {
            generators.add(registration.bean());
        }
        if (generators.isEmpty()) {
            beanContext.getBean(SchemaGenerator.class);
            return;
        }
        for (Object generator : generators) {
            // false in a context that does not track bean dependencies: the schema is kept as it is
            context.recreate(generator);
        }
    }

    /**
     * An entity of one generation, whose associated entities are read with the introspector of that generation
     * only. The shared introspector would remember a retired generation's introspections for as long as memory
     * allows, after the runtime forgot them as it retired the generation, and keep its classes reachable.
     *
     * @param <T> The entity type
     */
    private static final class GenerationEntity<T> extends RuntimePersistentEntity<T> {

        private final BeanIntrospector introspector;

        GenerationEntity(BeanIntrospection<T> introspection, BeanIntrospector introspector) {
            super(introspection);
            this.introspector = introspector;
        }

        @Override
        protected RuntimePersistentEntity<T> getEntity(Class<T> type) {
            return new GenerationEntity<>(introspector.getIntrospection(type), introspector);
        }
    }
}
