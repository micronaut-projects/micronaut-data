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
package io.micronaut.data.jdbc.operations;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.convert.DatabaseConversionContextFactory;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import io.micronaut.data.runtime.operations.internal.sql.SqlJsonColumnMapperProvider;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.json.JsonMapper;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Named;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * SQL Server-specific JDBC repository operations.
 *
 * <p>This implementation extends {@link DefaultJdbcRepositoryOperations} so SQL Server can reuse
 * the standard JDBC repository behavior and only replace the entity operation objects that need
 * SQL Server-specific upsert generated-id handling. SQL Server {@code MERGE ... OUTPUT inserted.id}
 * returns generated IDs as a result set, which means the upsert path needs to execute the statement
 * as a query and read that result instead of relying on {@link PreparedStatement#getGeneratedKeys()}.</p>
 */
@EachBean(DataSource.class)
@Requires(condition = SqlServerJdbcRepositoryOperationsCondition.class)
final class SqlServerJdbcRepositoryOperations extends DefaultJdbcRepositoryOperations {

    /**
     * Default constructor.
     *
     * @param dataSourceName              The data source name
     * @param jdbcConfiguration           The jdbcConfiguration
     * @param dataSource                  The datasource
     * @param connectionOperations        The connection operations
     * @param transactionOperations       The JDBC operations for the data source
     * @param executorService             The executor service
     * @param beanContext                 The bean context
     * @param dateTimeProvider            The dateTimeProvider
     * @param entityRegistry              The entity registry
     * @param conversionService           The conversion service
     * @param attributeConverterRegistry  The attribute converter registry
     * @param schemaTenantResolver        The schema tenant resolver
     * @param schemaHandler               The schema handler
     * @param jsonMapper                  The JSON mapper
     * @param sqlJsonColumnMapperProvider The SQL JSON column mapper provider
     * @param conversionContextFactory    The conversion context factory
     * @param sqlExceptionMapperList      The SQL exception mapper list
     */
    @SuppressWarnings("ParameterNumber")
    SqlServerJdbcRepositoryOperations(@Parameter String dataSourceName,
                                      @Parameter DataJdbcConfiguration jdbcConfiguration,
                                      DataSource dataSource,
                                      @Parameter ConnectionOperations<Connection> connectionOperations,
                                      @Parameter TransactionOperations<Connection> transactionOperations,
                                      @Named("io") @Nullable ExecutorService executorService,
                                      BeanContext beanContext,
                                      @NonNull DateTimeProvider dateTimeProvider,
                                      RuntimeEntityRegistry entityRegistry,
                                      DataConversionService conversionService,
                                      AttributeConverterRegistry attributeConverterRegistry,
                                      @Nullable SchemaTenantResolver schemaTenantResolver,
                                      JdbcSchemaHandler schemaHandler,
                                      @Nullable JsonMapper jsonMapper,
                                      SqlJsonColumnMapperProvider<ResultSet> sqlJsonColumnMapperProvider,
                                      @Parameter DatabaseConversionContextFactory conversionContextFactory,
                                      List<SqlExceptionMapper> sqlExceptionMapperList) {
        super(
            dataSourceName,
            jdbcConfiguration,
            dataSource,
            connectionOperations,
            transactionOperations,
            executorService,
            beanContext,
            dateTimeProvider,
            entityRegistry,
            conversionService,
            attributeConverterRegistry,
            schemaTenantResolver,
            schemaHandler,
            jsonMapper,
            sqlJsonColumnMapperProvider,
            conversionContextFactory,
            sqlExceptionMapperList
        );
    }

    @Override
    protected <T> JdbcEntityOperations<T> getJdbcEntityOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity, SqlStoredQuery<T, ?> storedQuery) {
        return getJdbcEntityOperations(ctx, persistentEntity, entity, storedQuery, false);
    }

    @Override
    protected <T> JdbcEntityOperations<T> getJdbcEntityOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity, SqlStoredQuery<T, ?> storedQuery, boolean insert) {
        return new SqlServerJdbcEntityOperations<>(ctx, persistentEntity, entity, storedQuery, insert);
    }

    @Override
    protected <T> JdbcEntitiesOperations<T> getJdbcEntitiesOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, SqlStoredQuery<T, ?> storedQuery) {
        return getJdbcEntitiesOperations(ctx, persistentEntity, entities, storedQuery, false);
    }

    @Override
    protected <T> JdbcEntitiesOperations<T> getJdbcEntitiesOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, SqlStoredQuery<T, ?> storedQuery, boolean insert) {
        return new SqlServerJdbcEntitiesOperations<>(ctx, persistentEntity, entities, storedQuery, insert);
    }

    private boolean shouldUseSqlServerUpsertReturning(SqlStoredQuery<?, ?> storedQuery) {
        return storedQuery.getDialect() == Dialect.SQL_SERVER
            && isUpsertOperation(storedQuery)
            && CollectionUtils.isNotEmpty(storedQuery.getOutParameterBindings());
    }

    private <T> void bindParameters(PreparedStatement ps,
                                    JdbcOperationContext ctx,
                                    SqlStoredQuery<T, ?> storedQuery,
                                    T entity,
                                    @Nullable Map<QueryParameterBinding, Object> previousValues) {
        JdbcParameterBinder parameterBinder = new JdbcParameterBinder(ctx.connection, ps, storedQuery);
        storedQuery.bindParameters(parameterBinder, ctx.invocationContext, entity, previousValues);
    }

    private <T> Object readReturnedId(ResultSet resultSet,
                                      RuntimePersistentProperty<T> identity,
                                      SqlStoredQuery<T, ?> storedQuery,
                                      Object entity) throws SQLException {
        List<Object> ids = readReturnedIds(resultSet, identity, storedQuery);
        if (ids.isEmpty()) {
            throw new DataAccessException("SQL Server upsert OUTPUT clause produced no generated ID for entity: " + entity);
        } else if (ids.size() != 1) {
            throw new DataAccessException("SQL Server upsert OUTPUT clause produced " + ids.size() + " generated IDs for a single entity: " + entity);
        }
        return ids.getFirst();
    }

    private <T> List<Object> readReturnedIds(ResultSet resultSet,
                                             RuntimePersistentProperty<T> identity,
                                             SqlStoredQuery<T, ?> storedQuery) throws SQLException {
        List<Object> ids = new ArrayList<>();
        while (resultSet.next()) {
            ids.add(getGeneratedIdentity(resultSet, identity, storedQuery.getDialect()));
        }
        return ids;
    }

    protected class SqlServerJdbcEntityOperations<T> extends JdbcEntityOperations<T> {
        protected SqlServerJdbcEntityOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity, SqlStoredQuery<T, ?> storedQuery, boolean insert) {
            super(ctx, storedQuery, persistentEntity, entity, insert);
        }

        @Override
        protected void execute() throws SQLException {
            if (shouldUseSqlServerUpsertReturning(storedQuery)) {
                upsert();
            } else {
                super.execute();
            }
        }

        private void upsert() throws SQLException {
            QUERY_LOG.debug("Executing SQL query: {}", storedQuery.getQuery());
            try {
                try (PreparedStatement ps = ctx.connection.prepareStatement(storedQuery.getQuery())) {
                    bindParameters(ps, ctx, storedQuery, entity, previousValues);
                    RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                    try (ResultSet resultSet = ps.executeQuery()) {
                        Object id = readReturnedId(resultSet, identity, storedQuery, entity);
                        entity = updateEntityId(identity.getProperty(), entity, id);
                        rowsUpdated = 1;
                    }
                }
            } catch (SQLException e) {
                DataAccessException dataAccessException = mapSqlException(e, ctx.dialect);
                if (dataAccessException != null) {
                    throw dataAccessException;
                }
                throw e;
            }
        }
    }

    protected class SqlServerJdbcEntitiesOperations<T> extends JdbcEntitiesOperations<T> {
        protected SqlServerJdbcEntitiesOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, SqlStoredQuery<T, ?> storedQuery, boolean insert) {
            super(ctx, persistentEntity, entities, storedQuery, insert);
        }

        @Override
        protected void execute() {
            if (shouldUseSqlServerUpsertReturning(storedQuery)) {
                upsert();
            } else {
                super.execute();
            }
        }

        private void upsert() {
            QUERY_LOG.debug("Executing SQL query: {}", storedQuery.getQuery());
            try (PreparedStatement ps = ctx.connection.prepareStatement(storedQuery.getQuery())) {
                RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                for (Data d : entities) {
                    if (d.vetoed) {
                        continue;
                    }
                    bindParameters(ps, ctx, storedQuery, d.entity, d.previousValues);
                    try (ResultSet resultSet = ps.executeQuery()) {
                        Object id = readReturnedId(resultSet, identity, storedQuery, d.entity);
                        d.entity = updateEntityId(identity.getProperty(), d.entity, id);
                        rowsUpdated++;
                    }
                    ps.clearParameters();
                }
            } catch (SQLException e) {
                throw sqlExceptionToDataAccessException(e, ctx.dialect,
                    sqlException -> new DataAccessException(
                        "Error executing upsert statement: " + sqlException.getMessage(),
                        sqlException
                    )
                );
            }
        }
    }
}
