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
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.QueryOutParameterBinding;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.convert.DatabaseConversionContextFactory;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import io.micronaut.data.runtime.operations.internal.sql.SqlJsonColumnMapperProvider;
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
 * SQL Server-specific R2DBC repository operations.
 *
 * <p>This implementation extends {@link DefaultR2dbcRepositoryOperations} so SQL Server can reuse
 * the standard R2DBC repository behavior and only replace the entity operation objects that need
 * SQL Server-specific upsert generated-id handling. SQL Server {@code MERGE ... OUTPUT inserted.id}
 * returns generated IDs as a result row, so the upsert path needs to read that row instead of using
 * generic {@link Statement#returnGeneratedValues(String...)} handling.</p>
 */
@EachBean(ConnectionFactory.class)
@Requires(condition = SqlServerR2dbcRepositoryOperationsCondition.class)
@Internal
public final class SqlServerR2dbcRepositoryOperations extends DefaultR2dbcRepositoryOperations {

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
    SqlServerR2dbcRepositoryOperations(
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
        return new SqlServerR2dbcEntityOperations<>(ctx, persistentEntity, entity, storedQuery, insert);
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
        return new SqlServerR2dbcEntitiesOperations<>(ctx, persistentEntity, entities, storedQuery, insert);
    }

    private boolean shouldUseSqlServerUpsertReturning(SqlStoredQuery<?, ?> storedQuery) {
        return storedQuery.getDialect() == Dialect.SQL_SERVER
            && isUpsertOperation(storedQuery)
            && CollectionUtils.isNotEmpty(storedQuery.getOutParameterBindings());
    }

    private <T> Mono<Object> executeReturningId(R2dbcOperationContext ctx,
                                                SqlStoredQuery<T, ?> storedQuery,
                                                T entity,
                                                @Nullable Map<QueryParameterBinding, Object> previousValues) {
        SqlStoredQuery<T, ?> entityStoredQuery = prepareStoredQuery(storedQuery, entity);
        Statement statement = ctx.getConnection().createStatement(entityStoredQuery.getQuery());
        R2dbcParameterBinder binder = new R2dbcParameterBinder(ctx, statement, entityStoredQuery);
        entityStoredQuery.bindParameters(binder, ctx.getInvocationContext(), entity, previousValues);
        List<QueryOutParameterBinding> outParameterBindings = entityStoredQuery.getOutParameterBindings();
        if (outParameterBindings.size() != 1) {
            return Mono.error(new DataAccessException("SQL Server upsert OUTPUT requires exactly one generated identity OUT parameter, but got: " + outParameterBindings.size()));
        }
        QueryOutParameterBinding out = outParameterBindings.getFirst();
        return executeAndMapEachRow(statement, row -> columnIndexResultSetReader.readDynamic(row, 0, out.dataType()))
            .onErrorResume(errorHandler(entityStoredQuery.getDialect()))
            .collectList()
            .flatMap(ids -> {
                if (ids.isEmpty()) {
                    return Mono.error(new DataAccessException("SQL Server upsert OUTPUT clause produced no generated ID for entity: " + entity));
                }
                if (ids.size() != 1) {
                    return Mono.error(new DataAccessException("SQL Server upsert OUTPUT clause produced " + ids.size() + " generated IDs for a single entity: " + entity));
                }
                return Mono.just(ids.getFirst());
            });
    }

    protected class SqlServerR2dbcEntityOperations<T> extends R2dbcEntityOperations<T> {

        protected SqlServerR2dbcEntityOperations(R2dbcOperationContext ctx,
                                                 RuntimePersistentEntity<T> persistentEntity,
                                                 T entity,
                                                 SqlStoredQuery<T, ?> storedQuery,
                                                 boolean insert) {
            super(ctx, storedQuery, persistentEntity, entity, insert);
        }

        @Override
        protected void execute() throws RuntimeException {
            if (shouldUseSqlServerUpsertReturning(storedQuery)) {
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
                    });
            });
        }
    }

    protected class SqlServerR2dbcEntitiesOperations<T> extends R2dbcEntitiesOperations<T> {

        protected SqlServerR2dbcEntitiesOperations(R2dbcOperationContext ctx,
                                                   RuntimePersistentEntity<T> persistentEntity,
                                                   Iterable<T> entities,
                                                   SqlStoredQuery<T, ?> storedQuery,
                                                   boolean insert) {
            super(ctx, storedQuery, persistentEntity, entities, insert);
        }

        @Override
        protected void execute() throws RuntimeException {
            if (shouldUseSqlServerUpsertReturning(storedQuery)) {
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
                        });
                })
                .collectList());
        }
    }
}
