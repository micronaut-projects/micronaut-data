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
import io.micronaut.data.model.query.builder.sql.validation.SchemaValidationException;
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.IdentifierNamingStrategy;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils;
import io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

/**
 * Schema generator used for testing purposes.
 */
@Context
@Internal
public class SchemaGenerator {

    private static final String MATCH_ALL = "%";

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
                                    validate(connection, configuration, entities, dialectSqlTableMappingValidatorMap, definitionProviders);
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
                                validate(connection, configuration, entities, dialectSqlTableMappingValidatorMap, definitionProviders);
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
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect);
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
                    String[] sql = builder.buildCreateTableStatements(definitionProviders, entities, dialect, configuration.resolveDialectOptions());
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
    private static void validate(Connection connection,
                                 DataJdbcConfiguration configuration,
                                 PersistentEntity[] entities,
                                 Map<Dialect, SqlTableMappingValidator> dialectSqlTableMappingValidatorMap,
                                 List<DefinitionProvider> definitionProviders) throws SQLException {
        Dialect dialect = configuration.getDialect();
        SqlTableMappingValidator sqlTableMappingValidator = dialectSqlTableMappingValidatorMap.get(dialect);
        if (sqlTableMappingValidator == null) {
            throw new IllegalStateException("There is no supported SqlTableMappingValidator for dialect " + dialect);
        }
        // Get all tables for all entities and remove (de-duplicate) if there is SqlTableMapping created from the entity
        // that represents join and ad-hoc SqlTableMapping for the same entity based on relation mappings (to be removed/skipped)
        Map<String, SqlTableMapping> sqlTableMappingByTableName = CollectionUtils.newLinkedHashMap(entities.length);
        for (PersistentEntity entity : entities) {
            if (entity.getAnnotationMetadata().hasAnnotation(JsonView.class)) {
                continue;
            }
            List<SqlTableMapping> sqlTableMappings = SqlSchemaUtils.getSqlTableMappings(definitionProviders, entity, dialect);
            for (SqlTableMapping sqlTableMapping : sqlTableMappings) {
                String tableName = sqlTableMapping.name();
                String tableNameLowerCase = tableName.toLowerCase();
                if (sqlTableMappingByTableName.containsKey(tableNameLowerCase)) {
                    SqlTableMapping existingSqlTableMapping = sqlTableMappingByTableName.get(tableNameLowerCase);
                    if (existingSqlTableMapping.type() == SqlTableMapping.TableType.JOIN) {
                        // Remove ad-hoc join table created from one of the entities relation mappings and not an actual entity
                        sqlTableMappingByTableName.remove(tableNameLowerCase);
                    } else if (sqlTableMapping.type() == SqlTableMapping.TableType.JOIN) {
                        // Skip this table mapping ad-hoc join table created from one of the entities relation mappings and not an actual entity
                        continue;
                    }
                }
                sqlTableMappingByTableName.put(tableNameLowerCase, sqlTableMapping);
            }
        }

        Map<String, SqlTableMetadata> dbSqlTableMetadataMap = getDbSqlTableMetadataList(connection, sqlTableMappingByTableName.keySet());
        for (Map.Entry<String, SqlTableMapping> sqlTableMappingEntry : sqlTableMappingByTableName.entrySet()) {
            String tableNameLowerCase = sqlTableMappingEntry.getKey();
            SqlTableMapping sqlTableMapping = sqlTableMappingEntry.getValue();
            SqlTableMetadata dbSqlTableMetadata = dbSqlTableMetadataMap.get(tableNameLowerCase);
            if (dbSqlTableMetadata == null) {
                throw new SchemaValidationException("Schema validation failed. Expected table [" + sqlTableMapping.name() + "] not found");
            }
            sqlTableMappingValidator.validateTable(sqlTableMapping, dbSqlTableMetadata);
        }
    }

    private static Map<String, SqlTableMetadata> getDbSqlTableMetadataList(Connection connection,
                                                                           Set<String> wantedTableNames) throws SQLException {
        Map<String, SqlTableMetadata> sqlTableMetadataList = CollectionUtils.newHashMap(50);
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        String[] tableTypes = { SqlSchemaUtils.TABLE_TYPE };
        DatabaseMetaData metaData = connection.getMetaData();
        IdentifierNamingStrategy namingStrategy = getIdentifierNamingStrategy(metaData);
        catalog = namingStrategy.apply(catalog);
        schema = namingStrategy.apply(schema);
        // Some dialects won't support both catalog and schema
        // Get tables
        ResultSet tablesResultSet = metaData.getTables(catalog, schema, MATCH_ALL, tableTypes);
        while (tablesResultSet.next()) {
            String tableName = tablesResultSet.getString(SqlSchemaUtils.TABLE_NAME_COLUMN);
            String tableNameLowerCase = tableName.toLowerCase();
            if (!wantedTableNames.contains(tableNameLowerCase)) {
                // Skip table that does not have entity mapped
                continue;
            }
            String tableCatalog = tablesResultSet.getString(SqlSchemaUtils.TABLE_CATALOG_COLUMN);
            String tableSchema = tablesResultSet.getString(SqlSchemaUtils.TABLE_SCHEMA_COLUMN);
            SqlTableMetadata sqlTableMetadata = new SqlTableMetadata(tableCatalog, tableSchema, tableName);
            sqlTableMetadataList.put(tableNameLowerCase, sqlTableMetadata);
        }
        // Get columns
        populateSqlColumnMetadata(metaData, catalog, schema, sqlTableMetadataList);
        return sqlTableMetadataList;
    }

    private static void populateSqlColumnMetadata(DatabaseMetaData metaData, String catalog, String schema,
                                           Map<String, SqlTableMetadata> sqlTableMetadataMap) throws SQLException {
        ResultSet columnsResultSet = metaData.getColumns(catalog, schema, null, MATCH_ALL);
        SqlTableMetadata sqlTableMetadata = null;
        String currentTableName = StringUtils.EMPTY_STRING;
        while (columnsResultSet.next()) {
            String tableName = columnsResultSet.getString(SqlSchemaUtils.TABLE_NAME_COLUMN).toLowerCase();
            if (!sqlTableMetadataMap.containsKey(tableName)) {
                // No need to populate columns for the table which does not have mapped entity
                continue;
            }
            if (!currentTableName.equals(tableName)) {
                currentTableName = tableName;
                sqlTableMetadata = sqlTableMetadataMap.get(currentTableName);
            }
            if (sqlTableMetadata != null) {
                addExtractedColumnInformation(sqlTableMetadata, columnsResultSet);
            }
        }
    }

    private static void addExtractedColumnInformation(SqlTableMetadata sqlTableMetadata, ResultSet columnsResultSet) throws SQLException {
        String columnName = columnsResultSet.getString(SqlSchemaUtils.COLUMN_NAME_COLUMN);
        int columnType = columnsResultSet.getInt(SqlSchemaUtils.DATA_TYPE_COLUMN);
        String typeName = columnsResultSet.getString(SqlSchemaUtils.TYPE_NAME_COLUMN);
        int columnSize = columnsResultSet.getInt(SqlSchemaUtils.COLUMN_SIZE_COLUMN);
        // the number of fractional digits. Null is returned for data types where DECIMAL_DIGITS is not applicable.
        int decimalDigits = columnsResultSet.getInt(SqlSchemaUtils.DECIMAL_DIGITS_COLUMN);
        int nullable = columnsResultSet.getInt(SqlSchemaUtils.NULLABLE_COLUMN);
        sqlTableMetadata.addColumn(new SqlColumnMetadata(columnName, columnType, typeName,
            columnSize, decimalDigits, nullable == 1));
    }

    private static IdentifierNamingStrategy getIdentifierNamingStrategy(DatabaseMetaData metaData) throws SQLException {
        if (metaData.storesUpperCaseIdentifiers()) {
            return IdentifierNamingStrategy.UPPER;
        }
        if (metaData.storesLowerCaseIdentifiers()) {
            return IdentifierNamingStrategy.LOWER;
        }
        // default MIXED
        return IdentifierNamingStrategy.MIXED;
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
