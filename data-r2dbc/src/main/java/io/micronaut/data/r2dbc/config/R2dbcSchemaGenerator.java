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
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.r2dbc.operations.R2dbcSchemaHandler;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.data.runtime.config.SqlDialectOptionsConfiguration;
import io.micronaut.data.runtime.schema.SchemaContext;
import io.micronaut.data.runtime.schema.SchemaContributions;
import io.r2dbc.spi.Connection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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
    private final List<DefinitionProvider> definitionProviders;
    private final List<R2dbcSchemaContributor> schemaContributors;

    /**
     * Default constructor.
     *
     * @param configurations     The configurations.
     * @param schemaHandler      The schema handler
     * @param definitionProviders Providers of vendor-specific SQL definitions
     */
    public R2dbcSchemaGenerator(List<DataR2dbcConfiguration> configurations,
                                R2dbcSchemaHandler schemaHandler,
                                List<DefinitionProvider> definitionProviders) {
        this(configurations, schemaHandler, definitionProviders, List.of());
    }

    /**
     * Constructor with schema contributors.
     *
     * @param configurations      The configurations.
     * @param schemaHandler       The schema handler
     * @param definitionProviders Providers of vendor-specific SQL definitions
     * @param schemaContributors  Contributors that generate the schema of some entities, such as the tables and property graphs of graph entities
     * @since 5.3.0
     */
    @Inject
    public R2dbcSchemaGenerator(List<DataR2dbcConfiguration> configurations,
                                R2dbcSchemaHandler schemaHandler,
                                List<DefinitionProvider> definitionProviders,
                                List<R2dbcSchemaContributor> schemaContributors) {
        this.schemaContributors = schemaContributors == null ? java.util.Collections.emptyList() : schemaContributors;
        this.configurations = configurations;
        this.schemaHandler = schemaHandler;
        this.definitionProviders = definitionProviders == null ? java.util.Collections.emptyList() : definitionProviders;
    }

    /**
     * Creates the schema.
     *
     * @param beanLocator The bean locator
     */
    @PostConstruct
    protected void createSchema(BeanLocator beanLocator) {
        RuntimeEntityRegistry runtimeEntityRegistry = beanLocator.getBean(RuntimeEntityRegistry.class);
        PropertyPlaceholderResolver placeholderResolver = beanLocator.getBean(Environment.class).getPlaceholderResolver();
        for (DataR2dbcConfiguration configuration : configurations) {

            SchemaGenerate schemaGenerate = configuration.getSchemaGenerate();
            if (schemaGenerate == SchemaGenerate.VALIDATE) {
                throw new IllegalStateException("Micronaut Data does not currently support validation of R2dbc data sources.");
            }
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
                        .filter(i -> !Modifier.isAbstract(i.getBeanType().getModifiers()))
                        .filter(i -> !i.hasAnnotation(JsonSubView.class))
                        .sorted(Comparator.comparing(i -> i.hasAnnotation(JsonView.class)))
                        .map(e -> runtimeEntityRegistry.getEntity(e.getBeanType())).toArray(PersistentEntity[]::new);
                SchemaContributions contributions = resolvePlaceholders(placeholderResolver, SchemaContributions.of(schemaContributors,
                    new SchemaContext(configuration.getName(), configuration.getDialect(), configuration.getDialectOptions().getVersion(), schemaGenerate, Arrays.asList(entities))));
                PersistentEntity[] tableEntities = contributions.tableEntities();
                if (!contributions.isEmpty()) {
                    Mono.from(configuration.getConnectionFactory().create()).flatMap(connection -> {
                        Dialect dialect = configuration.getDialect();
                        String dialectVersion = configuration.getDialectOptions().resolveVersion(() -> detectVersion(connection, configuration.getName()));
                        SqlQueryBuilder builder = new SqlQueryBuilder(dialect, dialectVersion);
                        if (configuration.getSchemaGenerateNames() != null && !configuration.getSchemaGenerateNames().isEmpty()) {
                            Mono<Void> result = Mono.empty();
                            for (String schemaName : configuration.getSchemaGenerateNames()) {
                                result = result.then(Mono.from(schemaHandler.createSchema(connection, dialect, schemaName)))
                                    .then(Mono.from(schemaHandler.useSchema(connection, dialect, schemaName)))
                                    .then(generate(connection, configuration, tableEntities, builder, contributions));
                            }
                            return restoringOracleSchema(connection, dialect, result).then(Mono.from(connection.close()));
                        }
                        Mono<Void> result = Mono.empty();
                        if (configuration.getSchemaGenerateName() != null) {
                            result = Mono.from(schemaHandler.createSchema(connection, dialect, configuration.getSchemaGenerateName()))
                                .then(Mono.from(schemaHandler.useSchema(connection, dialect, configuration.getSchemaGenerateName())));
                        }
                        return restoringOracleSchema(connection, dialect, result.then(generate(connection, configuration, tableEntities, builder, contributions)))
                            .then(Mono.from(connection.close()));
                    }).block();
                }
            }
        }
    }

    /**
     * Resolves the placeholders of the contributed statements like JDBC schema generation, before a connection is
     * opened, so that a missing property fails the generation without a connection to close.
     */
    private static SchemaContributions resolvePlaceholders(PropertyPlaceholderResolver placeholderResolver, SchemaContributions contributions) {
        return new SchemaContributions(contributions.tableEntities(),
            resolvePlaceholders(placeholderResolver, contributions.dropStatements()),
            resolvePlaceholders(placeholderResolver, contributions.beforeTablesDropStatements()),
            resolvePlaceholders(placeholderResolver, contributions.beforeTablesStatements()),
            resolvePlaceholders(placeholderResolver, contributions.createStatements()));
    }

    private static List<String> resolvePlaceholders(PropertyPlaceholderResolver placeholderResolver, List<String> statements) {
        return statements.stream()
            .map(sql -> sql.contains(placeholderResolver.getPrefix()) ? placeholderResolver.resolveRequiredPlaceholders(sql) : sql)
            .toList();
    }

    static @Nullable String detectVersion(Connection connection, String dataSourceName) {
        String versionText;
        try {
            versionText = connection.getMetadata().getDatabaseVersion();
        } catch (RuntimeException e) {
            // Oracle R2DBC rethrows a SQLException of the JDBC metadata as an R2dbcException
            LOG.warn("Unable to detect the database version of datasource [{}]: schema generation uses no target version", dataSourceName, e);
            return null;
        }
        String version = SqlDialectOptionsConfiguration.parseMajorMinor(versionText);
        if (version == null) {
            LOG.warn("Unable to detect the database version of datasource [{}] from [{}]: schema generation uses no target version",
                dataSourceName, versionText);
        } else if (LOG.isInfoEnabled()) {
            LOG.info("Schema generation for datasource [{}] targets the detected database version {}", dataSourceName, version);
        }
        return version;
    }

    private Mono<Void> generate(Connection connection,
                                DataR2dbcConfiguration configuration,
                                PersistentEntity[] entities,
                                SqlQueryBuilder builder,
                                SchemaContributions contributions) {
        // Contributed objects over the tables, such as property graphs, are dropped before the tables, and objects the
        // tables use, such as domains, after them, so that both can be created again
        // The tables are dropped one by one even with batch generation, ignoring the failures: in a batch, a table that
        // doesn't exist yet would prevent the next tables from being dropped, and the creation would keep their old data
        Mono<Void> dropTables = entities.length > 0 && configuration.getSchemaGenerate() == SchemaGenerate.CREATE_DROP
            ? dropTablesOneByOne(connection, entities, builder)
            : Mono.empty();
        Mono<Void> createTables = entities.length > 0 ? createTables(connection, configuration, entities, builder) : Mono.empty();
        return executeDropStatements(connection, contributions.dropStatements())
            .then(dropTables)
            .thenMany(executeDropStatements(connection, contributions.beforeTablesDropStatements()))
            .thenMany(executeCreateStatements(connection, contributions.beforeTablesStatements()))
            .then(createTables)
            .thenMany(executeCreateStatements(connection, contributions.createStatements()))
            .then();
    }

    private Flux<Void> executeDropStatements(Connection connection, List<String> statements) {
        return Flux.fromIterable(statements)
            .concatMap(sql -> {
                if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                    DataSettings.QUERY_LOG.debug("Executing DROP statement: \n{}", sql);
                }
                return execute(connection, sql)
                    .onErrorResume(throwable -> {
                        // Contributed DROP statements tolerate missing objects, so a failure, such as a foreign key from another table, is a warning
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("DROP Statement Unsuccessful: {}, {}", throwable.getMessage(), sql);
                        }
                        return Mono.empty();
                    });
            });
    }

    private Flux<Void> executeCreateStatements(Connection connection, List<String> statements) {
        return Flux.fromIterable(statements)
            .concatMap(sql -> {
                if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                    DataSettings.QUERY_LOG.debug("Executing CREATE statement: \n{}", sql);
                }
                return execute(connection, sql)
                    .onErrorResume(throwable -> {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("CREATE Statement Unsuccessful: {}, {}", throwable.getMessage(), sql);
                        }
                        return Mono.empty();
                    });
            });
    }

    private Mono<Void> createTables(Connection connection,
                                    DataR2dbcConfiguration configuration,
                                    PersistentEntity[] entities,
                                    SqlQueryBuilder builder) {
        List<String> createStatements = Arrays.asList(
            builder.buildCreateTableStatements(definitionProviders, entities, builder.getDialect())
        );
        Flux<Void> createTablesFlow = Flux.fromIterable(createStatements)
                .concatMap(sql -> {
                    if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                        DataSettings.QUERY_LOG.debug("Creating Table: \n{}", sql);
                    }
                    return execute(connection, sql)
                            .onErrorResume((throwable -> {
                                if (LOG.isWarnEnabled()) {
                                    LOG.warn("Unable to create table :{}, {}", throwable.getMessage(), sql);
                                }
                                return Mono.empty();
                            }));
                });
        // Oracle R2DBC doesn't execute several statements in one string
        if (configuration.getDialect().allowBatch() && configuration.getDialect() != Dialect.ORACLE && configuration.isBatchGenerate()) {
            return createTablesInBatch(connection, createStatements, createTablesFlow.then());
        }
        return createTablesFlow.then();
    }

    private Mono<Void> dropTablesOneByOne(Connection connection,
                                          PersistentEntity[] entities,
                                          SqlQueryBuilder builder) {
        List<String> dropStatements = Arrays.stream(entities).flatMap(entity -> Arrays.stream(builder.buildDropTableStatements(entity)))
                .toList();
        return Flux.fromIterable(dropStatements)
                .concatMap(sql -> {
                    if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                        DataSettings.QUERY_LOG.debug("Dropping Table: \n{}", sql);
                    }
                    return execute(connection, sql)
                            .onErrorResume(throwable -> Mono.empty());
                })
                .then();
    }

    private Mono<Void> createTablesInBatch(Connection connection,
                                           List<String> createStatements,
                                           Mono<Void> createTablesOneByOne) {
        return Mono.defer(() -> {
            // The same statements as without batch generation, which don't repeat a schema or a join table
            // shared by several entities. They are executed together: each one needs to be terminated
            String sql = createStatements.stream()
                .map(statement -> statement.endsWith(";") ? statement : statement + ";")
                .collect(Collectors.joining(System.lineSeparator()));
            if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                DataSettings.QUERY_LOG.debug("Creating Tables: \n{}", sql);
            }
            return execute(connection, sql);
        }).onErrorResume(throwable -> {
            // For example a schema or a table that already exists: like without batch generation,
            // create the tables one by one, ignoring the statements that fail
            if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                DataSettings.QUERY_LOG.debug("Batch create unsuccessful, creating the tables one by one: {}", throwable.getMessage());
            }
            return createTablesOneByOne;
        });
    }

    /**
     * Generation switches the session schema on Oracle, and the connection may go back to a pool, so the
     * schema in use before the generation is restored afterwards.
     */
    private Mono<Void> restoringOracleSchema(Connection connection, Dialect dialect, Mono<Void> generation) {
        if (dialect != Dialect.ORACLE) {
            return generation;
        }
        Mono<String> currentSchema = Flux.from(connection.createStatement("SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') FROM DUAL").execute())
            .flatMap(result -> result.map((row, metadata) -> row.get(0, String.class)))
            .next();
        return Mono.usingWhen(
            currentSchema,
            schema -> generation,
            schema -> restoreOracleSchema(connection, schema),
            (schema, error) -> restoreOracleSchema(connection, schema),
            schema -> restoreOracleSchema(connection, schema)
        );
    }

    private Mono<Void> restoreOracleSchema(Connection connection, String schema) {
        // The exact name reported by the database, bound and quoted by the database so that its case is kept
        return Flux.from(connection.createStatement(
                "BEGIN EXECUTE IMMEDIATE 'ALTER SESSION SET CURRENT_SCHEMA = ' || DBMS_ASSERT.ENQUOTE_NAME(?, FALSE); END;")
                .bind(0, schema)
                .execute())
            .flatMap(result -> Flux.from(result.getRowsUpdated()))
            .then();
    }

    private Mono<Void> execute(Connection connection, String sql) {
        return Flux.from(connection.createStatement(sql).execute())
                .flatMap(result -> Flux.from(result.getRowsUpdated()))
                .collectList()
                .then();
    }
}
