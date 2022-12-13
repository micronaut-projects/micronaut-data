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
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.jdbc.DelegatingDataSource;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
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

    private final List<DataJdbcConfiguration> configurations;
    private final JdbcSchemaHandler schemaHandler;

    /**
     * Constructors a schema generator for the given configurations.
     *
     * @param configurations The configurations
     * @param schemaHandler  The schema handler
     */
    public SchemaGenerator(List<DataJdbcConfiguration> configurations, JdbcSchemaHandler schemaHandler) {
        this.configurations = configurations == null ? Collections.emptyList() : configurations;
        this.schemaHandler = schemaHandler;
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
            SchemaGenerate schemaGenerate = configuration.getSchemaGenerate();
            if (schemaGenerate == null || schemaGenerate == SchemaGenerate.NONE) {
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
                .filter(i -> !java.lang.reflect.Modifier.isAbstract(i.getBeanType().getModifiers()))
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
                                generate(connection, configuration, entities);
                            }
                        } else {
                            if (configuration.getSchemaGenerateName() != null) {
                                schemaHandler.createSchema(connection, dialect, configuration.getSchemaGenerateName());
                                schemaHandler.useSchema(connection, dialect, configuration.getSchemaGenerateName());
                            }
                            generate(connection, configuration, entities);
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
                                 PersistentEntity[] entities) throws SQLException {
        Dialect dialect = configuration.getDialect();
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect);
        if (dialect.allowBatch() && configuration.isBatchGenerate()) {
            switch (configuration.getSchemaGenerate()) {
                case CREATE_DROP:
                    try {
                        String sql = builder.buildBatchDropTableStatement(entities);
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
                    String sql = builder.buildBatchCreateTableStatement(entities);
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
}
