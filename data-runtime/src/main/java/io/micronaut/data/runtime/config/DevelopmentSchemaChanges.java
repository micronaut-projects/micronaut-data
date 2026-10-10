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
package io.micronaut.data.runtime.config;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
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
 * What the development schema reloaders of the SQL modules share: which tables changed between two generations of
 * the application's entities, and generating the schema again. Used in development mode only.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class DevelopmentSchemaChanges {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentSchemaChanges.class);

    private DevelopmentSchemaChanges() {
    }

    /**
     * The entities whose {@code CREATE TABLE} statements differ between the two generations, for a data source with
     * the given packages. A removed entity keeps its table, as the schema generation of an application that starts
     * does, and is not reported. The entities are read with the introspector of their own generation: the shared
     * introspector would remember a retired generation's introspections, and keep its classes reachable.
     *
     * @param oldLoader The loader of the running generation
     * @param newLoader The loader of the new generation
     * @param packages The packages of the data source, none for every package
     * @param builder The query builder of the data source
     * @param definitionProviders The providers of vendor-specific definitions
     * @return The changed tables, empty when the loaders are the same
     */
    @SuppressWarnings("ReferenceEquality") // a generation is its loader
    public static List<ChangedTable> changedTables(ClassLoader oldLoader,
                                                   ClassLoader newLoader,
                                                   List<String> packages,
                                                   SqlQueryBuilder builder,
                                                   List<DefinitionProvider> definitionProviders) {
        if (oldLoader == newLoader) {
            return List.of();
        }
        BeanIntrospector previousIntrospector = BeanIntrospector.forClassLoader(oldLoader);
        BeanIntrospector currentIntrospector = BeanIntrospector.forClassLoader(newLoader);
        Map<String, BeanIntrospection<Object>> before = entities(previousIntrospector);
        Map<String, BeanIntrospection<Object>> after = entities(currentIntrospector);
        List<ChangedTable> changed = new ArrayList<>();
        for (Map.Entry<String, BeanIntrospection<Object>> entry : before.entrySet()) {
            BeanIntrospection<Object> current = after.get(entry.getKey());
            if (current == null || !inPackages(packages, entry.getKey())) {
                continue;
            }
            try {
                RuntimePersistentEntity<Object> previousEntity = new GenerationEntity<>(entry.getValue(), previousIntrospector);
                if (!Arrays.equals(builder.buildCreateTableStatements(previousEntity, definitionProviders),
                    builder.buildCreateTableStatements(new GenerationEntity<>(current, currentIntrospector), definitionProviders))) {
                    changed.add(new ChangedTable(entry.getKey(), previousEntity.getPersistedName()));
                }
            } catch (RuntimeException | LinkageError e) {
                LOG.debug("Cannot compare the table of entity {}", entry.getKey(), e);
            }
        }
        return changed;
    }

    /**
     * Generates the schema again, by recreating the schema generator, or by creating it when a bean it depends on
     * was recreated before it, which destroyed it with its dependents. A context that does not track bean
     * dependencies keeps the schema as it is.
     *
     * @param beanContext The context
     * @param generatorType The type of the schema generator
     */
    public static void regenerate(BeanContext beanContext, Class<?> generatorType) {
        if (!(beanContext instanceof WatchableBeanContext context)) {
            return;
        }
        List<Object> generators = new ArrayList<>();
        for (BeanRegistration<?> registration : beanContext.getActiveBeanRegistrations(generatorType)) {
            generators.add(registration.bean());
        }
        if (generators.isEmpty()) {
            if (beanContext.containsBean(generatorType)) {
                beanContext.getBean(generatorType);
            }
            return;
        }
        for (Object generator : generators) {
            context.recreate(generator);
        }
    }

    private static boolean inPackages(List<String> packages, String className) {
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
            // as the schema generators select them: no inner or abstract classes, and no JSON sub-views
            if (type.getName().contains("$") || Modifier.isAbstract(type.getModifiers()) || introspection.hasAnnotation(JsonSubView.class)) {
                continue;
            }
            entities.put(type.getName(), introspection);
        }
        return entities;
    }

    /**
     * A table whose definition changed.
     *
     * @param entity The entity class name
     * @param table The table name
     */
    public record ChangedTable(String entity, String table) {
    }

    /**
     * An entity of one generation, whose associated entities are read with the introspector of that generation
     * only.
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
