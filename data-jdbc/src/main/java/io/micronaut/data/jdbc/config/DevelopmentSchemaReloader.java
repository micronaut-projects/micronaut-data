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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the schema that {@link SchemaGenerator} generates in step with the entities in development mode. It exists
 * only in development mode, so nothing of it is on the path of a query, nor of the schema generation of an
 * application in production.
 *
 * <ul>
 *     <li>A change that restarts the application, or that is applied in place and retires a classloader, drops the
 *     tables of the entities whose table definition changed, and of the entities whose tables refer to theirs through
 *     a foreign key, for each data source that generates its schema in
 *     {@link SchemaGenerate#CREATE} mode, so that the schema generation of the new code creates them as they are
 *     now. {@code CREATE} only creates the tables that do not exist, and would otherwise keep the table of the
 *     previous version of an entity, without the columns it gained, in a database that outlives the application:
 *     a retained connection pool, an in-memory database kept open, or a database server. The tables of the other
 *     entities keep their rows. {@link SchemaGenerate#CREATE_DROP} needs nothing, since it drops
 *     every table anyway.</li>
 *     <li>A change applied in place that retires a classloader also generates the schema again, as the application
 *     did as it started, by recreating the schema generator.</li>
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
@Requires(condition = DevelopmentMode.Active.class)
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
            dropChangedTables(configurations, beanContext.getClassLoader(), change.newLoader());
        }
        if (!restart) {
            regenerate();
        }
    }

    /**
     * Drops the tables of the entities whose table definition differs between the two generations.
     *
     * @param configurations The configurations that generate their schema in create mode
     * @param oldLoader The loader of the running generation
     * @param newLoader The loader of the new generation
     */
    @SuppressWarnings("ReferenceEquality") // a generation is its loader
    private void dropChangedTables(List<DataJdbcConfiguration> configurations, ClassLoader oldLoader, ClassLoader newLoader) {
        if (oldLoader == newLoader) {
            return;
        }
        BeanIntrospector previousIntrospector = BeanIntrospector.forClassLoader(oldLoader);
        BeanIntrospector currentIntrospector = BeanIntrospector.forClassLoader(newLoader);
        Map<String, BeanIntrospection<Object>> before = entities(previousIntrospector);
        Map<String, BeanIntrospection<Object>> after = entities(currentIntrospector);
        List<DefinitionProvider> definitionProviders = new ArrayList<>(beanContext.getBeansOfType(DefinitionProvider.class));
        PropertyPlaceholderResolver placeholderResolver = beanContext instanceof ApplicationContext applicationContext
            ? applicationContext.getEnvironment().getPlaceholderResolver()
            : null;
        for (DataJdbcConfiguration configuration : configurations) {
            SqlQueryBuilder builder = new SqlQueryBuilder(configuration.getDialect(), configuration.getDialectOptions().getVersion());
            Map<String, RuntimePersistentEntity<Object>> previousEntities = new LinkedHashMap<>();
            Set<String> changed = new LinkedHashSet<>();
            for (Map.Entry<String, BeanIntrospection<Object>> entry : before.entrySet()) {
                if (!inPackages(configuration, entry.getKey())) {
                    continue;
                }
                try {
                    RuntimePersistentEntity<Object> previousEntity = new GenerationEntity<>(entry.getValue(), previousIntrospector);
                    previousEntities.put(entry.getKey(), previousEntity);
                    BeanIntrospection<Object> current = after.get(entry.getKey());
                    // a removed entity keeps its table, as the schema generation of an application that starts does
                    if (current != null && !Arrays.equals(builder.buildCreateTableStatements(previousEntity, definitionProviders),
                        builder.buildCreateTableStatements(new GenerationEntity<>(current, currentIntrospector), definitionProviders))) {
                        changed.add(entry.getKey());
                    }
                } catch (RuntimeException | LinkageError e) {
                    LOG.debug("Cannot compare the table of entity {}: it is kept", entry.getKey(), e);
                }
            }
            if (changed.isEmpty()) {
                continue;
            }
            Set<String> dropped = withReferencingEntities(changed, previousEntities);
            List<List<String>> drops = new ArrayList<>();
            for (Map.Entry<String, RuntimePersistentEntity<Object>> entry : previousEntities.entrySet()) {
                String entity = entry.getKey();
                if (dropped.contains(entity)) {
                    LOG.info("{} {}: dropping its table from data source [{}], whose schema generation creates it again", entity,
                        changed.contains(entity) ? "changed" : "references a changed entity", configuration.getName());
                    drops.add(Arrays.asList(builder.buildDropTableStatements(entry.getValue())));
                }
            }
            drop(configuration, drops, placeholderResolver);
        }
    }

    /**
     * The changed entities, and the entities whose tables reference theirs, directly or through other such entities:
     * a table cannot be dropped while a foreign key of another table refers to it, and the table that refers to it is
     * created again together with it.
     *
     * @param changed The entities whose table changed
     * @param entities The entities of the configuration, as they were
     * @return The entities whose tables are dropped
     */
    private static Set<String> withReferencingEntities(Set<String> changed, Map<String, RuntimePersistentEntity<Object>> entities) {
        Set<String> dropped = new LinkedHashSet<>(changed);
        boolean added = true;
        while (added) {
            added = false;
            for (Map.Entry<String, RuntimePersistentEntity<Object>> entry : entities.entrySet()) {
                if (!dropped.contains(entry.getKey()) && references(entry.getValue(), dropped)) {
                    dropped.add(entry.getKey());
                    added = true;
                }
            }
        }
        return dropped;
    }

    private static boolean references(RuntimePersistentEntity<Object> entity, Set<String> entityNames) {
        try {
            for (Association association : entity.getAssociations()) {
                // a foreign key of this table, or of a join table this entity owns: not an embedded value, nor the
                // inverse side of a relation, whose foreign key is in the other table
                if (association.getKind() == Relation.Kind.EMBEDDED
                    || association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").isPresent()) {
                    continue;
                }
                PersistentEntity associated = association.getAssociatedEntity();
                if (associated != null && entityNames.contains(associated.getName())) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            LOG.debug("Cannot read the associations of entity {}", entity.getName(), e);
        }
        return false;
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
     * Drops the tables, each with its join tables. A drop that fails, as one of a table that another table being
     * dropped still refers to does, is tried again after the others, for as long as that makes progress.
     *
     * @param configuration The configuration
     * @param drops The statements that drop each table
     * @param placeholderResolver The placeholder resolver, if any
     */
    private void drop(DataJdbcConfiguration configuration, List<List<String>> drops, @Nullable PropertyPlaceholderResolver placeholderResolver) {
        DataSource dataSource;
        try {
            dataSource = DelegatingDataSource.unwrapDataSource(beanContext.getBean(DataSource.class, Qualifiers.byName(configuration.getName())));
        } catch (RuntimeException e) {
            LOG.debug("No data source [{}] to drop the changed tables from", configuration.getName(), e);
            return;
        }
        JdbcSchemaHandler schemaHandler = beanContext.getBean(JdbcSchemaHandler.class);
        List<String> schemaNames = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(configuration.getSchemaGenerateNames())) {
            schemaNames.addAll(configuration.getSchemaGenerateNames());
        } else {
            schemaNames.add(configuration.getSchemaGenerateName());
        }
        try (Connection connection = dataSource.getConnection()) {
            for (String schemaName : schemaNames) {
                if (schemaName != null) {
                    schemaHandler.useSchema(connection, configuration.getDialect(), schemaName);
                }
                List<String> pending = new ArrayList<>();
                for (List<String> statements : drops) {
                    for (String statement : statements) {
                        pending.add(placeholderResolver != null && statement.contains(placeholderResolver.getPrefix())
                            ? placeholderResolver.resolveRequiredPlaceholders(statement)
                            : statement);
                    }
                }
                Map<String, String> failures = new LinkedHashMap<>();
                while (!pending.isEmpty()) {
                    failures.clear();
                    for (String sql : pending) {
                        if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                            DataSettings.QUERY_LOG.debug("Dropping changed table: \n{}", sql);
                        }
                        try (Statement statement = connection.createStatement()) {
                            statement.executeUpdate(sql);
                        } catch (SQLException e) {
                            failures.put(sql, e.getMessage());
                        }
                    }
                    if (failures.size() == pending.size()) {
                        break;
                    }
                    pending = new ArrayList<>(failures.keySet());
                }
                failures.forEach((sql, message) ->
                    LOG.warn("Cannot drop a changed table of data source [{}] with [{}]: {}", configuration.getName(), sql, message));
            }
        } catch (SQLException | RuntimeException e) {
            LOG.warn("Cannot drop the changed tables of data source [{}]: {}", configuration.getName(), e.getMessage(), e);
        }
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
