/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.jdbc.search;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.annotation.search.Searchable;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.query.builder.sql.TextSearchDialect;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.search.SearchCapability;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.search.SearchCapabilities;
import io.micronaut.data.runtime.search.SearchIndexOperations;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;

/**
 * {@link SearchIndexOperations} of a JDBC datasource: runs the full-text search index DDL the
 * {@link SqlQueryBuilder} renders for the datasource's dialect.
 *
 * @since 5.4.0
 */
@Experimental
@EachBean(DataJdbcConfiguration.class)
public class JdbcSearchIndexOperations implements SearchIndexOperations, SearchCapabilities {

    private final DataJdbcConfiguration configuration;
    private final BeanLocator beanLocator;
    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final SqlQueryBuilder queryBuilder;
    @Nullable
    private final TextSearchDialect textSearchDialect;

    /**
     * @param configuration The datasource configuration
     * @param beanLocator The bean locator, to find the datasource
     * @param runtimeEntityRegistry The entity registry
     */
    public JdbcSearchIndexOperations(@Parameter DataJdbcConfiguration configuration,
                                     BeanLocator beanLocator,
                                     RuntimeEntityRegistry runtimeEntityRegistry) {
        this.configuration = configuration;
        this.beanLocator = beanLocator;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.queryBuilder = new SqlQueryBuilder(configuration.getDialect());
        this.textSearchDialect = TextSearchDialect.forDialect(configuration.getDialect());
    }

    @Override
    public Set<SearchCapability> getSearchCapabilities(Class<?> entityType) {
        if (textSearchDialect == null || !runtimeEntityRegistry.getEntity(entityType).getAnnotationMetadata().hasAnnotation(Searchable.class)) {
            return Set.of();
        }
        return textSearchDialect.capabilities();
    }

    @Override
    public boolean exists(Class<?> entityType) {
        List<String> names = searchIndexNames(entityType);
        TextSearchDialect dialect = textSearchDialect;
        if (dialect == null || names.isEmpty()) {
            return false;
        }
        String query = dialect.searchIndexExistsQuery();
        if (query == null) {
            return false;
        }
        try (Connection connection = dataSource().getConnection()) {
            for (String name : names) {
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    ps.setString(1, dialect.catalogIndexName(name));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || rs.getLong(1) == 0) {
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read the search indexes of " + entityType.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void create(Class<?> entityType) {
        if (exists(entityType)) {
            return;
        }
        execute(entityType, queryBuilder.buildCreateSearchIndexStatements(entity(entityType)));
    }

    @Override
    public void drop(Class<?> entityType) {
        if (textSearchDialect == null) {
            return;
        }
        List<String> statements = new java.util.ArrayList<>();
        for (String name : searchIndexNames(entityType)) {
            statements.add(textSearchDialect.buildDropSearchIndex(name));
        }
        execute(entityType, statements);
    }

    @Override
    public void sync(Class<?> entityType) {
        if (textSearchDialect == null) {
            return;
        }
        execute(entityType, textSearchDialect.buildSyncSearchIndex(searchIndexNames(entityType)));
    }

    private List<String> searchIndexNames(Class<?> entityType) {
        return queryBuilder.searchIndexNames(entity(entityType));
    }

    private PersistentEntity entity(Class<?> entityType) {
        return runtimeEntityRegistry.getEntity(entityType);
    }

    private void execute(Class<?> entityType, List<String> statements) {
        if (statements.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource().getConnection()) {
            for (String statement : statements) {
                if (DataSettings.QUERY_LOG.isDebugEnabled()) {
                    DataSettings.QUERY_LOG.debug("Executing search index statement: {}", statement);
                }
                try (PreparedStatement ps = connection.prepareStatement(statement)) {
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Search index statement failed for " + entityType.getName() + ": " + e.getMessage(), e);
        }
    }

    private DataSource dataSource() {
        return DelegatingDataSource.unwrapDataSource(beanLocator.getBean(DataSource.class, Qualifiers.byName(configuration.getName())));
    }
}
