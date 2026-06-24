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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.QueryOutParameterBinding;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;
import io.micronaut.data.r2dbc.mapper.ColumnNameByIndexR2dbcResultReader;
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.convert.DatabaseConversionContextFactory;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.OracleReturningMetadata;
import io.micronaut.data.runtime.operations.internal.sql.SqlJsonColumnMapperProvider;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.json.JsonMapper;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;
import jakarta.inject.Named;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Oracle-specific R2DBC repository operations.
 *
 * <p>This implementation extends {@link DefaultR2dbcRepositoryOperations} so Oracle can reuse the
 * standard R2DBC repository behavior and only replace the entity operation objects that need Oracle
 * upsert generated-id returning. Oracle R2DBC exposes DML {@code RETURNING ... INTO} values as OUT
 * parameters, so Oracle upsert with generated IDs must bind the OUT parameters from the stored query
 * metadata and map the returned readable result instead of relying on generic
 * {@link Statement#returnGeneratedValues(String...)} handling.</p>
 */
@EachBean(ConnectionFactory.class)
@Requires(condition = OracleR2dbcRepositoryOperationsCondition.class)
@Internal
public final class OracleR2dbcRepositoryOperations extends DefaultR2dbcRepositoryOperations {

    /**
     * Default constructor.
     *
     * @param dataSourceName              The data source name
     * @param connectionFactory           The associated connection factory
     * @param dateTimeProvider            The date time provider
     * @param runtimeEntityRegistry       The runtime entity registry
     * @param applicationContext          The bean context
     * @param executorService             The executor
     * @param conversionService           The conversion service
     * @param attributeConverterRegistry  The attribute converter registry
     * @param schemaTenantResolver        The schema tenant resolver
     * @param schemaHandler               The schema handler
     * @param configuration               The configuration
     * @param jsonMapper                  The JSON mapper
     * @param sqlJsonColumnMapperProvider The SQL JSON column mapper provider
     * @param r2dbcExceptionMapperList    The R2DBC exception mapper list
     * @param vectorBindSupports          The vector bind supports
     * @param transactionOperations       The transaction operations
     * @param connectionOperations        The connection operations
     * @param conversionContextFactory    The conversion context factory
     */
    @Internal
    @SuppressWarnings("ParameterNumber")
    OracleR2dbcRepositoryOperations(
        @Parameter String dataSourceName,
        ConnectionFactory connectionFactory,
        @NonNull DateTimeProvider<Object> dateTimeProvider,
        RuntimeEntityRegistry runtimeEntityRegistry,
        ApplicationContext applicationContext,
        @Nullable @Named("io") ExecutorService executorService,
        DataConversionService conversionService,
        AttributeConverterRegistry attributeConverterRegistry,
        @Nullable SchemaTenantResolver schemaTenantResolver,
        R2dbcSchemaHandler schemaHandler,
        @Parameter DataR2dbcConfiguration configuration,
        @Nullable JsonMapper jsonMapper,
        SqlJsonColumnMapperProvider<Row> sqlJsonColumnMapperProvider,
        List<R2dbcExceptionMapper> r2dbcExceptionMapperList,
        List<VectorBindSupport> vectorBindSupports,
        @Parameter R2dbcReactorTransactionOperations transactionOperations,
        @Parameter ReactorConnectionOperations<Connection> connectionOperations,
        @Parameter DatabaseConversionContextFactory conversionContextFactory) {
        super(
            dataSourceName,
            connectionFactory,
            dateTimeProvider,
            runtimeEntityRegistry,
            applicationContext,
            executorService,
            conversionService,
            attributeConverterRegistry,
            schemaTenantResolver,
            schemaHandler,
            configuration,
            jsonMapper,
            sqlJsonColumnMapperProvider,
            r2dbcExceptionMapperList,
            vectorBindSupports,
            transactionOperations,
            connectionOperations,
            conversionContextFactory
        );
    }

    @Override
    protected <T> R2dbcEntityOperations<T> getR2dbcEntityOperations(R2dbcOperationContext ctx,
                                                                    RuntimePersistentEntity<T> persistentEntity,
                                                                    T entity,
                                                                    SqlStoredQuery<T, ?> storedQuery) {
        return getR2dbcEntityOperations(ctx, persistentEntity, entity, storedQuery, false);
    }

    @Override
    protected <T> R2dbcEntityOperations<T> getR2dbcEntityOperations(R2dbcOperationContext ctx,
                                                                    RuntimePersistentEntity<T> persistentEntity,
                                                                    T entity,
                                                                    SqlStoredQuery<T, ?> storedQuery,
                                                                    boolean insert) {
        return new OracleR2dbcEntityOperations<>(ctx, persistentEntity, entity, storedQuery, insert);
    }

    @Override
    protected <T> R2dbcEntitiesOperations<T> getR2dbcEntitiesOperations(R2dbcOperationContext ctx,
                                                                        RuntimePersistentEntity<T> persistentEntity,
                                                                        Iterable<T> entities,
                                                                        SqlStoredQuery<T, ?> storedQuery) {
        return getR2dbcEntitiesOperations(ctx, persistentEntity, entities, storedQuery, false);
    }

    @Override
    protected <T> R2dbcEntitiesOperations<T> getR2dbcEntitiesOperations(R2dbcOperationContext ctx,
                                                                        RuntimePersistentEntity<T> persistentEntity,
                                                                        Iterable<T> entities,
                                                                        SqlStoredQuery<T, ?> storedQuery,
                                                                        boolean insert) {
        return new OracleR2dbcEntitiesOperations<>(ctx, persistentEntity, entities, storedQuery, insert);
    }

    private boolean shouldUseOracleUpsertReturning(SqlStoredQuery<?, ?> storedQuery) {
        return isUpsertOperation(storedQuery) && CollectionUtils.isNotEmpty(storedQuery.getOutParameterBindings());
    }

    private <T> Mono<Object> executeReturningId(R2dbcOperationContext ctx,
                                                SqlStoredQuery<T, ?> storedQuery,
                                                T entity,
                                                @Nullable Map<QueryParameterBinding, Object> previousValues) {
        SqlStoredQuery<T, ?> entityStoredQuery = prepareStoredQuery(storedQuery, entity);
        Statement statement = ctx.getConnection().createStatement(entityStoredQuery.getQuery());
        R2dbcParameterBinder binder = new R2dbcParameterBinder(ctx, statement, entityStoredQuery);
        entityStoredQuery.bindParameters(binder, ctx.getInvocationContext(), entity, previousValues);
        statement = bindOracleReturningOutParameters(statement, entityStoredQuery, binder.currentIndex());
        List<QueryOutParameterBinding> outParameterBindings = entityStoredQuery.getOutParameterBindings();
        if (outParameterBindings.size() != 1) {
            return Mono.error(new DataAccessException("Oracle upsert RETURNING requires exactly one generated identity OUT parameter, but got: " + outParameterBindings.size()));
        }
        QueryOutParameterBinding out = outParameterBindings.getFirst();
        OracleReturningMetadata metadata = getOracleReturningMetadata(entityStoredQuery);
        ColumnNameByIndexR2dbcResultReader resultReader = new ColumnNameByIndexR2dbcResultReader(conversionService, metadata.columnIndexesByName());
        return executeAndMapOracleReturningSingleNullable(statement, entityStoredQuery.getDialect(), readable -> resultReader.readDynamic(readable, out.name(), out.dataType()));
    }

    @SuppressWarnings("unchecked")
    private <T> SqlStoredQuery<T, ?> prepareStoredQuery(SqlStoredQuery<T, ?> storedQuery, T entity) {
        if (storedQuery instanceof SqlPreparedQuery<T, ?> sqlPreparedQuery) {
            SqlStoredQuery<T, Object> typedStoredQuery = (SqlStoredQuery<T, Object>) storedQuery;
            SqlPreparedQuery<T, Object> typedPreparedQuery = (SqlPreparedQuery<T, Object>) sqlPreparedQuery;
            DefaultSqlPreparedQuery<T, Object> entityPreparedQuery = new DefaultSqlPreparedQuery<>(typedPreparedQuery, typedStoredQuery);
            entityPreparedQuery.prepare(entity);
            return entityPreparedQuery;
        }
        return storedQuery;
    }

    protected class OracleR2dbcEntityOperations<T> extends R2dbcEntityOperations<T> {

        protected OracleR2dbcEntityOperations(R2dbcOperationContext ctx,
                                              RuntimePersistentEntity<T> persistentEntity,
                                              T entity,
                                              SqlStoredQuery<T, ?> storedQuery,
                                              boolean insert) {
            super(ctx, storedQuery, persistentEntity, entity, insert);
        }

        @Override
        protected void execute() throws RuntimeException {
            if (shouldUseOracleUpsertReturning(storedQuery)) {
                upsert();
            } else {
                super.execute();
            }
        }

        private void upsert() {
            QUERY_LOG.debug("Executing SQL query: {}", storedQuery.getQuery());
            BeanProperty<T, Object> identityProperty = persistentEntity.getIdentity().getProperty();
            data = data.flatMap(d -> {
                if (d.vetoed) {
                    return Mono.just(d);
                }
                return executeReturningId(ctx, storedQuery, d.entity, d.previousValues)
                    .map(id -> {
                        d.entity = updateEntityId(identityProperty, d.entity, id);
                        return d;
                    })
                    .switchIfEmpty(Mono.just(d));
            });
        }
    }

    protected class OracleR2dbcEntitiesOperations<T> extends R2dbcEntitiesOperations<T> {

        protected OracleR2dbcEntitiesOperations(R2dbcOperationContext ctx,
                                                RuntimePersistentEntity<T> persistentEntity,
                                                Iterable<T> entities,
                                                SqlStoredQuery<T, ?> storedQuery,
                                                boolean insert) {
            super(ctx, storedQuery, persistentEntity, entities, insert);
        }

        @Override
        protected void execute() throws RuntimeException {
            if (shouldUseOracleUpsertReturning(storedQuery)) {
                upsert();
            } else {
                super.execute();
            }
        }

        private void upsert() {
            QUERY_LOG.debug("Executing SQL query: {}", storedQuery.getQuery());
            BeanProperty<T, Object> identityProperty = persistentEntity.getIdentity().getProperty();
            entities = entities.flatMap(list -> Flux.fromIterable(list)
                .concatMap(d -> {
                    if (d.vetoed) {
                        return Mono.just(d);
                    }
                    return executeReturningId(ctx, storedQuery, d.entity, d.previousValues)
                        .map(id -> {
                            d.entity = updateEntityId(identityProperty, d.entity, id);
                            return d;
                        })
                        .switchIfEmpty(Mono.just(d));
                })
                .collectList());
        }
    }
}
