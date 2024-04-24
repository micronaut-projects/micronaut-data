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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.aop.InvocationContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.data.connection.reactive.ReactorConnectionOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.EntityOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedDataOperation;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.reactive.BlockingExecutorReactorRepositoryOperations;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;
import io.micronaut.data.r2dbc.convert.R2dbcConversionContext;
import io.micronaut.data.r2dbc.mapper.ColumnIndexR2dbcResultReader;
import io.micronaut.data.r2dbc.mapper.ColumnNameR2dbcResultReader;
import io.micronaut.data.r2dbc.mapper.R2dbcQueryStatement;
import io.micronaut.data.r2dbc.transaction.R2dbcReactorTransactionOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.convert.RuntimePersistentPropertyConversionContext;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import io.micronaut.data.runtime.operations.ReactorToAsyncOperationsAdaptor;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntitiesOperations;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.data.runtime.operations.internal.ReactiveCascadeOperations;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.AbstractSqlRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.sql.SqlJsonColumnMapperProvider;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.data.runtime.support.AbstractConversionContext;
import io.micronaut.json.JsonMapper;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations.TransactionalCallback;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcType;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.Statement;
import jakarta.inject.Named;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Defines an implementation of Micronaut Data's core interfaces for R2DBC.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@EachBean(ConnectionFactory.class)
@Internal
final class DefaultR2dbcRepositoryOperations extends AbstractSqlRepositoryOperations<Row, Statement, RuntimeException>
    implements BlockingExecutorReactorRepositoryOperations, R2dbcRepositoryOperations, R2dbcOperations,
    ReactiveCascadeOperations.ReactiveCascadeOperationsHelper<DefaultR2dbcRepositoryOperations.R2dbcOperationContext> {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultR2dbcRepositoryOperations.class);
    private final ConnectionFactory connectionFactory;
    private final ReactorReactiveRepositoryOperations reactiveOperations;
    private final String dataSourceName;
    private ExecutorService ioExecutorService;
    private AsyncRepositoryOperations asyncRepositoryOperations;
    private final ReactiveCascadeOperations<R2dbcOperationContext> cascadeOperations;
    private final R2dbcReactorTransactionOperations transactionOperations;
    private final ReactorConnectionOperations<Connection> connectionOperations;
    @Nullable
    private final SchemaTenantResolver schemaTenantResolver;
    private final R2dbcSchemaHandler schemaHandler;
    private final DataR2dbcConfiguration configuration;

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
     * @param transactionOperations       The transaction operations
     * @param connectionOperations        The connection operations
     */
    @Internal
    @SuppressWarnings("ParameterNumber")
    DefaultR2dbcRepositoryOperations(
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
        @Parameter R2dbcReactorTransactionOperations transactionOperations,
        @Parameter ReactorConnectionOperations<Connection> connectionOperations) {
        super(
            dataSourceName,
            new ColumnNameR2dbcResultReader(conversionService),
            new ColumnIndexR2dbcResultReader(conversionService),
            new R2dbcQueryStatement(conversionService),
            dateTimeProvider,
            runtimeEntityRegistry,
            applicationContext,
            conversionService,
            attributeConverterRegistry,
            jsonMapper,
            sqlJsonColumnMapperProvider);
        this.connectionFactory = connectionFactory;
        this.ioExecutorService = executorService;
        this.schemaTenantResolver = schemaTenantResolver;
        this.schemaHandler = schemaHandler;
        this.configuration = configuration;
        this.transactionOperations = transactionOperations;
        this.connectionOperations = connectionOperations;
        this.reactiveOperations = new DefaultR2dbcReactiveRepositoryOperations();
        this.dataSourceName = dataSourceName;
        this.cascadeOperations = new ReactiveCascadeOperations<>(conversionService, this);
        String name = dataSourceName;
        if (name == null) {
            name = "default";
        }
    }

    @Override
    protected Integer getFirstResultSetIndex() {
        return 0;
    }

    @Override
    public <T> T block(Function<io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations, Mono<T>> supplier) {
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Mono.defer(() -> supplier.apply(reactive())
                .contextWrite(ReactorPropagation.addPropagatedContext(Context.empty(), propagatedContext)))
            .block();
    }

    @Override
    public <T> Optional<T> blockOptional(Function<io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations, Mono<T>> supplier) {
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return Mono.defer(() -> supplier.apply(reactive())
                .contextWrite(ReactorPropagation.addPropagatedContext(Context.empty(), propagatedContext)))
            .blockOptional();
    }

    @Override
    public <T> Mono<T> persistOne(R2dbcOperationContext ctx, T value, RuntimePersistentEntity<T> persistentEntity) {
        SqlStoredQuery<T, ?> storedQuery = resolveEntityInsert(ctx.annotationMetadata, ctx.repositoryType, (Class<T>) value.getClass(), persistentEntity);
        R2dbcEntityOperations<T> op = new R2dbcEntityOperations<>(ctx, storedQuery, persistentEntity, value, true);
        op.persist();
        return op.getEntity();
    }

    @Override
    public <T> Flux<T> persistBatch(R2dbcOperationContext ctx, Iterable<T> values, RuntimePersistentEntity<T> persistentEntity, Predicate<T> predicate) {
        SqlStoredQuery<T, ?> storedQuery = resolveEntityInsert(
            ctx.annotationMetadata,
            ctx.repositoryType,
            persistentEntity.getIntrospection().getBeanType(),
            persistentEntity
        );
        R2dbcEntitiesOperations<T> op = new R2dbcEntitiesOperations<>(ctx, storedQuery, persistentEntity, values, true);
        if (predicate != null) {
            op.veto(predicate);
        }
        op.persist();
        return op.getEntities();
    }

    @Override
    public <T> Mono<T> updateOne(R2dbcOperationContext ctx, T value, RuntimePersistentEntity<T> persistentEntity) {
        SqlStoredQuery<T, ?> storedQuery = resolveEntityUpdate(ctx.annotationMetadata, ctx.repositoryType, (Class<T>) value.getClass(), persistentEntity);
        R2dbcEntityOperations<T> op = new R2dbcEntityOperations<>(ctx, persistentEntity, value, storedQuery);
        op.update();
        return op.getEntity();
    }

    @Override
    public Mono<Void> persistManyAssociation(R2dbcOperationContext ctx,
                                             RuntimeAssociation runtimeAssociation,
                                             Object value, RuntimePersistentEntity<Object> persistentEntity,
                                             Object child, RuntimePersistentEntity<Object> childPersistentEntity) {
        SqlStoredQuery<Object, ?> storedQuery = resolveSqlInsertAssociation(ctx.repositoryType, runtimeAssociation, persistentEntity, value);
        R2dbcEntityOperations<Object> assocEntityOp = new R2dbcEntityOperations<>(ctx, childPersistentEntity, child, storedQuery);
        try {
            assocEntityOp.execute();
        } catch (Exception e1) {
            throw new DataAccessException("SQL error executing INSERT: " + e1.getMessage(), e1);
        }
        return assocEntityOp.getEntity().then();
    }

    @Override
    public Mono<Void> persistManyAssociationBatch(R2dbcOperationContext ctx,
                                                  RuntimeAssociation runtimeAssociation,
                                                  Object value, RuntimePersistentEntity<Object> persistentEntity,
                                                  Iterable<Object> child, RuntimePersistentEntity<Object> childPersistentEntity,
                                                  Predicate<Object> veto) {
        SqlStoredQuery<Object, ?> storedQuery = resolveSqlInsertAssociation(ctx.repositoryType, runtimeAssociation, persistentEntity, value);
        R2dbcEntitiesOperations<Object> assocEntitiesOp = new R2dbcEntitiesOperations<>(ctx, childPersistentEntity, child, storedQuery);
        assocEntitiesOp.veto(veto);
        try {
            assocEntitiesOp.execute();
        } catch (Exception e1) {
            throw new DataAccessException("SQL error executing INSERT: " + e1.getMessage(), e1);
        }
        return assocEntitiesOp.getEntities().then();
    }

    private Mono<Number> sum(Stream<Mono<Number>> stream) {
        return stream.reduce((m1, m2) -> m1.zipWith(m2).map(t -> t.getT1().longValue() + t.getT2().longValue())).orElse(Mono.empty());
    }

    private <T> Flux<T> concatMono(Stream<Mono<T>> stream) {
        return Flux.concat(stream.toList());
    }

    @NonNull
    @Override
    public ReactorReactiveRepositoryOperations reactive() {
        return reactiveOperations;
    }

    @NonNull
    @Override
    public AsyncRepositoryOperations async() {
        if (asyncRepositoryOperations == null) {
            if (ioExecutorService == null) {
                ioExecutorService = Executors.newCachedThreadPool();
            }
            asyncRepositoryOperations = new ReactorToAsyncOperationsAdaptor(reactiveOperations, ioExecutorService);
        }
        return asyncRepositoryOperations;
    }

    @NonNull
    @Override
    public ConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    @NonNull
    @Override
    public <T> Flux<T> withConnection(@NonNull Function<Connection, Publisher<? extends T>> handler) {
        Objects.requireNonNull(handler, "Handler cannot be null");
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating a new Connection for DataSource: " + dataSourceName);
        }
        return Flux.usingWhen(connectionFactory.create(), tenantAwareHandler(handler), (connection -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Closing Connection for DataSource: " + dataSourceName);
            }
            return connection.close();
        }));
    }

    private <K> Function<Connection, Publisher<? extends K>> tenantAwareHandler(Function<Connection, Publisher<? extends K>> handler) {
        Function<Connection, Publisher<? extends K>> theHandler;
        if (schemaTenantResolver == null) {
            theHandler = handler;
        } else {
            theHandler = connection -> {
                String schemaName = schemaTenantResolver.resolveTenantSchemaName();
                if (schemaName != null) {
                    return Mono.fromDirect(schemaHandler.useSchema(connection, configuration.getDialect(), schemaName))
                        .thenReturn(connection)
                        .flatMapMany(handler::apply);
                }
                return handler.apply(connection);
            };
        }
        return theHandler;
    }

    @NonNull
    @Override
    public <T> Publisher<T> withTransaction(@NonNull ReactiveTransactionStatus<Connection> status,
                                            @NonNull TransactionalCallback<Connection, T> handler) {
        return transactionOperations.withTransaction(status, handler);
    }

    @Override
    public <T> Publisher<T> withTransaction(TransactionalCallback<Connection, T> handler) {
        return transactionOperations.withTransaction(handler);
    }

    private static <R> Mono<R> toSingleResult(Flux<R> flux) {
        // Prevent canceling an active stream when converting to Mono by reading full flux and returning the first result
        return Mono.fromDirect(flux.collectList().flatMap(result -> {
            if (result.isEmpty()) {
                return Mono.empty();
            }
            if (result.size() > 1) {
                return Mono.error(new NonUniqueResultException());
            }
            return Mono.just(result.get(0));
        }));
    }

    @Override
    public boolean isSupportsBatchInsert(R2dbcOperationContext context, RuntimePersistentEntity<?> persistentEntity) {
        return isSupportsBatchInsert(persistentEntity, context.dialect);
    }

    private static <T> Flux<T> executeAndMapEachRow(Statement statement, Function<Row, T> mapper) {
        return Flux.from(statement.execute())
            .flatMap(result -> Flux.from(result.map((row, rowMetadata) -> mapper.apply(row))));
    }

    private static <T> Flux<T> executeAndMapEachReadable(Statement statement, Function<Readable, T> mapper) {
        return Flux.from(statement.execute())
            .flatMap(result -> Flux.from(result.map(mapper)));
    }

    private static <T> Flux<T> executeAndMapEachRowNullable(Statement statement, Function<Row, T> mapper) {
        return Flux.from(statement.execute())
            .flatMap(result -> Flux.from(result.map((row, metadata) -> Mono.justOrEmpty(mapper.apply(row)))).flatMap(t -> t));
    }

    private static <T> Mono<T> executeAndMapEachRowSingle(Statement statement, Dialect dialect, Function<Row, T> mapper) {
        return executeAndMapEachRow(statement, mapper).onErrorResume(errorHandler(dialect)).as(DefaultR2dbcRepositoryOperations::toSingleResult);
    }

    private static <T> Flux<T> executeAndMapEachReadable(Statement statement, Dialect dialect, Function<Readable, T> mapper) {
        return executeAndMapEachReadable(statement, mapper).onErrorResume(errorHandler(dialect));
    }

    private static Mono<Number> executeAndGetRowsUpdatedSingle(Statement statement, Dialect dialect) {
        return executeAndGetRowsUpdated(statement)
            .onErrorResume(errorHandler(dialect))
            .as(DefaultR2dbcRepositoryOperations::toSingleResult);
    }

    private static Flux<Number> executeAndGetRowsUpdated(Statement statement) {
        return Flux.from(statement.execute())
            .flatMap(Result::getRowsUpdated)
            .map((Number n) -> n.longValue());
    }

    private static <T> Function<? super Throwable, ? extends Publisher<? extends T>> errorHandler(Dialect dialect) {
        return throwable -> {
            if (throwable.getCause() instanceof SQLException sqlException) {
                Throwable newThrowable = handleSqlException(sqlException, dialect);
                return Mono.error(newThrowable);
            }
            return Mono.error(throwable);
        };
    }

    /**
     * Reactive operations implementation.
     */
    private final class DefaultR2dbcReactiveRepositoryOperations implements ReactorReactiveRepositoryOperations {

        @Override
        public <T> Mono<Boolean> exists(@NonNull PreparedQuery<T, Boolean> pq) {
            SqlPreparedQuery<T, Boolean> preparedQuery = getSqlPreparedQuery(pq);
            return executeReadMono(preparedQuery, connection -> {
                Statement statement = prepareStatement(connection::createStatement, preparedQuery, false, true);
                preparedQuery.bindParameters(new R2dbcParameterBinder(connection, statement, preparedQuery));
                return executeAndMapEachRow(statement, row -> true).collectList()
                    .map(records -> !records.isEmpty() && records.stream().allMatch(v -> v));
            });
        }

        @NonNull
        @Override
        public <T, R> Mono<R> findOne(@NonNull PreparedQuery<T, R> pq) {
            SqlPreparedQuery<T, R> preparedQuery = getSqlPreparedQuery(pq);
            return executeReadMono(preparedQuery, connection -> {
                Statement statement = prepareStatement(connection::createStatement, preparedQuery, false, true);
                preparedQuery.bindParameters(new R2dbcParameterBinder(connection, statement, preparedQuery));

                SqlTypeMapper<Row, R> mapper = createMapper(preparedQuery, Row.class);
                if (mapper instanceof SqlResultEntityTypeMapper<Row, R> entityTypeMapper) {
                    final boolean hasJoins = !preparedQuery.getJoinFetchPaths().isEmpty();
                    if (!hasJoins) {
                        return executeAndMapEachRow(statement, entityTypeMapper::readEntity);
                    }
                    SqlResultEntityTypeMapper.PushingMapper<Row, R> rowsMapper = entityTypeMapper.readOneMapper();
                    return executeAndMapEachRow(statement, row -> {
                        rowsMapper.processRow(row);
                        return "";
                    }).collectList().flatMap(ignore -> Mono.justOrEmpty(rowsMapper.getResult()));
                }
                return executeAndMapEachRowNullable(statement, row -> mapper.map(row, preparedQuery.getResultType()));
            });
        }

        @NonNull
        @Override
        public <T, R> Flux<R> findAll(@NonNull PreparedQuery<T, R> pq) {
            SqlPreparedQuery<T, R> preparedQuery = getSqlPreparedQuery(pq);
            return executeReadFlux(preparedQuery, connection -> {
                Statement statement = prepareStatement(connection::createStatement, preparedQuery, false, false);
                preparedQuery.bindParameters(new R2dbcParameterBinder(connection, statement, preparedQuery));

                SqlTypeMapper<Row, R> mapper = createMapper(preparedQuery, Row.class);
                if (mapper instanceof SqlResultEntityTypeMapper<Row, R> entityTypeMapper) {
                    SqlResultEntityTypeMapper.PushingMapper<Row, List<R>> rowsMapper = entityTypeMapper.readManyMapper();
                    return executeAndMapEachRow(statement, row -> {
                        rowsMapper.processRow(row);
                        return "";
                    }).collectList().flatMapIterable(ignore -> rowsMapper.getResult());
                }
                return executeAndMapEachRowNullable(statement, row -> mapper.map(row, preparedQuery.getResultType()));
            });
        }

        @NonNull
        @Override
        public Mono<Number> executeUpdate(@NonNull PreparedQuery<?, Number> pq) {
            SqlPreparedQuery<?, Number> preparedQuery = getSqlPreparedQuery(pq);
            return executeWriteMono(preparedQuery, connection -> {
                Statement statement = prepareStatement(connection::createStatement, preparedQuery, true, true);
                Dialect dialect = preparedQuery.getDialect();
                preparedQuery.bindParameters(new R2dbcParameterBinder(connection, statement, preparedQuery));
                return executeAndGetRowsUpdatedSingle(statement, dialect)
                    .flatMap((Number rowsUpdated) -> {
                        if (QUERY_LOG.isTraceEnabled()) {
                            QUERY_LOG.trace("Update operation updated {} records", rowsUpdated);
                        }
                        if (preparedQuery.isOptimisticLock()) {
                            checkOptimisticLocking(1, rowsUpdated);
                        }
                        Argument<?> argument = preparedQuery.getResultArgument().getFirstTypeVariable().orElse(null);
                        if (argument != null) {
                            if (argument.isVoid() || argument.getType() == Void.class) {
                                return Mono.empty();
                            } else if (argument.getType().isInstance(rowsUpdated)) {
                                return Mono.just(rowsUpdated);
                            } else {
                                return Mono.just((Number) columnIndexResultSetReader.convertRequired(rowsUpdated, argument));
                            }
                        }
                        return Mono.just(rowsUpdated);
                    });
            });
        }

        @NonNull
        @Override
        public Mono<Number> executeDelete(@NonNull PreparedQuery<?, Number> preparedQuery) {
            return executeUpdate(preparedQuery);
        }

        @NonNull
        @Override
        public <R> Flux<R> execute(@NonNull PreparedQuery<?, R> pq) {
            SqlPreparedQuery<?, R> preparedQuery = getSqlPreparedQuery(pq);
            return executeWriteFlux(preparedQuery, connection -> {
                if (preparedQuery.isProcedure()) {
                    int outIndex = preparedQuery.getQueryBindings().size();
                    Statement statement = prepareStatement(connection::createStatement, preparedQuery, true, true);
                    preparedQuery.bindParameters(new R2dbcParameterBinder(connection, statement, preparedQuery));
                    if (!preparedQuery.getResultArgument().isVoid()) {
                        statement = statement.bind(outIndex, Parameters.out(preparedQuery.getResultType()));
                    }
                    if (preparedQuery.getResultArgument().isVoid()) {
                        return executeAndGetRowsUpdated(statement).thenMany(Flux.empty());
                    }
                    return executeAndMapEachReadable(statement, preparedQuery.getDialect(), readable -> readable.get(0, preparedQuery.getResultType()));
                } else {
                    throw new IllegalStateException("Not implemented");
                }
            });
        }

        @NonNull
        @Override
        public <T> Mono<Number> delete(@NonNull DeleteOperation<T> operation) {
            return executeWriteMono(operation, status -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                final R2dbcOperationContext ctx = createContext(operation, status, storedQuery);
                R2dbcEntityOperations<T> op = new R2dbcEntityOperations<>(ctx, storedQuery.getPersistentEntity(), operation.getEntity(), storedQuery);
                op.delete();
                return op.getRowsUpdated();
            });
        }

        @NonNull
        @Override
        public <T> Flux<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
            return executeWriteFlux(operation, status -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                final RuntimePersistentEntity<T> persistentEntity = storedQuery.getPersistentEntity();
                final R2dbcOperationContext ctx = createContext(operation, status, storedQuery);
                if (!isSupportsBatchInsert(persistentEntity, storedQuery)) {
                    return concatMono(
                        operation.split().stream()
                            .map(persistOp -> {
                                R2dbcEntityOperations<T> op = new R2dbcEntityOperations<>(ctx, storedQuery, persistentEntity, persistOp.getEntity(), true);
                                op.persist();
                                return op.getEntity();
                            })
                    );
                } else {
                    R2dbcEntitiesOperations<T> op = new R2dbcEntitiesOperations<>(ctx, storedQuery, persistentEntity, operation, true);
                    op.persist();
                    return op.getEntities();
                }
            });
        }

        @NonNull
        @Override
        public <T, R> Mono<R> findOptional(@NonNull PreparedQuery<T, R> preparedQuery) {
            return findOne(preparedQuery);
        }

        @NonNull
        @Override
        public <T> Mono<T> persist(@NonNull InsertOperation<T> operation) {
            return executeWriteMono(operation, status -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                final R2dbcOperationContext ctx = createContext(operation, status, storedQuery);
                R2dbcEntityOperations<T> op = new R2dbcEntityOperations<>(ctx, storedQuery, storedQuery.getPersistentEntity(), operation.getEntity(), true);
                op.persist();
                return op.getEntity();
            });
        }

        @NonNull
        @Override
        public <T> Mono<T> update(@NonNull UpdateOperation<T> operation) {
            return executeWriteMono(operation, status -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                final R2dbcOperationContext ctx = createContext(operation, status, storedQuery);
                R2dbcEntityOperations<T> op = new R2dbcEntityOperations<>(ctx, storedQuery.getPersistentEntity(), operation.getEntity(), storedQuery);
                op.update();
                return op.getEntity();
            });
        }

        private <R> Mono<R> executeWriteMono(@NonNull PreparedDataOperation<?> operation,
                                             @NonNull Function<Connection, Publisher<R>> entityOperation) {
            return withConnectionMono(operation, true, connection -> Mono.fromDirect(entityOperation.apply(connection)));
        }

        private <R> Flux<R> executeWriteFlux(@NonNull PreparedDataOperation<?> operation,
                                             @NonNull Function<Connection, Flux<R>> entityOperation) {
            return withConnectionFlux(operation, true, entityOperation);
        }

        private <R> Mono<R> executeReadMono(@NonNull PreparedDataOperation<?> operation,
                                            @NonNull Function<Connection, Publisher<R>> entityOperation) {
            return withConnectionMono(operation, false, connection -> Mono.fromDirect(entityOperation.apply(connection)));
        }

        private <R> Flux<R> executeReadFlux(@NonNull PreparedDataOperation<?> operation,
                                            @NonNull Function<Connection, Flux<R>> entityOperation) {
            return withConnectionFlux(operation, false, entityOperation);
        }

        private <R> Flux<R> withConnectionFlux(@NonNull PreparedDataOperation<?> operation,
                                               boolean isWrite,
                                               @NonNull Function<Connection, Flux<R>> callback) {
            @SuppressWarnings("unchecked")
            ReactiveTransactionStatus<Connection> tx = operation
                    .getParameterInRole(R2dbcRepository.PARAMETER_TX_STATUS_ROLE, ReactiveTransactionStatus.class).orElse(null);
            if (tx != null) {
                try {
                    return Flux.from(callback.apply(tx.getConnection()));
                } catch (Exception e) {
                    return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                }
            }
            return connectionOperations.withConnectionFlux(
                isWrite ? ConnectionDefinition.DEFAULT : ConnectionDefinition.READ_ONLY,
                status -> callback.apply(status.getConnection())
            );
        }

        private <R> Mono<R> withConnectionMono(@NonNull PreparedDataOperation<?> operation,
                                               boolean isWrite,
                                               @NonNull Function<Connection, Mono<R>> callback) {
            @SuppressWarnings("unchecked")
            ReactiveTransactionStatus<Connection> tx = operation
                    .getParameterInRole(R2dbcRepository.PARAMETER_TX_STATUS_ROLE, ReactiveTransactionStatus.class).orElse(null);
            if (tx != null) {
                try {
                    return Mono.fromDirect(callback.apply(tx.getConnection()));
                } catch (Exception e) {
                    return Mono.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                }
            }
            return connectionOperations.withConnectionMono(
                isWrite ? ConnectionDefinition.DEFAULT : ConnectionDefinition.READ_ONLY,
                status -> callback.apply(status.getConnection())
            );
        }

        @NonNull
        @Override
        public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
            return executeWriteMono(operation, connection -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                RuntimePersistentEntity<T> persistentEntity = storedQuery.getPersistentEntity();
                final R2dbcOperationContext ctx = createContext(operation, connection, storedQuery);
                if (isSupportsBatchDelete(persistentEntity, storedQuery.getDialect())) {
                    R2dbcEntitiesOperations<T> op = new R2dbcEntitiesOperations<>(ctx, persistentEntity, operation, storedQuery);
                    op.delete();
                    return op.getRowsUpdated();
                }
                return sum(
                    operation.split().stream()
                        .map(deleteOp -> {
                            R2dbcEntityOperations<T> op = new R2dbcEntityOperations<>(ctx, persistentEntity, deleteOp.getEntity(), storedQuery);
                            op.delete();
                            return op.getRowsUpdated();
                        })
                );
            });
        }

        @NonNull
        @Override
        public <T> Flux<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
            return executeWriteFlux(operation, connection -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                final R2dbcOperationContext ctx = createContext(operation, connection, storedQuery);
                final RuntimePersistentEntity<T> persistentEntity = storedQuery.getPersistentEntity();
                if (!isSupportsBatchUpdate(persistentEntity, storedQuery)) {
                    return concatMono(
                        operation.split().stream()
                            .map(updateOp -> {
                                R2dbcEntityOperations<T> op = new R2dbcEntityOperations<>(ctx, persistentEntity, updateOp.getEntity(), storedQuery);
                                op.update();
                                return op.getEntity();
                            })
                    );
                }
                R2dbcEntitiesOperations<T> op = new R2dbcEntitiesOperations<>(ctx, persistentEntity, operation, storedQuery);
                op.update();
                return op.getEntities();
            });
        }

        private <T> R2dbcOperationContext createContext(EntityOperation<T> operation, Connection connection, SqlStoredQuery<T, ?> storedQuery) {
            return new R2dbcOperationContext(operation.getAnnotationMetadata(), operation.getInvocationContext(), operation.getRepositoryType(), storedQuery.getDialect(), connection);
        }

        @NonNull
        @Override
        public <T> Mono<T> findOptional(@NonNull Class<T> type, @NonNull Object id) {
            throw new UnsupportedOperationException("The findOptional method by ID is not supported. Execute the SQL query directly");
        }

        @NonNull
        @Override
        public <R> Mono<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery) {
            throw new UnsupportedOperationException("The findPage method is not supported. Execute the SQL query directly");
        }

        @NonNull
        @Override
        public <T> Mono<T> findOne(@NonNull Class<T> type, @NonNull Object id) {
            throw new UnsupportedOperationException("The findOne method by ID is not supported. Execute the SQL query directly");
        }

        @NonNull
        @Override
        public <T> Mono<Long> count(PagedQuery<T> pagedQuery) {
            throw new UnsupportedOperationException("The count method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
        }

        @NonNull
        @Override
        public <T> Flux<T> findAll(PagedQuery<T> pagedQuery) {
            throw new UnsupportedOperationException("The findAll method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
        }

        @Override
        public ConversionService getConversionService() {
            return conversionService;
        }
    }

    private final class R2dbcParameterBinder implements BindableParametersStoredQuery.Binder {

        private final Connection connection;
        private final Statement ps;
        private final SqlStoredQuery<?, ?> sqlStoredQuery;

        private int index = 0;

        private R2dbcParameterBinder(R2dbcOperationContext ctx, Statement ps, SqlStoredQuery<?, ?> sqlStoredQuery) {
            this(ctx.connection, ps, sqlStoredQuery);
        }

        private R2dbcParameterBinder(Connection connection, Statement ps, SqlStoredQuery<?, ?> sqlStoredQuery) {
            this.connection = connection;
            this.ps = ps;
            this.sqlStoredQuery = sqlStoredQuery;
        }

        @Override
        public Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
            return runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
        }

        @Override
        public Object convert(Object value, RuntimePersistentProperty<?> property) {
            if (property == null) {
                return value;
            }
            AttributeConverter<Object, Object> converter = property.getConverter();
            if (converter != null) {
                return converter.convertToPersistedValue(value, createTypeConversionContext(property, property.getArgument()));
            }
            return value;
        }

        @Override
        public Object convert(Class<?> converterClass, Object value, Argument<?> argument) {
            if (converterClass == null) {
                return value;
            }
            AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
            ConversionContext conversionContext = createTypeConversionContext(null, argument);
            return converter.convertToPersistedValue(value, conversionContext);
        }

        private ConversionContext createTypeConversionContext(@Nullable RuntimePersistentProperty<?> property,
                                                              @Nullable Argument<?> argument) {
            if (property != null) {
                return new RuntimePersistentPropertyR2dbcCC(connection, property);
            }
            if (argument != null) {
                return new ArgumentR2dbcCC(connection, argument);
            }
            return new R2dbcConversionContextImpl(connection);
        }

        @Override
        public void bindOne(QueryParameterBinding binding, Object value) {
            JsonDataType jsonDataType = null;
            if (binding.getDataType() == DataType.JSON) {
                jsonDataType = binding.getJsonDataType();
            }
            setStatementParameter(ps, index, binding.getDataType(), jsonDataType, value, sqlStoredQuery);
            index++;
        }

        @Override
        public void bindMany(QueryParameterBinding binding, Collection<Object> values) {
            for (Object value : values) {
                bindOne(binding, value);
            }

        }

        @Override
        public int currentIndex() {
            return index;
        }

    }

    private final class R2dbcEntityOperations<T> extends AbstractReactiveEntityOperations<R2dbcOperationContext, T, RuntimeException> {
        private final SqlStoredQuery<T, ?> storedQuery;

        private R2dbcEntityOperations(R2dbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity, SqlStoredQuery<T, ?> storedQuery) {
            this(ctx, storedQuery, persistentEntity, entity, false);
        }

        private R2dbcEntityOperations(R2dbcOperationContext ctx, SqlStoredQuery<T, ?> storedQuery, RuntimePersistentEntity<T> persistentEntity, T entity, boolean insert) {
            super(ctx,
                DefaultR2dbcRepositoryOperations.this.cascadeOperations,
                DefaultR2dbcRepositoryOperations.this.conversionService,
                entityEventRegistry,
                persistentEntity, entity, insert);
            this.storedQuery = storedQuery;
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
            data = data.map(d -> {
                if (d.vetoed) {
                    return d;
                }
                d.previousValues = storedQuery.collectAutoPopulatedPreviousValues(d.entity);
                return d;
            });
        }

        private Statement prepare(Connection connection) throws RuntimeException {
            if (storedQuery instanceof SqlPreparedQuery<T, ?> sqlPreparedQuery) {
                data = data.map(d -> {
                    if (d.vetoed) {
                        return d;
                    }
                    sqlPreparedQuery.prepare(d.entity);
                    return d;
                });
            }
            LOG.debug(storedQuery.getQuery());
            Statement statement = connection.createStatement(storedQuery.getQuery());
            if (hasGeneratedId) {
                if (isJsonEntityGeneratedId(storedQuery, persistentEntity)) {
                    return statement.bind(storedQuery.getQueryBindings().size(), Parameters.out(R2dbcType.NUMERIC));
                } else {
                    return statement.returnGeneratedValues(persistentEntity.getIdentity().getPersistedName());
                }
            }
            return statement;
        }

        private void setParameters(Statement stmt, SqlStoredQuery<T, ?> storedQuery) {
            data = data.map(d -> {
                if (d.vetoed) {
                    return d;
                }
                storedQuery.bindParameters(new R2dbcParameterBinder(ctx, stmt, storedQuery), ctx.invocationContext, d.entity, d.previousValues);
                return d;
            });
        }

        @Override
        protected void execute() throws RuntimeException {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL query: {}", storedQuery.getQuery());
            }
            Statement statement = prepare(ctx.connection);
            setParameters(statement, storedQuery);
            if (hasGeneratedId) {
                data = data.flatMap(d -> {
                    if (d.vetoed) {
                        return Mono.just(d);
                    }
                    RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                    Function<Object, Data> idMapper = id -> {
                        BeanProperty<T, Object> property = identity.getProperty();
                        d.entity = updateEntityId(property, d.entity, id);
                        return d;
                    };
                    if (isJsonEntityGeneratedId(storedQuery, persistentEntity)) {
                        return Flux.from(statement.execute()).flatMap(result -> Flux.from(result.map(outParameters -> outParameters.get(0, Object.class))))
                            .onErrorResume(errorHandler(ctx.dialect)).map(idMapper).last();
                    } else {
                        return executeAndMapEachRowSingle(statement, ctx.dialect, row -> columnIndexResultSetReader.readDynamic(row, 0, identity.getDataType()))
                            .map(idMapper);
                    }
                });
            } else {
                data = data.flatMap(d -> {
                    if (d.vetoed) {
                        return Mono.just(d);
                    }
                    return executeAndGetRowsUpdatedSingle(statement, ctx.dialect).map(rowsUpdated -> {
                        d.rowsUpdated = rowsUpdated.longValue();
                        return d;
                    });
                });
            }
            if (storedQuery.isOptimisticLock()) {
                data = data.map(d -> {
                    checkOptimisticLocking(1, d.rowsUpdated);
                    return d;
                });
            }
        }
    }

    private final class R2dbcEntitiesOperations<T> extends AbstractReactiveEntitiesOperations<R2dbcOperationContext, T, RuntimeException> {

        private final SqlStoredQuery<T, ?> storedQuery;

        private R2dbcEntitiesOperations(R2dbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, SqlStoredQuery storedQuery) {
            this(ctx, storedQuery, persistentEntity, entities, false);
        }

        private R2dbcEntitiesOperations(R2dbcOperationContext ctx, SqlStoredQuery storedQuery, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, boolean insert) {
            super(ctx,
                DefaultR2dbcRepositoryOperations.this.cascadeOperations,
                DefaultR2dbcRepositoryOperations.this.conversionService,
                entityEventRegistry,
                persistentEntity, entities, insert);
            this.storedQuery = storedQuery;
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
            entities = entities.map(list -> {
                for (Data d : list) {
                    if (d.vetoed) {
                        continue;
                    }
                    d.previousValues = storedQuery.collectAutoPopulatedPreviousValues(d.entity);
                }
                return list;
            });
        }

        private void setParameters(Statement stmt, SqlStoredQuery<T, ?> storedQuery) {
            AtomicBoolean isFirst = new AtomicBoolean(true);
            entities = entities.map(list -> {
                for (Data d : list) {
                    if (d.vetoed) {
                        continue;
                    }
                    if (isFirst.get()) {
                        isFirst.set(false);
                    } else {
                        // https://github.com/r2dbc/r2dbc-spi/issues/259
                        stmt.add();
                    }
                    storedQuery.bindParameters(new R2dbcParameterBinder(ctx, stmt, storedQuery), ctx.invocationContext, d.entity, d.previousValues);
                }
                return list;
            });
        }

        @Override
        protected void execute() throws RuntimeException {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL query: {}", storedQuery.getQuery());
            }
            Statement statement;
            if (hasGeneratedId) {
                statement = ctx.connection.createStatement(storedQuery.getQuery());
                if (isJsonEntityGeneratedId(storedQuery, persistentEntity)) {
                    statement.bind(storedQuery.getQueryBindings().size(), Parameters.out(R2dbcType.NUMERIC));
                } else {
                    statement.returnGeneratedValues(persistentEntity.getIdentity().getPersistedName());
                }
            } else {
                statement = ctx.connection.createStatement(storedQuery.getQuery());
            }
            setParameters(statement, storedQuery);
            if (hasGeneratedId) {
                entities = entities
                    .flatMap(list -> {
                        List<Data> notVetoedEntities = list.stream().filter(this::notVetoed).toList();
                        if (notVetoedEntities.isEmpty()) {
                            return Mono.just(notVetoedEntities);
                        }
                        Function<Row, Object> idMapper;
                        if (isJsonEntityGeneratedId(storedQuery, persistentEntity)) {
                            idMapper = row -> row.get(0, Object.class);
                        } else {
                            idMapper = row -> columnIndexResultSetReader.readDynamic(row, 0, persistentEntity.getIdentity().getDataType());
                        }
                        Mono<List<Object>> ids = executeAndMapEachRow(statement, idMapper).collectList();

                        return ids.flatMap(idList -> {
                            Iterator<Object> iterator = idList.iterator();
                            ListIterator<Data> resultIterator = notVetoedEntities.listIterator();
                            RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                            while (resultIterator.hasNext()) {
                                Data d = resultIterator.next();
                                if (!iterator.hasNext()) {
                                    throw new DataAccessException("Failed to generate ID for entity: " + d.entity);
                                } else {
                                    Object id = iterator.next();
                                    d.entity = updateEntityId(identity.getProperty(), d.entity, id);
                                }
                            }
                            return Mono.just(list);
                        });
                    });
            } else {
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities
                    .flatMap(list -> {
                        List<Data> notVetoedEntities = list.stream().filter(this::notVetoed).toList();
                        if (notVetoedEntities.isEmpty()) {
                            return Mono.just(Tuples.of(list, 0L));
                        }
                        return executeAndGetRowsUpdated(statement)
                            .map(Number::longValue)
                            .reduce(0L, Long::sum)
                            .map(rowsUpdated -> {
                                if (storedQuery.isOptimisticLock()) {
                                    checkOptimisticLocking(notVetoedEntities.size(), rowsUpdated);
                                }
                                return Tuples.of(list, rowsUpdated);
                            });
                    }).cache();
                entities = entitiesWithRowsUpdated.flatMap(t -> Mono.just(t.getT1()));
                rowsUpdated = entitiesWithRowsUpdated.map(Tuple2::getT2);
            }
        }
    }

    protected static class R2dbcOperationContext extends OperationContext {

        private final Connection connection;
        private final Dialect dialect;
        private final InvocationContext<?, ?> invocationContext;

        /**
         * The default constructor.
         *
         * @param annotationMetadata the annotation metadata
         * @param invocationContext  the invocation context
         * @param repositoryType     the repository type
         * @param dialect            the dialect
         * @param connection         the connection
         */
        public R2dbcOperationContext(AnnotationMetadata annotationMetadata, InvocationContext<?, ?> invocationContext, Class<?> repositoryType, Dialect dialect, Connection connection) {
            super(annotationMetadata, repositoryType);
            this.dialect = dialect;
            this.connection = connection;
            this.invocationContext = invocationContext;
        }
    }

    private static final class RuntimePersistentPropertyR2dbcCC extends R2dbcConversionContextImpl implements RuntimePersistentPropertyConversionContext {

        private final RuntimePersistentProperty<?> property;

        public RuntimePersistentPropertyR2dbcCC(Connection connection, RuntimePersistentProperty<?> property) {
            super(ConversionContext.of(property.getArgument()), connection);
            this.property = property;
        }

        @Override
        public RuntimePersistentProperty<?> getRuntimePersistentProperty() {
            return property;
        }
    }

    private static final class ArgumentR2dbcCC extends R2dbcConversionContextImpl implements ArgumentConversionContext<Object> {

        private final Argument argument;

        public ArgumentR2dbcCC(Connection connection, Argument argument) {
            super(ConversionContext.of(argument), connection);
            this.argument = argument;
        }

        @Override
        public Argument<Object> getArgument() {
            return argument;
        }
    }

    private static class R2dbcConversionContextImpl extends AbstractConversionContext
        implements R2dbcConversionContext {

        private final Connection connection;

        public R2dbcConversionContextImpl(Connection connection) {
            this(ConversionContext.DEFAULT, connection);
        }

        public R2dbcConversionContextImpl(ConversionContext conversionContext, Connection connection) {
            super(conversionContext);
            this.connection = connection;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

    }
}
