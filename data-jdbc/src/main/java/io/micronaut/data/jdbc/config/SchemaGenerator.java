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
package io.micronaut.data.jdbc.config;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.JsonSubView;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils;
import io.micronaut.data.model.query.builder.sql.validation.SchemaValidationResult;
import io.micronaut.data.model.query.builder.sql.validation.SqlJsonViewValidator;
import io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
import io.micronaut.data.model.schema.sql.SqlJsonViewMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlJsonViewMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Schema generator used for testing purposes.
 */
@Context
@Internal
public class SchemaGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaGenerator.class);

    private final List<DataJdbcConfiguration> configurations;
    private final JdbcSchemaHandler schemaHandler;
    private final Map<Dialect, SqlTableMappingValidator> dialectSqlTableMappingValidatorMap;
    private final PropertyPlaceholderResolver propertyPlaceholderResolver;
    private final List<DefinitionProvider> definitionProviders;

    /**
     * Constructors a schema generator for the given configurations.
     *
     * @param configurations              The configurations
     * @param schemaHandler               The schema handler
     * @param sqlTableMappingValidators   The list of {@link SqlTableMappingValidator} instances
     * @param environment                 The environment
     * @param definitionProviders         Providers of vendor-specific SQL definitions (columns and indexes) used during schema generation
     */
    public SchemaGenerator(List<DataJdbcConfiguration> configurations,
                           JdbcSchemaHandler schemaHandler,
                           List<SqlTableMappingValidator> sqlTableMappingValidators,
                           Environment environment,
                           List<DefinitionProvider> definitionProviders) {
        this.configurations = configurations == null ? Collections.emptyList() : configurations;
        this.schemaHandler = schemaHandler;
        this.propertyPlaceholderResolver = environment.getPlaceholderResolver();
        this.dialectSqlTableMappingValidatorMap = CollectionUtils.newHashMap(sqlTableMappingValidators.size());
        for (SqlTableMappingValidator sqlTableMappingValidator : sqlTableMappingValidators) {
            Dialect dialect = sqlTableMappingValidator.getSupportedDialect();
            if (dialectSqlTableMappingValidatorMap.containsKey(dialect)) {
                throw new IllegalStateException("More than one SqlTableMappingValidator is declared for dialect " + dialect);
            }
            dialectSqlTableMappingValidatorMap.put(dialect, sqlTableMappingValidator);
        }
        this.definitionProviders = definitionProviders == null ? Collections.emptyList() : definitionProviders;
    }

    /**
     * Initializes or validates the schema for the configuration.
     *
     * @param beanLocator The bean locator
     */
    @PostConstruct
    public void createOrValidateSchema(BeanLocator beanLocator) {
        RuntimeEntityRegistry runtimeEntityRegistry = beanLocator.getBean(RuntimeEntityRegistry.class);
        for (DataJdbcConfiguration configuration : configurations) {
            boolean enabled = configuration.isEnabled();
            SchemaGenerate schemaGenerate = configuration.getSchemaGenerate();
            if (!enabled || schemaGenerate == null || schemaGenerate == SchemaGenerate.NONE) {
                if (!enabled && LOG.isDebugEnabled()) {
                    LOG.debug("The datasource [{}] is disabled, skipping schema generator.", configuration.getName());
                }
                continue;
            }
            Dialect dialect = configuration.getDialect();
            String name = configuration.getName();
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
                .map(beanIntrospection -> runtimeEntityRegistry.getEntity(beanIntrospection.getBeanType()))
                .toArray(PersistentEntity[]::new);
            if (ArrayUtils.isNotEmpty(entities)) {
                DataSource dataSource = DelegatingDataSource.unwrapDataSource(beanLocator.getBean(DataSource.class, Qualifiers.byName(name)));
                try {
                    try (Connection connection = dataSource.getConnection()) {
                        if (configuration.getSchemaGenerateNames() != null && !configuration.getSchemaGenerateNames().isEmpty()) {
                            for (String schemaName : configuration.getSchemaGenerateNames()) {
                                if (schemaGenerate != SchemaGenerate.VALIDATE) {
                                    schemaHandler.createSchema(connection, dialect, schemaName);
                                }
                                schemaHandler.useSchema(connection, dialect, schemaName);
                                if (schemaGenerate == SchemaGenerate.VALIDATE) {
                                    validate(connection, configuration, entities);
                                } else {
                                    generate(connection, configuration, propertyPlaceholderResolver, entities);
                                }
                            }
                        } else {
                            if (configuration.getSchemaGenerateName() != null) {
                                if (schemaGenerate != SchemaGenerate.VALIDATE) {
                                    schemaHandler.createSchema(connection, dialect, configuration.getSchemaGenerateName());
                                }
                                schemaHandler.useSchema(connection, dialect, configuration.getSchemaGenerateName());
                            }
                            if (schemaGenerate == SchemaGenerate.VALIDATE) {
                                validate(connection, configuration, entities);
                            } else {
                                generate(connection, configuration, propertyPlaceholderResolver, entities);
                            }
                        }
                    } catch (SQLException e) {
                        throw new DataAccessException("Unable to create database schema: " + e.getMessage(), e);
                    }
                } catch (NoSuchBeanException e) {
                    throw new ConfigurationException("No DataSource configured for setting [" + DataJdbcConfiguration.PREFIX + name + "]. Ensure the DataSource is configured correctly and try again.", e);
                }
            }
        }
    }

    @SuppressWarnings("java:S3776")
    private void generate(Connection connection,
                          DataJdbcConfiguration configuration,
                          PropertyPlaceholderResolver propertyPlaceholderResolver,
                          PersistentEntity[] entities) throws SQLException {
        Dialect dialect = configuration.getDialect();
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect, configuration.getDialectOptions().getVersion());
        if (dialect.allowBatch() && configuration.isBatchGenerate()) {
            switch (configuration.getSchemaGenerate()) {
                case CREATE_DROP:
                    try {
                        String sql = resolveSql(propertyPlaceholderResolver, builder.buildBatchDropTableStatement(entities));
                        if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                            DataSettings.QUERY_LOG.debug("Dropping Tables: \n{}", sql);
                        }
                        try (PreparedStatement ps = connection.prepareStatement(sql)) {
                            ps.executeUpdate();
                        }
                    } catch (SQLException e) {
                        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                            DataSettings.QUERY_LOG.trace("Drop Unsuccessful: " + e.getMessage());
                        }
                    }
                case CREATE:
                    String sql = resolveSql(propertyPlaceholderResolver, builder.buildBatchCreateTableStatement(definitionProviders, entities));
                    if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                        DataSettings.QUERY_LOG.debug("Creating Tables: \n{}", sql);
                    }
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        ps.executeUpdate();
                    }
                    break;
                default:
                    // do nothing
            }
        } else {
            switch (configuration.getSchemaGenerate()) {
                case CREATE_DROP:
                    for (PersistentEntity entity : entities) {
                        try {
                            String[] statements = builder.buildDropTableStatements(entity);
                            for (String sql : statements) {
                                sql = resolveSql(propertyPlaceholderResolver, sql);
                                if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                                    DataSettings.QUERY_LOG.debug("Dropping Table: \n{}", sql);
                                }
                                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                                    ps.executeUpdate();
                                }
                            }
                        } catch (SQLException e) {
                            if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                                DataSettings.QUERY_LOG.trace("Drop Unsuccessful: " + e.getMessage());
                            }
                        }
                    }
                case CREATE:
                    String[] sql = builder.buildCreateTableStatements(definitionProviders, entities, dialect);
                    for (String stmt : sql) {
                        stmt = resolveSql(propertyPlaceholderResolver, stmt);
                        if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                            DataSettings.QUERY_LOG.debug("Executing CREATE statement: \n{}", stmt);
                        }
                        try {
                            try (PreparedStatement ps = connection.prepareStatement(stmt)) {
                                ps.executeUpdate();
                            }
                        } catch (SQLException e) {
                            if (DataSettings.QUERY_LOG.isWarnEnabled()) {
                                DataSettings.QUERY_LOG.warn("CREATE Statement Unsuccessful: " + e.getMessage());
                            }
                        }
                    }
                    break;
                default:
                    // do nothing
            }
        }
    }

    @SuppressWarnings("java:S3776")
    private void validate(Connection connection,
                          DataJdbcConfiguration configuration,
                          PersistentEntity[] entities) throws SQLException {
        Dialect dialect = configuration.getDialect();
        SqlDialectOptions dialectOptions = SqlDialectOptions.of(dialect, configuration.getDialectOptions().getVersion());
        SqlTableMappingValidator sqlTableMappingValidator = dialectSqlTableMappingValidatorMap.get(dialect);
        if (sqlTableMappingValidator == null) {
            throw new IllegalStateException("There is no supported SqlTableMappingValidator for dialect " + dialect);
        }
        // Tables grouped by the schema declared in the mapping (empty for the connection default schema)
        Map<String, Map<String, SqlTableMapping>> sqlTableMappingsBySchema = getSqlTableMappingsBySchema(entities, dialect);

        SchemaValidationResult result = new SchemaValidationResult();
        JdbcSchemaMetadataReader metadataReader = new JdbcSchemaMetadataReader(connection, dialect);
        for (Map<String, SqlTableMapping> sqlTableMappings : sqlTableMappingsBySchema.values()) {
            String schema = sqlTableMappings.values().iterator().next().schema();
            JdbcSchemaMetadataReader.SchemaTables schemaTables = metadataReader.readTables(
                StringUtils.isNotEmpty(schema) ? schema : null, sqlTableMappings.keySet());
            String columnTypeDefinitionsQuery = sqlTableMappingValidator.getColumnTypeDefinitionsQuery();
            if (columnTypeDefinitionsQuery != null && sqlTableMappings.values().stream().anyMatch(SchemaGenerator::hasDefinedColumns)) {
                // Needed to verify the type arguments of columns with a definition, like the vector dimension
                metadataReader.readColumnTypeDefinitions(columnTypeDefinitionsQuery, schemaTables);
            }
            Set<String> sequenceNames = null;
            boolean sequencesRead = false;
            for (Map.Entry<String, SqlTableMapping> sqlTableMappingEntry : sqlTableMappings.entrySet()) {
                SqlTableMapping sqlTableMapping = sqlTableMappingEntry.getValue();
                SqlTableMetadata dbSqlTableMetadata = schemaTables.tables().get(sqlTableMappingEntry.getKey());
                if (dbSqlTableMetadata == null) {
                    String tableName = StringUtils.isNotEmpty(schema) ? schema + "." + sqlTableMapping.name() : sqlTableMapping.name();
                    result.addError("Expected table [" + tableName + "] not found");
                    continue;
                }
                sqlTableMappingValidator.validateTable(sqlTableMapping, dbSqlTableMetadata, dialectOptions, result);
                if (sqlTableMapping.sequences().stream().anyMatch(sequence -> SqlSchemaUtils.requiresSequence(sequence, dialect))) {
                    if (!sequencesRead) {
                        sequenceNames = readSequenceNames(metadataReader, sqlTableMappingValidator, schemaTables.schema(), result);
                        sequencesRead = true;
                    }
                    if (sequenceNames != null) {
                        sqlTableMappingValidator.validateSequences(sqlTableMapping, sequenceNames, dialectOptions, result);
                    }
                }
            }
        }
        validateJsonViews(metadataReader, entities, dialect, result);
        if (LOG.isWarnEnabled()) {
            for (String warning : result.getWarnings()) {
                LOG.warn("Schema validation warning for datasource [{}]: {}", configuration.getName(), warning);
            }
        }
        result.throwIfErrors();
    }

    /**
     * Validates the Oracle JSON relational duality views of the {@link JsonView} entities.
     * JSON views are only supported (created) for Oracle, they are skipped for the other dialects.
     */
    private static void validateJsonViews(JdbcSchemaMetadataReader metadataReader,
                                          PersistentEntity[] entities,
                                          Dialect dialect,
                                          SchemaValidationResult result) {
        if (dialect != Dialect.ORACLE) {
            // JSON views are only created for Oracle
            return;
        }
        List<SqlJsonViewMapping> jsonViewMappings = new ArrayList<>();
        for (PersistentEntity entity : entities) {
            if (entity.getAnnotationMetadata().hasAnnotation(JsonView.class)) {
                SqlJsonViewMapping jsonViewMapping = SqlSchemaUtils.getSqlJsonViewMapping(entity);
                if (jsonViewMapping != null) {
                    jsonViewMappings.add(jsonViewMapping);
                }
            }
        }
        if (jsonViewMappings.isEmpty()) {
            return;
        }
        Map<String, List<SqlJsonViewMapping>> jsonViewMappingsBySchema = jsonViewMappings.stream()
            .collect(Collectors.groupingBy(mapping -> schemaKey(mapping.schema()), LinkedHashMap::new, Collectors.toList()));
        for (List<SqlJsonViewMapping> schemaJsonViewMappings : jsonViewMappingsBySchema.values()) {
            String schema = schemaJsonViewMappings.getFirst().schema();
            Map<String, SqlJsonViewMetadata> jsonViews;
            try {
                jsonViews = metadataReader.readJsonDualityViews(StringUtils.isNotEmpty(schema) ? schema : null);
            } catch (SQLException e) {
                result.addWarning("Unable to read the JSON views of schema [" + schema + "], the JSON views are not validated: " + e.getMessage());
                continue;
            }
            for (SqlJsonViewMapping jsonViewMapping : schemaJsonViewMappings) {
                SqlJsonViewValidator.validate(jsonViewMapping, jsonViews.get(jsonViewMapping.name().toLowerCase(Locale.ENGLISH)), result);
            }
        }
    }

    private Map<String, Map<String, SqlTableMapping>> getSqlTableMappingsBySchema(PersistentEntity[] entities, Dialect dialect) {
        // Get all tables for all entities and remove (de-duplicate) if there is SqlTableMapping created from the entity
        // that represents join and ad-hoc SqlTableMapping for the same entity based on relation mappings (to be removed/skipped)
        Map<String, SqlTableMapping> sqlTableMappingByTableName = CollectionUtils.newLinkedHashMap(entities.length);
        for (PersistentEntity entity : entities) {
            if (entity.getAnnotationMetadata().hasAnnotation(JsonView.class)) {
                continue;
            }
            List<SqlTableMapping> sqlTableMappings = SqlSchemaUtils.getSqlTableMappings(definitionProviders, entity, dialect);
            for (SqlTableMapping sqlTableMapping : sqlTableMappings) {
                String key = schemaKey(sqlTableMapping.schema()) + "." + sqlTableMapping.name().toLowerCase(Locale.ENGLISH);
                SqlTableMapping existingSqlTableMapping = sqlTableMappingByTableName.get(key);
                if (existingSqlTableMapping != null) {
                    if (existingSqlTableMapping.type() == SqlTableMapping.TableType.JOIN) {
                        // Remove ad-hoc join table created from one of the entities relation mappings and not an actual entity
                        sqlTableMappingByTableName.remove(key);
                    } else if (sqlTableMapping.type() == SqlTableMapping.TableType.JOIN) {
                        // Skip this table mapping ad-hoc join table created from one of the entities relation mappings and not an actual entity
                        continue;
                    }
                }
                sqlTableMappingByTableName.put(key, sqlTableMapping);
            }
        }
        return sqlTableMappingByTableName.values().stream()
            .collect(Collectors.groupingBy(sqlTableMapping -> schemaKey(sqlTableMapping.schema()), LinkedHashMap::new,
                Collectors.toMap(sqlTableMapping -> sqlTableMapping.name().toLowerCase(Locale.ENGLISH), sqlTableMapping -> sqlTableMapping,
                    (first, second) -> first, LinkedHashMap::new)));
    }

    /**
     * @return The lower case sequence names or null if the sequences cannot be read
     */
    private static @Nullable Set<String> readSequenceNames(JdbcSchemaMetadataReader metadataReader,
                                                           SqlTableMappingValidator sqlTableMappingValidator,
                                                           @Nullable String schema,
                                                           SchemaValidationResult result) {
        String query = sqlTableMappingValidator.getSequenceNamesQuery();
        if (query == null) {
            return null;
        }
        try {
            return metadataReader.readSequenceNames(query, schema);
        } catch (SQLException e) {
            result.addWarning("Unable to read the sequences of schema [" + schema + "], the sequences are not validated: " + e.getMessage());
            return null;
        }
    }

    private static boolean hasDefinedColumns(SqlTableMapping sqlTableMapping) {
        return Stream.concat(sqlTableMapping.primaryKeyColumns().stream(), sqlTableMapping.columns().stream())
            .anyMatch(column -> StringUtils.isNotEmpty(column.getDefinition()));
    }

    private static String schemaKey(@Nullable String schema) {
        return schema == null ? "" : schema.toLowerCase(Locale.ENGLISH);
    }

    /**
     * Resolves property placeholder values if there are any.
     *
     * @param propertyPlaceholderResolver The property placeholder resolver
     * @param sql The SQL to resolve placeholder properties if there are any
     * @return The resulting SQL with resolved properties if there were any
     */
    private static String resolveSql(PropertyPlaceholderResolver propertyPlaceholderResolver, String sql) {
        if (sql.contains(propertyPlaceholderResolver.getPrefix())) {
            return propertyPlaceholderResolver.resolveRequiredPlaceholders(sql);
        }
        return sql;
    }
}
