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
package io.micronaut.data.r2dbc.config;

import io.micronaut.context.BeanContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.watch.ClassChangeWatcher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
import io.micronaut.data.runtime.config.DevelopmentSchemaChanges;
import io.micronaut.data.runtime.config.SchemaGenerate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the schema that {@link R2dbcSchemaGenerator} generates in step with the entities in development mode, as far
 * as the configured schema generation allows, as the JDBC module does. It exists only in development mode, so nothing
 * of it is on the path of a query, nor of the schema generation of an application in production.
 *
 * <ul>
 *     <li>A change applied in place that retires a classloader generates the schema again, as the application did as
 *     it started, by recreating the schema generator, after the entity registry was recreated.</li>
 *     <li>A change that restarts the application, or that is applied in place and retires a classloader, warns about
 *     each entity whose table definition changed, for each R2DBC data source that generates its schema in
 *     {@link SchemaGenerate#CREATE} mode. Development mode retains the R2DBC connection factory across restarts, so
 *     an in-memory database the pool keeps open outlives the application as a database server does, and keeps the
 *     previous table of a changed entity. Nothing is dropped: the warning recommends
 *     {@link SchemaGenerate#CREATE_DROP} for the development environment.</li>
 * </ul>
 *
 * <p>It holds the context only, never a connection factory nor an entity.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Context
@DevelopmentActive
final class DevelopmentR2dbcSchemaReloader {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentR2dbcSchemaReloader.class);

    private final BeanContext beanContext;

    /**
     * @param beanContext The context, watched when it can be
     */
    DevelopmentR2dbcSchemaReloader(BeanContext beanContext) {
        this.beanContext = beanContext;
        if (beanContext instanceof WatchableBeanContext watchable) {
            watchable.classChanges().watch(new SchemaWatcher());
        }
    }

    private void onClassChange(ClassChangeEvent change) {
        boolean restart = change.strategy() == ReloadStrategy.RESTART;
        if (!restart && change.retiredLoaders().isEmpty()) {
            return;
        }
        List<DataR2dbcConfiguration> configurations = new ArrayList<>();
        for (DataR2dbcConfiguration configuration : beanContext.getBeansOfType(DataR2dbcConfiguration.class)) {
            if (configuration.getSchemaGenerate() == SchemaGenerate.CREATE) {
                configurations.add(configuration);
            }
        }
        if (!configurations.isEmpty() && !change.changes().isEmpty()) {
            warnChangedTables(configurations, beanContext.getClassLoader(), change.newLoader());
        }
        if (!restart) {
            DevelopmentSchemaChanges.regenerate(beanContext, R2dbcSchemaGenerator.class);
        }
    }

    private void warnChangedTables(List<DataR2dbcConfiguration> configurations, ClassLoader oldLoader, ClassLoader newLoader) {
        List<DefinitionProvider> definitionProviders = new ArrayList<>(beanContext.getBeansOfType(DefinitionProvider.class));
        for (DataR2dbcConfiguration configuration : configurations) {
            SqlQueryBuilder builder = new SqlQueryBuilder(configuration.getDialect(), configuration.getDialectOptions().getVersion());
            for (DevelopmentSchemaChanges.ChangedTable changed : DevelopmentSchemaChanges.changedTables(oldLoader, newLoader, configuration.getPackages(), builder, definitionProviders)) {
                LOG.warn("""
                    The table [{}] of entity {} changed, but R2DBC data source [{}] generates its schema in CREATE mode, \
                    which only creates missing tables: a database that outlives the application, such as one the retained \
                    connection pool keeps open, keeps the previous table. To have the schema follow entity changes in \
                    development, set r2dbc.datasources.{}.schema-generate=CREATE_DROP in application-dev.properties \
                    (this drops and recreates the tables, and their rows, on each restart), or drop the table yourself.""",
                    changed.table(), changed.entity(), configuration.getName(), configuration.getName());
            }
        }
    }

    /**
     * Follows the class changes after the data beans are recreated, so that the schema is generated from the entity
     * registry of the new generation.
     */
    private final class SchemaWatcher implements ClassChangeWatcher, Ordered {
        @Override
        public void onChange(ClassChangeEvent change) {
            onClassChange(change);
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
