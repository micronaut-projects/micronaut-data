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
import io.micronaut.data.jdbc.mapper.JdbcQueryStatement;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.QueryOutParameterBinding;
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
import oracle.jdbc.OraclePreparedStatement;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Oracle-specific JDBC repository operations.
 *
 * <p>This implementation extends {@link DefaultJdbcRepositoryOperations} so Oracle can reuse the
 * standard JDBC repository behavior and only replace the entity operation objects that need Oracle
 * driver support. In particular, Oracle {@code MERGE} statements with {@code RETURNING ... INTO}
 * require {@link OraclePreparedStatement#registerReturnParameter(int, int)} and
 * {@link OraclePreparedStatement#getReturnResultSet()}, which are not available through standard
 * JDBC. The default implementation continues to handle all non-Oracle-specific paths, while this
 * class overrides the single-entity and batch entity operations for Oracle upsert generated-id
 * returning.</p>
 */
@EachBean(DataSource.class)
@Requires(classes = OraclePreparedStatement.class)
@Requires(condition = OracleJdbcRepositoryOperationsCondition.class)
final class OracleJdbcRepositoryOperations extends DefaultJdbcRepositoryOperations {

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
    OracleJdbcRepositoryOperations(@Parameter String dataSourceName,
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
        return new OracleJdbcEntityOperations<>(ctx, persistentEntity, entity, storedQuery, insert);
    }

    @Override
    protected <T> JdbcEntitiesOperations<T> getJdbcEntitiesOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, SqlStoredQuery<T, ?> storedQuery) {
        return getJdbcEntitiesOperations(ctx, persistentEntity, entities, storedQuery, false);
    }

    @Override
    protected <T> JdbcEntitiesOperations<T> getJdbcEntitiesOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, SqlStoredQuery<T, ?> storedQuery, boolean insert) {
        return new OracleJdbcEntitiesOperations<>(ctx, persistentEntity, entities, storedQuery, insert);
    }

    private boolean shouldUseOracleUpsertReturning(SqlStoredQuery<?, ?> storedQuery) {
        return storedQuery.getDialect() == Dialect.ORACLE
            && isUpsertOperation(storedQuery)
            && CollectionUtils.isNotEmpty(storedQuery.getOutParameterBindings());
    }

    private void registerReturnParameters(OraclePreparedStatement ps,
                                          SqlStoredQuery<?, ?> query,
                                          int inCount) throws SQLException {
        List<QueryOutParameterBinding> outParams = query.getOutParameterBindings();
        if (CollectionUtils.isEmpty(outParams)) {
            throw new DataAccessException("Missing OUT parameter metadata for Oracle RETURNING. SqlQueryBuilder must attach QueryOutParameterBinding list.");
        }
        int pos = inCount;
        for (QueryOutParameterBinding outParam : outParams) {
            DataType dataType = query.getDialect().getDataType(outParam.dataType());
            int sqlType = JdbcQueryStatement.findSqlType(dataType, query.getDialect());
            if (sqlType == -1) {
                sqlType = Types.VARCHAR;
            }
            ps.registerReturnParameter(++pos, sqlType);
        }
    }

    private OraclePreparedStatement unwrapOraclePreparedStatement(PreparedStatement ps) throws SQLException {
        return ps.unwrap(OraclePreparedStatement.class);
    }

    private <T> int bindParameters(PreparedStatement ps,
                                   JdbcOperationContext ctx,
                                   SqlStoredQuery<T, ?> storedQuery,
                                   T entity,
                                   @Nullable Map<QueryParameterBinding, Object> previousValues) {
        JdbcParameterBinder parameterBinder = new JdbcParameterBinder(ctx.connection, ps, storedQuery);
        storedQuery.bindParameters(parameterBinder, ctx.invocationContext, entity, previousValues);
        return parameterBinder.currentIndex() - 1;
    }

    private <T> Object readReturnedId(OraclePreparedStatement oraclePreparedStatement,
                                      RuntimePersistentProperty<T> identity,
                                      SqlStoredQuery<T, ?> storedQuery,
                                      Object entity) throws SQLException {
        List<Object> ids = readReturnedIds(oraclePreparedStatement, identity, storedQuery);
        if (ids.isEmpty()) {
            throw new DataAccessException("Oracle upsert RETURNING clause produced no generated ID for entity: " + entity);
        } else if (ids.size() != 1) {
            throw new DataAccessException("Oracle upsert RETURNING clause produced " + ids.size() + " generated IDs for a single entity: " + entity);
        }
        return ids.getFirst();
    }

    private <T> List<Object> readReturnedIds(OraclePreparedStatement oraclePreparedStatement,
                                             RuntimePersistentProperty<T> identity,
                                             SqlStoredQuery<T, ?> storedQuery) throws SQLException {
        List<Object> ids = new ArrayList<>();
        try (ResultSet resultSet = oraclePreparedStatement.getReturnResultSet()) {
            while (resultSet.next()) {
                ids.add(getGeneratedIdentity(resultSet, identity, storedQuery.getDialect()));
            }
        }
        return ids;
    }

    protected class OracleJdbcEntityOperations<T> extends JdbcEntityOperations<T> {
        protected OracleJdbcEntityOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity, SqlStoredQuery<T, ?> storedQuery, boolean insert) {
            super(ctx, storedQuery, persistentEntity, entity, insert);
        }

        @Override
        protected void execute() throws SQLException {
            if (shouldUseOracleUpsertReturning(storedQuery)) {
                upsert();
            } else {
                super.execute();
            }
        }

        private void upsert() throws SQLException {
            QUERY_LOG.debug("Executing SQL query: {}", storedQuery.getQuery());
            try (PreparedStatement ps = ctx.connection.prepareStatement(storedQuery.getQuery())) {
                OraclePreparedStatement oraclePreparedStatement = unwrapOraclePreparedStatement(ps);
                int inCount = bindParameters(ps, ctx, storedQuery, entity, previousValues);
                registerReturnParameters(oraclePreparedStatement, storedQuery, inCount);
                rowsUpdated = oraclePreparedStatement.executeUpdate();
                RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                Object id = readReturnedId(oraclePreparedStatement, identity, storedQuery, entity);
                entity = updateEntityId(identity.getProperty(), entity, id);
            } catch (SQLException e) {
                DataAccessException dataAccessException = mapSqlException(e, ctx.dialect);
                if (dataAccessException != null) {
                    throw dataAccessException;
                }
                throw e;
            }
        }
    }

    protected class OracleJdbcEntitiesOperations<T> extends JdbcEntitiesOperations<T> {
        protected OracleJdbcEntitiesOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, SqlStoredQuery<T, ?> storedQuery, boolean insert) {
            super(ctx, persistentEntity, entities, storedQuery, insert);
        }

        @Override
        protected void execute() {
            if (shouldUseOracleUpsertReturning(storedQuery)) {
                upsert();
            } else {
                super.execute();
            }
        }

        private void upsert() {
            QUERY_LOG.debug("Executing SQL query: {}", storedQuery.getQuery());
            List<Data> notVetoedEntities = notVetoedEntities();
            if (notVetoedEntities.isEmpty()) {
                rowsUpdated = 0;
                return;
            }
            try (PreparedStatement ps = ctx.connection.prepareStatement(storedQuery.getQuery())) {
                OraclePreparedStatement oraclePreparedStatement = unwrapOraclePreparedStatement(ps);
                boolean returnParametersRegistered = false;
                for (Data d : notVetoedEntities) {
                    int inCount = bindParameters(ps, ctx, storedQuery, d.entity, d.previousValues);
                    if (!returnParametersRegistered) {
                        registerReturnParameters(oraclePreparedStatement, storedQuery, inCount);
                        returnParametersRegistered = true;
                    }
                    ps.addBatch();
                }
                rowsUpdated = Arrays.stream(ps.executeBatch()).sum();
                updateEntityIdsFromReturnedIds(oraclePreparedStatement, notVetoedEntities);
            } catch (SQLException e) {
                throw sqlExceptionToDataAccessException(e, ctx.dialect,
                    sqlException -> new DataAccessException(
                        "Error executing upsert statement: " + sqlException.getMessage(),
                        sqlException
                    )
                );
            }
        }

        private void updateEntityIdsFromReturnedIds(OraclePreparedStatement oraclePreparedStatement,
                                                    List<Data> notVetoedEntities) throws SQLException {
            RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
            List<Object> ids = readReturnedIds(oraclePreparedStatement, identity, storedQuery);
            Iterator<Object> iterator = ids.iterator();
            int updated = 0;
            for (Data d : notVetoedEntities) {
                if (!iterator.hasNext()) {
                    throw new DataAccessException("Oracle upsert RETURNING clause produced " + updated + " generated IDs for " + notVetoedEntities.size() + " entities");
                }
                Object id = iterator.next();
                d.entity = updateEntityId(identity.getProperty(), d.entity, id);
                updated++;
            }
            if (iterator.hasNext()) {
                throw new DataAccessException("Oracle upsert RETURNING clause produced more generated IDs than entities");
            }
        }

        private List<Data> notVetoedEntities() {
            return entities.stream().filter(d -> !d.vetoed).toList();
        }
    }
}
