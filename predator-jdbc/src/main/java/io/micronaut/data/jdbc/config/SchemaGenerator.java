package io.micronaut.data.jdbc.config;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
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
import io.micronaut.data.runtime.config.PredatorSettings;
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
@Requires(env = Environment.TEST)
public class SchemaGenerator {

    private List<PredatorJdbcConfiguration> configurations;

    /**
     * Constructors a schema generator for the given configurations.
     *
     * @param configurations The configurations
     */
    public SchemaGenerator(List<PredatorJdbcConfiguration> configurations) {
        this.configurations = configurations == null ? Collections.emptyList() : configurations;
    }

    /**
     * Initialize the schema for the configuration.
     *
     * @param beanContext the bean context
     */
    @PostConstruct
    void init(BeanContext beanContext) {
        for (PredatorJdbcConfiguration configuration : configurations) {
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
                    DataSource dataSource = beanContext.getBean(DataSource.class, Qualifiers.byName(name));
                    try {
                        try (Connection connection = dataSource.getConnection()) {
                            SqlQueryBuilder builder = new SqlQueryBuilder(dialect);
                            switch (schemaGenerate) {
                                case CREATE_DROP:
                                    for (PersistentEntity entity : entities) {
                                        try {
                                            String tableName = builder.getTableName(entity);
                                            String sql = "DROP TABLE " + tableName;
                                            if (PredatorSettings.QUERY_LOG.isDebugEnabled()) {
                                                PredatorSettings.QUERY_LOG.debug("Dropping Table: \n{}", sql);
                                            }
                                            PreparedStatement ps = connection.prepareStatement(sql);
                                            ps.executeUpdate();
                                        } catch (SQLException e) {
                                            if (PredatorSettings.QUERY_LOG.isDebugEnabled()) {
                                                PredatorSettings.QUERY_LOG.debug("Drop Failed: " + e.getMessage());
                                            }
                                        }
                                    }
                                case CREATE:
                                    for (PersistentEntity entity : entities) {

                                        String sql = builder.buildCreateTable(entity);
                                        if (PredatorSettings.QUERY_LOG.isDebugEnabled()) {
                                            PredatorSettings.QUERY_LOG.debug("Creating Table: \n{}", sql);
                                        }
                                        PreparedStatement ps = connection.prepareStatement(sql);
                                        ps.executeUpdate();
                                    }


                                    break;
                                default:
                                    // do nothing
                            }
                        } catch (SQLException e) {
                            throw new DataAccessException("Unable to create database schema: " + e.getMessage(), e);
                        }
                    } catch (NoSuchBeanException e) {
                        throw new ConfigurationException("No DataSource configured for setting [" + PredatorJdbcConfiguration.PREFIX + name + "]. Ensure the DataSource is configured correctly and try again.", e);
                    }
                }
            }
        }
    }
}
