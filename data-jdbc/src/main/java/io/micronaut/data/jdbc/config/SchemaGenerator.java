/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.inject.qualifiers.Qualifiers;

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

    /**
     * Constructors a schema generator for the given configurations.
     *
     * @param configurations The configurations
     */
    public SchemaGenerator(List<DataJdbcConfiguration> configurations) {
        this.configurations = configurations == null ? Collections.emptyList() : configurations;
    }

    /**
     * Initialize the schema for the configuration.
     * @param beanLocator The bean locator
     *
     */
    @PostConstruct
    public void createSchema(BeanLocator beanLocator) {
        for (DataJdbcConfiguration configuration : configurations) {
            Dialect dialect = configuration.getDialect();
            SchemaGenerate schemaGenerate = configuration.getSchemaGenerate();
            if (schemaGenerate != null && schemaGenerate != SchemaGenerate.NONE) {
                String name = configuration.getName();
                List<String> packages = configuration.getPackages();

                Collection<BeanIntrospection<Object>> introspections;
                if (CollectionUtils.isNotEmpty(packages)) {
                    introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class, packages.toArray(new String[0]));
                } else {
                    introspections = BeanIntrospector.SHARED.findIntrospections(MappedEntity.class);
                }
                PersistentEntity[] entities = introspections.stream().map(PersistentEntity::of).toArray(PersistentEntity[]::new);
                if (ArrayUtils.isNotEmpty(entities)) {
                    DataSource dataSource = beanLocator.getBean(DataSource.class, Qualifiers.byName(name));
                    try {
                        try (Connection connection = dataSource.getConnection()) {
                            SqlQueryBuilder builder = new SqlQueryBuilder(dialect);
                            if (dialect.allowBatch() && configuration.isBatchGenerate()) {
                                switch (schemaGenerate) {
                                    case CREATE_DROP:
                                        try {
                                            String sql = builder.buildBatchDropTableStatement(entities);
                                            if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                                                DataSettings.QUERY_LOG.debug("Dropping Tables: \n{}", sql);
                                            }
                                            PreparedStatement ps = connection.prepareStatement(sql);
                                            ps.executeUpdate();
                                        } catch (SQLException e) {
                                            if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                                                DataSettings.QUERY_LOG.trace("Drop Failed: " + e.getMessage());
                                            }
                                        }
                                    case CREATE:
                                        String sql = builder.buildBatchCreateTableStatement(entities);
                                        if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                                            DataSettings.QUERY_LOG.debug("Creating Tables: \n{}", sql);
                                        }
                                        PreparedStatement ps = connection.prepareStatement(sql);
                                        ps.executeUpdate();
                                        break;
                                    default:
                                        // do nothing
                                }
                            } else {
                                switch (schemaGenerate) {
                                    case CREATE_DROP:
                                        for (PersistentEntity entity : entities) {
                                            try {
                                                String[] statements = builder.buildDropTableStatements(entity);
                                                for (String sql : statements) {
                                                    if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                                                        DataSettings.QUERY_LOG.debug("Dropping Table: \n{}", sql);
                                                    }
                                                    PreparedStatement ps = connection.prepareStatement(sql);
                                                    ps.executeUpdate();
                                                }
                                            } catch (SQLException e) {
                                                if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                                                    DataSettings.QUERY_LOG.trace("Drop Failed: " + e.getMessage());
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
                                                    PreparedStatement ps = connection.prepareStatement(stmt);
                                                    ps.executeUpdate();
                                                } catch (SQLException e) {
                                                    if (DataSettings.QUERY_LOG.isWarnEnabled()) {
                                                        DataSettings.QUERY_LOG.warn("CREATE Statement Failed: " + e.getMessage());
                                                    }
                                                }
                                            }

                                        }


                                        break;
                                    default:
                                        // do nothing
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
    }

}
