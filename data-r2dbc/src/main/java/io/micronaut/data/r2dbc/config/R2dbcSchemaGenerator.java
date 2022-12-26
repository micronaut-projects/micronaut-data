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
package io.micronaut.data.r2dbc.config;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.r2dbc.operations.R2dbcSchemaHandler;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.r2dbc.spi.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Schema generation for R2DBC.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Context
@Internal
public class R2dbcSchemaGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(R2dbcSchemaGenerator.class);
    private final List<DataR2dbcConfiguration> configurations;
    private final R2dbcSchemaHandler schemaHandler;

    /**
     * Default constructor.
     *
     * @param configurations     The configurations.
     * @param schemaHandler
     */
    public R2dbcSchemaGenerator(List<DataR2dbcConfiguration> configurations, R2dbcSchemaHandler schemaHandler) {
        this.configurations = configurations;
        this.schemaHandler = schemaHandler;
    }

    /**
     * Creates the schema.
     *
     * @param beanLocator The bean locator
     */
    @PostConstruct
    protected void createSchema(BeanLocator beanLocator) {
        RuntimeEntityRegistry runtimeEntityRegistry = beanLocator.getBean(RuntimeEntityRegistry.class);
        for (DataR2dbcConfiguration configuration : configurations) {

            SchemaGenerate schemaGenerate = configuration.getSchemaGenerate();
            if (schemaGenerate != null && schemaGenerate != SchemaGenerate.NONE) {
                List<String> packages = configuration.getPackages();

                Collection<BeanIntrospection<Object>> introspections;
                if (CollectionUtils.isNotEmpty(packages)) {
                    introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class, packages.toArray(new String[0]));
                } else {
                    introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class);
                }
                PersistentEntity[] entities = introspections.stream()
                        // filter out inner / internal / abstract(MappedSuperClass) classes
                        .filter(i -> !i.getBeanType().getName().contains("$"))
                        .filter(i -> !java.lang.reflect.Modifier.isAbstract(i.getBeanType().getModifiers()))
                        .map(e -> runtimeEntityRegistry.getEntity(e.getBeanType())).toArray(PersistentEntity[]::new);
                if (ArrayUtils.isNotEmpty(entities)) {
                    SqlQueryBuilder builder = new SqlQueryBuilder(configuration.getDialect());
                    Mono.from(configuration.getConnectionFactory().create()).flatMap(connection -> {
                        Dialect dialect = configuration.getDialect();
                        if (configuration.getSchemaGenerateNames() != null && !configuration.getSchemaGenerateNames().isEmpty()) {
                            Mono<Void> result = Mono.empty();
                            for (String schemaName : configuration.getSchemaGenerateNames()) {
                                result = result.then(Mono.from(schemaHandler.createSchema(connection, dialect, schemaName)))
                                    .then(Mono.from(schemaHandler.useSchema(connection, dialect, schemaName)))
                                    .then(generate(connection, schemaGenerate, entities, builder));
                            }
                            return result.then(Mono.from(connection.close()));
                        }
                        Mono<Void> result = Mono.empty();
                        if (configuration.getSchemaGenerateName() != null) {
                            result = Mono.from(schemaHandler.createSchema(connection, dialect, configuration.getSchemaGenerateName()))
                                .then(Mono.from(schemaHandler.useSchema(connection, dialect, configuration.getSchemaGenerateName())));
                        }
                        return result.then(generate(connection, schemaGenerate, entities, builder))
                            .then(Mono.from(connection.close()));
                    }).block();
                }
            }
        }
    }

    private Mono<Void> generate(Connection connection, SchemaGenerate schemaGenerate, PersistentEntity[] entities, SqlQueryBuilder builder) {
        List<String> createStatements = Arrays.stream(entities)
                .flatMap(entity -> Arrays.stream(builder.buildCreateTableStatements(entity)))
                .collect(Collectors.toList());
        Flux<Void> createTablesFlow = Flux.fromIterable(createStatements)
                .concatMap(sql -> {
                    if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                        DataSettings.QUERY_LOG.debug("Creating Table: \n{}", sql);
                    }
                    return execute(connection, sql)
                            .onErrorResume((throwable -> {
                                if (LOG.isWarnEnabled()) {
                                    LOG.warn("Unable to create table :{}", throwable.getMessage());
                                }
                                return Mono.empty();
                            }));
                });
        switch (schemaGenerate) {
            case CREATE_DROP:
                List<String> dropStatements = Arrays.stream(entities).flatMap(entity -> Arrays.stream(builder.buildDropTableStatements(entity)))
                                                    .collect(Collectors.toList());
                return Flux.fromIterable(dropStatements)
                    .concatMap(sql -> {
                        if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                            DataSettings.QUERY_LOG.debug("Dropping Table: \n{}", sql);
                        }
                        return execute(connection, sql)
                            .onErrorResume((throwable -> Mono.empty()));
                    })
                    .thenMany(createTablesFlow)
                    .then();
            case CREATE:
            default:
                return createTablesFlow
                    .then();
        }
    }

    private Mono<Void> execute(Connection connection, String sql) {
        return Flux.from(connection.createStatement(sql).execute())
                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                .collectList()
                .then();
    }
}
