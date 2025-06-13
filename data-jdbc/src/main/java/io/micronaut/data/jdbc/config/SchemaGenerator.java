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
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder2;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Schema generator used for testing purposes.
 */
@Context
@Internal
public class SchemaGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaGenerator.class);

    private final List<DataJdbcConfiguration> configurations;
    private final JdbcSchemaHandler schemaHandler;
    private final PropertyPlaceholderResolver propertyPlaceholderResolver;

    /**
     * Constructors a schema generator for the given configurations.
     *
     * @param configurations The configurations
     * @param schemaHandler  The schema handler
     * @param environment    The environment
     */
    public SchemaGenerator(List<DataJdbcConfiguration> configurations,
                           JdbcSchemaHandler schemaHandler,
                           Environment environment) {
        this.configurations = configurations == null ? Collections.emptyList() : configurations;
        this.schemaHandler = schemaHandler;
        this.propertyPlaceholderResolver = environment.getPlaceholderResolver();
    }

    /**
     * Initialize the schema for the configuration.
     *
     * @param beanLocator The bean locator
     */
    @PostConstruct
    public void createSchema(BeanLocator beanLocator) {
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
                .filter(i -> !i.hasAnnotation(JsonView.class))
                .map(beanIntrospection -> runtimeEntityRegistry.getEntity(beanIntrospection.getBeanType()))
                .toArray(PersistentEntity[]::new);
            if (ArrayUtils.isNotEmpty(entities)) {
                DataSource dataSource = DelegatingDataSource.unwrapDataSource(beanLocator.getBean(DataSource.class, Qualifiers.byName(name)));
                try {
                    try (Connection connection = dataSource.getConnection()) {
                        if (configuration.getSchemaGenerateNames() != null && !configuration.getSchemaGenerateNames().isEmpty()) {
                            for (String schemaName : configuration.getSchemaGenerateNames()) {
                                schemaHandler.createSchema(connection, dialect, schemaName);
                                schemaHandler.useSchema(connection, dialect, schemaName);
                                generate(connection, configuration, propertyPlaceholderResolver, entities);
                            }
                        } else {
                            if (configuration.getSchemaGenerateName() != null) {
                                schemaHandler.createSchema(connection, dialect, configuration.getSchemaGenerateName());
                                schemaHandler.useSchema(connection, dialect, configuration.getSchemaGenerateName());
                            }
                            generate(connection, configuration, propertyPlaceholderResolver, entities);
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

    private static void generate(Connection connection,
                                 DataJdbcConfiguration configuration,
                                 PropertyPlaceholderResolver propertyPlaceholderResolver,
                                 PersistentEntity[] entities) throws SQLException {
        Dialect dialect = configuration.getDialect();
        SqlQueryBuilder2 builder = new SqlQueryBuilder2(dialect);
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
                    String sql = resolveSql(propertyPlaceholderResolver, builder.buildBatchCreateTableStatement(entities));
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
                    for (PersistentEntity entity : entities) {

                        String[] sql = builder.buildCreateTableStatements(entity);
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

                    }


                    break;
                default:
                    // do nothing
            }
        }
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
