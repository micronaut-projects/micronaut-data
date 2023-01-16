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
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.attr.AttributeHolder;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.EntityOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
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
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.convert.RuntimePersistentPropertyConversionContext;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.mapper.TypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlDTOMapper;
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import io.micronaut.data.runtime.operations.ReactorToAsyncOperationsAdaptor;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntitiesOperations;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.data.runtime.operations.internal.ReactiveCascadeOperations;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.AbstractSqlRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.data.runtime.support.AbstractConversionContext;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import io.micronaut.transaction.support.TransactionSynchronizationManager;
import io.micronaut.transaction.support.TransactionUtil;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.IsolationLevel;
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
import reactor.util.context.ContextView;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
    ReactorReactiveTransactionOperations<Connection>, ReactiveCascadeOperations.ReactiveCascadeOperationsHelper<DefaultR2dbcRepositoryOperations.R2dbcOperationContext> {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultR2dbcRepositoryOperations.class);
    private static final String NAME = "r2dbc";
    private final ConnectionFactory connectionFactory;
    private final ReactorReactiveRepositoryOperations reactiveOperations;
    private final String dataSourceName;
    private ExecutorService ioExecutorService;
    private AsyncRepositoryOperations asyncRepositoryOperations;
    private final ReactiveCascadeOperations<R2dbcOperationContext> cascadeOperations;
    private final String txStatusKey;
    private final String txDefinitionKey;
    private final String currentConnectionKey;
    @Nullable
    private final SchemaTenantResolver schemaTenantResolver;
    private final R2dbcSchemaHandler schemaHandler;
    private final DataR2dbcConfiguration configuration;

    /**
     * Default constructor.
     *
     * @param dataSourceName             The data source name
     * @param connectionFactory          The associated connection factory
     * @param mediaTypeCodecList         The media type codec list
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The runtime entity registry
     * @param applicationContext         The bean context
     * @param executorService            The executor
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     * @param schemaTenantResolver       The schema tenant resolver
     * @param schemaHandler              The schema handler
     * @param configuration              The configuration
     */
    @Internal
    protected DefaultR2dbcRepositoryOperations(
        @Parameter String dataSourceName,
        ConnectionFactory connectionFactory,
        List<MediaTypeCodec> mediaTypeCodecList,
        @NonNull DateTimeProvider<Object> dateTimeProvider,
        RuntimeEntityRegistry runtimeEntityRegistry,
        ApplicationContext applicationContext,
        @Nullable @Named("io") ExecutorService executorService,
        DataConversionService conversionService,
        AttributeConverterRegistry attributeConverterRegistry,
        @Nullable SchemaTenantResolver schemaTenantResolver,
        R2dbcSchemaHandler schemaHandler,
        @Parameter DataR2dbcConfiguration configuration) {
        super(
            dataSourceName,
            new ColumnNameR2dbcResultReader(conversionService),
            new ColumnIndexR2dbcResultReader(conversionService),
            new R2dbcQueryStatement(conversionService),
            mediaTypeCodecList,
            dateTimeProvider,
            runtimeEntityRegistry,
            applicationContext,
            conversionService, attributeConverterRegistry);
        this.connectionFactory = connectionFactory;
        this.ioExecutorService = executorService;
        this.schemaTenantResolver = schemaTenantResolver;
        this.schemaHandler = schemaHandler;
        this.configuration = configuration;
        this.reactiveOperations = new DefaultR2dbcReactiveRepositoryOperations();
        this.dataSourceName = dataSourceName;
        this.cascadeOperations = new ReactiveCascadeOperations<>(conversionService, this);
        String name = dataSourceName;
        if (name == null) {
            name = "default";
        }
        this.txStatusKey = ReactorReactiveTransactionOperations.TRANSACTION_STATUS_KEY_PREFIX + "." + NAME + "." + name;
        this.txDefinitionKey = ReactorReactiveTransactionOperations.TRANSACTION_DEFINITION_KEY_PREFIX + "." + NAME + "." + name;
        this.currentConnectionKey = "io.micronaut." + NAME + ".connection." + name;
    }

    @Override
    public <T> T block(Function<io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations, Mono<T>> supplier) {
        TransactionSynchronizationManager.TransactionSynchronizationState state = TransactionSynchronizationManager.getOrCreateState();
        return Mono.defer(() -> {
            try (TransactionSynchronizationManager.TransactionSynchronizationStateOp ignore = TransactionSynchronizationManager.withState(state)) {
                return supplier.apply(reactive())
                    .contextWrite(TransactionSynchronizationManager.getResourceOrDefault(ContextView.class, Context.empty()));
            }
        }).block();
    }

    @Override
    public <T> Optional<T> blockOptional(Function<io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations, Mono<T>> supplier) {
        TransactionSynchronizationManager.TransactionSynchronizationState state = TransactionSynchronizationManager.getOrCreateState();
        return Mono.defer(() -> {
                try (TransactionSynchronizationManager.TransactionSynchronizationStateOp ignore = TransactionSynchronizationManager.withState(state)) {
                    return supplier.apply(reactive())
                        .contextWrite(TransactionSynchronizationManager.getResourceOrDefault(ContextView.class, Context.empty()));
                }
            })
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
        return Flux.concat(stream.collect(Collectors.toList()));
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

    private <T> Flux<T> withConnectionWithCancelCallback(@NonNull BiFunction<Connection, Supplier<Publisher<Void>>, Publisher<? extends T>> handler) {
        Objects.requireNonNull(handler, "Handler cannot be null");
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating a new Connection for DataSource: " + dataSourceName);
        }
        return Mono.from(connectionFactory.create()).flatMapMany(connection -> {
            Supplier<Publisher<Void>> cancelCallback = () -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closing Connection for DataSource: " + dataSourceName);
                }
                return connection.close();
            };
            Function<Connection, Publisher<? extends T>> theHandler = (c -> handler.apply(c, cancelCallback));
            return tenantAwareHandler(theHandler).apply(connection);
        });
    }

    private IsolationLevel getIsolationLevel(TransactionDefinition definition) {
        TransactionDefinition.Isolation isolationLevel = definition.getIsolationLevel();
        switch (isolationLevel) {
            case READ_COMMITTED:
                return IsolationLevel.READ_COMMITTED;
            case READ_UNCOMMITTED:
                return IsolationLevel.READ_UNCOMMITTED;
            case REPEATABLE_READ:
                return IsolationLevel.REPEATABLE_READ;
            case SERIALIZABLE:
                return IsolationLevel.SERIALIZABLE;
            default:
                return null;
        }
    }

    @NonNull
    @Override
    public <T> Publisher<T> withTransaction(
        @NonNull ReactiveTransactionStatus<Connection> status,
        @NonNull ReactiveTransactionOperations.TransactionalCallback<Connection, T> handler) {
        Objects.requireNonNull(status, "Transaction status cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");
        return Flux.defer(() -> {
            try {
                return handler.doInTransaction(status);
            } catch (Exception e) {
                return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
            }
        }).contextWrite(context -> context.put(txStatusKey, status));
    }

    @Override
    public ReactiveTransactionStatus<Connection> getTransactionStatus(ContextView contextView) {
        return contextView.getOrDefault(txStatusKey, null);
    }

    @Override
    public TransactionDefinition getTransactionDefinition(ContextView contextView) {
        return contextView.getOrDefault(txDefinitionKey, null);
    }

    @Override
    @NonNull
    public <T> Flux<T> withTransaction(@NonNull TransactionDefinition definition,
                                       @NonNull ReactiveTransactionOperations.TransactionalCallback<Connection, T> handler) {
        Objects.requireNonNull(definition, "Transaction definition cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");

        return Flux.deferContextual(contextView -> {
            TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
            ReactiveTransactionStatus<Connection> transactionStatus = getTransactionStatus(contextView);
            if (transactionStatus != null) {
                // existing transaction, use it
                if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED || propagationBehavior == TransactionDefinition.Propagation.NEVER) {
                    return Flux.error(new TransactionUsageException("Found an existing transaction but propagation behaviour doesn't support it: " + propagationBehavior));
                }
                ReactiveTransactionStatus<Connection> existingTransaction = existingTransaction(transactionStatus);
                try {
                    return Flux.from(handler.doInTransaction(existingTransaction))
                        .contextWrite(ctx -> ctx.put(txStatusKey, existingTransaction));
                } catch (Exception e) {
                    return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                }
            } else {
                if (propagationBehavior == TransactionDefinition.Propagation.MANDATORY) {
                    return Flux.error(new NoTransactionException("Expected an existing transaction, but none was found in the Reactive context."));
                }
                return withConnectionWithCancelCallback((connection, cancelCallback) -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Transaction: {} begin for dataSource: {}", definition.getName(), dataSourceName);
                        }
                        DefaultReactiveTransactionStatus status = new DefaultReactiveTransactionStatus(definition, connection, true);
                        Mono<Boolean> resourceSupplier;
                        if (definition.getIsolationLevel() != TransactionDefinition.DEFAULT.getIsolationLevel()) {
                            IsolationLevel isolationLevel = getIsolationLevel(definition);
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Setting Isolation Level ({}) for transaction: {} for dataSource: {}", isolationLevel, definition.getName(), dataSourceName);
                            }
                            if (isolationLevel != null) {
                                resourceSupplier = Flux.from(connection.setTransactionIsolationLevel(isolationLevel))
                                    .thenMany(connection.beginTransaction())
                                    .hasElements();
                            } else {
                                resourceSupplier = Flux.from(connection.beginTransaction()).hasElements();
                            }
                        } else {
                            resourceSupplier = Flux.from(connection.beginTransaction()).hasElements();
                        }

                        Function<Boolean, Publisher<?>> onSuccess = ignore -> doCommit(status, cancelCallback);
                        BiFunction<Boolean, Throwable, Publisher<?>> onException = (b, throwable) -> onException(status, definition, throwable, cancelCallback);

                        return Flux.usingWhen(resourceSupplier,
                            (b) -> {
                                try {
                                    return Flux.from(handler.doInTransaction(status)).contextWrite(context ->
                                        context.put(txStatusKey, status)
                                            .put(txDefinitionKey, definition)
                                    );
                                } catch (Exception e) {
                                    return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                                }
                            },
                            onSuccess,
                            onException,
                            onSuccess);
                    }
                );
            }
        });
    }

    private Flux<Void> onException(DefaultReactiveTransactionStatus status,
                                   TransactionDefinition definition,
                                   Throwable throwable,
                                   Supplier<Publisher<Void>> cancelConnection) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Rolling back transaction: {} on error: {} for dataSource {}",
                status.getDefinition().getName(), throwable.getMessage(), dataSourceName, throwable);
        }
        if (!definition.rollbackOn(throwable)) {
            return doCommit(status, cancelConnection);
        }
        return rollback(status, cancelConnection)
            .onErrorResume((rollbackError) -> {
                if (rollbackError != throwable && LOG.isWarnEnabled()) {
                    LOG.warn("Error occurred during transaction: {} rollback failed with: {} for dataSource {}",
                        status.getDefinition().getName(), rollbackError.getMessage(), dataSourceName, rollbackError);
                }
                return Mono.error(throwable);
            });
    }

    private Flux<Void> rollback(DefaultReactiveTransactionStatus status, Supplier<Publisher<Void>> cancelConnection) {
        return Flux.from(status.getConnection().rollbackTransaction()).as(flux -> finishTx(flux, status, cancelConnection));
    }

    private Flux<Void> doCommit(DefaultReactiveTransactionStatus status, Supplier<Publisher<Void>> cancelConnection) {
        if (status.isRollbackOnly()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Rolling back transaction: {} for dataSource {}", status.getDefinition().getName(), dataSourceName);
            }
            return rollback(status, cancelConnection);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Committing transaction: {} for dataSource {}", status.getDefinition().getName(), dataSourceName);
        }
        return Flux.from(status.getConnection().commitTransaction()).as(flux -> finishTx(flux, status, cancelConnection));

    }

    private Flux<Void> finishTx(Flux<Void> flux, DefaultReactiveTransactionStatus status, Supplier<Publisher<Void>> cancelConnection) {
        return flux.hasElements()
            .flatMapMany(ignore -> {
                status.completed = true;
                return cancelConnection.get();
            });
    }

    private ReactiveTransactionStatus<Connection> existingTransaction(ReactiveTransactionStatus<Connection> existing) {
        return new ReactiveTransactionStatus<Connection>() {
            @Override
            public Connection getConnection() {
                return existing.getConnection();
            }

            @Override
            public boolean isNewTransaction() {
                return false;
            }

            @Override
            public void setRollbackOnly() {
                existing.setRollbackOnly();
            }

            @Override
            public boolean isRollbackOnly() {
                return existing.isRollbackOnly();
            }

            @Override
            public boolean isCompleted() {
                return existing.isCompleted();
            }
        };
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

    private static <T> Mono<T> executeAndMapEachRowSingle(Statement statement, Function<Row, T> mapper) {
        return executeAndMapEachRow(statement, mapper).as(DefaultR2dbcRepositoryOperations::toSingleResult);
    }

    private static Mono<Number> executeAndGetRowsUpdatedSingle(Statement statement) {
        return executeAndGetRowsUpdated(statement)
            .as(DefaultR2dbcRepositoryOperations::toSingleResult);
    }

    private static Flux<Number> executeAndGetRowsUpdated(Statement statement) {
        return Flux.from(statement.execute())
            .flatMap(Result::getRowsUpdated)
            .map((Number n) -> n.longValue());
    }

    /**
     * Represents the current reactive transaction status.
     */
    private static final class DefaultReactiveTransactionStatus implements ReactiveTransactionStatus<Connection> {
        private final TransactionDefinition definition;
        private final Connection connection;
        private final boolean isNew;
        private boolean rollbackOnly;
        private boolean completed;

        public DefaultReactiveTransactionStatus(TransactionDefinition definition, Connection connection, boolean isNew) {
            this.definition = definition;
            this.connection = connection;
            this.isNew = isNew;
        }

        public TransactionDefinition getDefinition() {
            return definition;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public boolean isNewTransaction() {
            return isNew;
        }

        @Override
        public void setRollbackOnly() {
            this.rollbackOnly = true;
        }

        @Override
        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        @Override
        public boolean isCompleted() {
            return completed;
        }
    }

    /**
     * reactive operations implementation.
     */
    private final class DefaultR2dbcReactiveRepositoryOperations implements ReactorReactiveRepositoryOperations {

        @Override
        public <T> Mono<Boolean> exists(@NonNull PreparedQuery<T, Boolean> pq) {
            SqlPreparedQuery<T, Boolean> preparedQuery = getSqlPreparedQuery(pq);
            return withNewOrExistingTransactionMono(preparedQuery, false, status -> {
                Connection connection = status.getConnection();
                Statement statement = prepareStatement(connection::createStatement, preparedQuery, false, true);
                preparedQuery.bindParameters(new R2dbcParameterBinder(connection, statement, preparedQuery.getDialect()));
                return executeAndMapEachRow(statement, row -> true).collectList()
                    .map(records -> !records.isEmpty() && records.stream().allMatch(v -> v));
            });
        }

        @NonNull
        @Override
        public <T, R> Mono<R> findOne(@NonNull PreparedQuery<T, R> pq) {
            SqlPreparedQuery<T, R> preparedQuery = getSqlPreparedQuery(pq);
            return withNewOrExistingTransactionMono(preparedQuery, false, status -> {
                Connection connection = status.getConnection();
                Statement statement = prepareStatement(connection::createStatement, preparedQuery, false, true);
                preparedQuery.bindParameters(new R2dbcParameterBinder(connection, statement, preparedQuery.getDialect()));
                if (preparedQuery.getResultDataType() == DataType.ENTITY) {
                    Class<R> resultType = preparedQuery.getResultType();
                    RuntimePersistentEntity<R> persistentEntity = getEntity(resultType);
                    SqlResultEntityTypeMapper<Row, R> mapper = new SqlResultEntityTypeMapper<>(
                        persistentEntity,
                        columnNameResultSetReader,
                        preparedQuery.getJoinFetchPaths(),
                        jsonCodec,
                        (loadedEntity, o) -> {
                            if (loadedEntity.hasPostLoadEventListeners()) {
                                return triggerPostLoad(o, loadedEntity, preparedQuery.getAnnotationMetadata());
                            } else {
                                return o;
                            }
                        },
                        conversionService);
                    SqlResultEntityTypeMapper.PushingMapper<Row, R> rowsMapper = mapper.readOneWithJoins();
                    return executeAndMapEachRow(statement, row -> {
                        rowsMapper.processRow(row);
                        return "";
                    }).collectList().flatMap(ignore -> Mono.justOrEmpty(rowsMapper.getResult()));
                }
                Class<R> resultType = preparedQuery.getResultType();
                if (preparedQuery.isDtoProjection()) {
                    RuntimePersistentEntity<T> persistentEntity = preparedQuery.getPersistentEntity();
                    boolean isRawQuery = preparedQuery.isRawQuery();
                    return executeAndMapEachRow(statement, row -> {
                        TypeMapper<Row, R> introspectedDataMapper =  new SqlDTOMapper<>(
                            persistentEntity,
                            isRawQuery ? getEntity(preparedQuery.getResultType()) : persistentEntity,
                            columnNameResultSetReader,
                            jsonCodec,
                            conversionService
                        );
                        return introspectedDataMapper.map(row, resultType);
                    });
                }
                return executeAndMapEachRow(statement, row -> {
                    Object v = columnIndexResultSetReader.readDynamic(row, 0, preparedQuery.getResultDataType());
                    if (v == null) {
                        return Flux.<R>empty();
                    } else if (resultType.isInstance(v)) {
                        return Flux.just((R) v);
                    } else {
                        return Flux.just(columnIndexResultSetReader.convertRequired(v, resultType));
                    }
                }).flatMap(m -> m);
            });
        }

        @NonNull
        @Override
        public <T, R> Flux<R> findAll(@NonNull PreparedQuery<T, R> pq) {
            SqlPreparedQuery<T, R> preparedQuery = getSqlPreparedQuery(pq);
            return withNewOrExistingTransactionFlux(preparedQuery, false, status -> {
                Connection connection = status.getConnection();
                Statement statement = prepareStatement(connection::createStatement, preparedQuery, false, false);
                preparedQuery.bindParameters(new R2dbcParameterBinder(connection, statement, preparedQuery.getDialect()));
                Class<R> resultType = preparedQuery.getResultType();
                boolean dtoProjection = preparedQuery.isDtoProjection();
                boolean isEntity = preparedQuery.getResultDataType() == DataType.ENTITY;
                if (isEntity || dtoProjection) {
                    TypeMapper<Row, R> mapper;
                    RuntimePersistentEntity<T> persistentEntity = preparedQuery.getPersistentEntity();
                    if (dtoProjection) {
                        boolean isRawQuery = preparedQuery.isRawQuery();
                        mapper = new SqlDTOMapper<>(
                            persistentEntity,
                            isRawQuery ? getEntity(preparedQuery.getResultType()) : persistentEntity,
                            columnNameResultSetReader,
                            jsonCodec,
                            conversionService
                        );
                    } else {
                        Set<JoinPath> joinFetchPaths = preparedQuery.getJoinFetchPaths();
                        SqlResultEntityTypeMapper<Row, R> entityTypeMapper = new SqlResultEntityTypeMapper<>(
                            getEntity(resultType),
                            columnNameResultSetReader,
                            joinFetchPaths,
                            jsonCodec,
                            (loadedEntity, o) -> {
                                if (loadedEntity.hasPostLoadEventListeners()) {
                                    return triggerPostLoad(o, loadedEntity, preparedQuery.getAnnotationMetadata());
                                } else {
                                    return o;
                                }
                            },
                            conversionService);
                        boolean onlySingleEndedJoins = isOnlySingleEndedJoins(persistentEntity, joinFetchPaths);
                        // Cannot stream ResultSet for "many" joined query
                        if (!onlySingleEndedJoins) {
                            SqlResultEntityTypeMapper.PushingMapper<Row, List<R>> manyReader = entityTypeMapper.readAllWithJoins();
                            return executeAndMapEachRow(statement, row -> {
                                manyReader.processRow(row);
                                return "";
                            }).collectList().flatMapIterable(ignore -> manyReader.getResult());
                        } else {
                            mapper = entityTypeMapper;
                        }
                    }
                    return executeAndMapEachRow(statement, row -> mapper.map(row, resultType));
                }
                return executeAndMapEachRow(statement, row -> {
                    Object v = columnIndexResultSetReader.readDynamic(row, 0, preparedQuery.getResultDataType());
                    if (v == null) {
                        return Mono.<R>empty();
                    } else if (resultType.isInstance(v)) {
                        return Mono.just((R) v);
                    } else {
                        Object converted = columnIndexResultSetReader.convertRequired(v, resultType);
                        if (converted != null) {
                            return Mono.just((R) converted);
                        } else {
                            return Mono.<R>empty();
                        }
                    }
                }).flatMap(m -> m);
            });
        }

        @NonNull
        @Override
        public Mono<Number> executeUpdate(@NonNull PreparedQuery<?, Number> pq) {
            SqlPreparedQuery<?, Number> preparedQuery = getSqlPreparedQuery(pq);
            return withNewOrExistingTransactionMono(preparedQuery, true, status -> {
                Connection connection = status.getConnection();
                Statement statement = prepareStatement(connection::createStatement, preparedQuery, true, true);
                preparedQuery.bindParameters(new R2dbcParameterBinder(connection, statement, preparedQuery.getDialect()));
                return executeAndGetRowsUpdatedSingle(statement)
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
        public <T> Mono<Number> delete(@NonNull DeleteOperation<T> operation) {
            return withNewOrExistingTransactionMono(operation, true, status -> {
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
            return withNewOrExistingTransactionFlux(operation, true, status -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                final RuntimePersistentEntity<T> persistentEntity = storedQuery.getPersistentEntity();
                final R2dbcOperationContext ctx = createContext(operation, status, storedQuery);
                if (!isSupportsBatchInsert(persistentEntity, storedQuery.getDialect())) {
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
            return withNewOrExistingTransactionMono(operation, true, status -> {
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
            return withNewOrExistingTransactionMono(operation, true, status -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                final R2dbcOperationContext ctx = createContext(operation, status, storedQuery);
                R2dbcEntityOperations<T> op = new R2dbcEntityOperations<>(ctx, storedQuery.getPersistentEntity(), operation.getEntity(), storedQuery);
                op.update();
                return op.getEntity();
            });
        }

        @NonNull
        private TransactionDefinition newTransactionDefinition(AttributeHolder attributeHolder) {
            return attributeHolder.getAttribute(txDefinitionKey, TransactionDefinition.class).orElseGet(() -> {
                if (attributeHolder instanceof AnnotationMetadataProvider) {
                    String name = null;
                    if (attributeHolder instanceof io.micronaut.core.naming.Named) {
                        name = ((io.micronaut.core.naming.Named) attributeHolder).getName();
                    }
                    return TransactionUtil.getTransactionDefinition(name, ((AnnotationMetadataProvider) attributeHolder));
                }
                return TransactionDefinition.DEFAULT;
            });
        }

        private <T, R> Mono<R> withNewOrExistingTransactionMono(@NonNull EntityOperation<T> operation,
                                                                boolean isWrite,
                                                                TransactionalCallback<Connection, R> entityOperation) {
            @SuppressWarnings("unchecked")
            ReactiveTransactionStatus<Connection> connection = operation
                .getParameterInRole(R2dbcRepository.PARAMETER_TX_STATUS, ReactiveTransactionStatus.class).orElse(null);
            if (connection != null) {
                try {
                    return Mono.fromDirect(entityOperation.doInTransaction(connection));
                } catch (Exception e) {
                    return Mono.error(e);
                }
            } else {
                return withNewOrExistingTxAttributeMono(operation, entityOperation, isWrite);
            }
        }

        private <T, R> Flux<R> withNewOrExistingTransactionFlux(@NonNull EntityOperation<T> operation,
                                                                boolean isWrite,
                                                                TransactionalCallback<Connection, R> entityOperation) {
            @SuppressWarnings("unchecked")
            ReactiveTransactionStatus<Connection> connection = operation
                .getParameterInRole(R2dbcRepository.PARAMETER_TX_STATUS, ReactiveTransactionStatus.class).orElse(null);
            if (connection != null) {
                try {
                    return Flux.from(entityOperation.doInTransaction(connection));
                } catch (Exception e) {
                    return Flux.error(e);
                }
            }
            return withNewOrExistingTxAttributeFlux(operation, entityOperation, isWrite);
        }

        private <T, R> Mono<R> withNewOrExistingTransactionMono(
            @NonNull PreparedQuery<T, R> operation,
            boolean isWrite,
            TransactionalCallback<Connection, R> entityOperation) {
            @SuppressWarnings("unchecked")
            ReactiveTransactionStatus<Connection> connection = operation
                .getParameterInRole(R2dbcRepository.PARAMETER_TX_STATUS, ReactiveTransactionStatus.class).orElse(null);
            if (connection != null) {
                try {
                    return Mono.fromDirect(entityOperation.doInTransaction(connection));
                } catch (Exception e) {
                    return Mono.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                }
            }
            return withNewOrExistingTxAttributeMono(operation, entityOperation, isWrite);
        }

        private <T, R> Flux<R> withNewOrExistingTransactionFlux(
            @NonNull PreparedQuery<T, R> operation,
            boolean isWrite,
            TransactionalCallback<Connection, R> entityOperation) {
            @SuppressWarnings("unchecked")
            ReactiveTransactionStatus<Connection> connection = operation
                .getParameterInRole(R2dbcRepository.PARAMETER_TX_STATUS, ReactiveTransactionStatus.class).orElse(null);
            if (connection != null) {
                try {
                    return Flux.from(entityOperation.doInTransaction(connection));
                } catch (Exception e) {
                    return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                }
            }
            return withNewOrExistingTxAttributeFlux(operation, entityOperation, isWrite);
        }

        private <R> Flux<R> withNewOrExistingTxAttributeFlux(
            @NonNull AttributeHolder attributeHolder,
            TransactionalCallback<Connection, R> entityOperation,
            boolean isWrite) {
            TransactionDefinition definition = newTransactionDefinition(attributeHolder);
            if (isWrite && definition.isReadOnly()) {
                return Flux.error(new TransactionUsageException("Cannot perform write operation with read-only transaction"));
            }
            return withTransaction(definition, entityOperation);
        }

        private <R> Mono<R> withNewOrExistingTxAttributeMono(
            @NonNull AttributeHolder attributeHolder,
            TransactionalCallback<Connection, R> entityOperation,
            boolean isWrite) {
            TransactionDefinition definition = newTransactionDefinition(attributeHolder);
            if (isWrite && definition.isReadOnly()) {
                return Mono.error(new TransactionUsageException("Cannot perform write operation with read-only transaction"));
            }
            return withTransaction(definition, entityOperation).as(DefaultR2dbcRepositoryOperations::toSingleResult);
        }

        @NonNull
        @Override
        public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
            return withNewOrExistingTransactionMono(operation, true, status -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                RuntimePersistentEntity<T> persistentEntity = storedQuery.getPersistentEntity();
                final R2dbcOperationContext ctx = createContext(operation, status, storedQuery);
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
            return withNewOrExistingTransactionFlux(operation, true, status -> {
                final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
                final R2dbcOperationContext ctx = createContext(operation, status, storedQuery);
                final RuntimePersistentEntity<T> persistentEntity = storedQuery.getPersistentEntity();
                if (!isSupportsBatchUpdate(persistentEntity, storedQuery.getDialect())) {
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

        private <T> R2dbcOperationContext createContext(EntityOperation<T> operation, ReactiveTransactionStatus<Connection> status, SqlStoredQuery<T, ?> storedQuery) {
            return new R2dbcOperationContext(operation.getAnnotationMetadata(), operation.getInvocationContext(), operation.getRepositoryType(), storedQuery.getDialect(), status.getConnection());
        }

        @NonNull
        @Override
        public <T> Mono<T> findOptional(@NonNull Class<T> type, @NonNull Serializable id) {
            throw new UnsupportedOperationException("The findOptional method by ID is not supported. Execute the SQL query directly");
        }

        @NonNull
        @Override
        public <R> Mono<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery) {
            throw new UnsupportedOperationException("The findPage method is not supported. Execute the SQL query directly");
        }

        @NonNull
        @Override
        public <T> Mono<T> findOne(@NonNull Class<T> type, @NonNull Serializable id) {
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
        private final Dialect dialect;
        private int index = 0;

        private R2dbcParameterBinder(R2dbcOperationContext ctx, Statement ps) {
            this(ctx.connection, ps, ctx.dialect);
        }

        private R2dbcParameterBinder(Connection connection, Statement ps, Dialect dialect) {
            this.connection = connection;
            this.ps = ps;
            this.dialect = dialect;
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
            setStatementParameter(ps, index, binding.getDataType(), value, dialect);
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
            if (storedQuery instanceof SqlPreparedQuery) {
                data = data.map(d -> {
                    if (d.vetoed) {
                        return d;
                    }
                    ((SqlPreparedQuery) storedQuery).prepare(d.entity);
                    return d;
                });
            }
            LOG.debug(storedQuery.getQuery());
            Statement statement = connection.createStatement(storedQuery.getQuery());
            if (hasGeneratedId) {
                return statement.returnGeneratedValues(persistentEntity.getIdentity().getPersistedName());
            }
            return statement;
        }

        private void setParameters(Statement stmt, SqlStoredQuery<T, ?> storedQuery) {
            data = data.map(d -> {
                if (d.vetoed) {
                    return d;
                }
                storedQuery.bindParameters(new R2dbcParameterBinder(ctx, stmt), ctx.invocationContext, d.entity, d.previousValues);
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
                    return executeAndMapEachRowSingle(statement, row -> columnIndexResultSetReader.readDynamic(row, 0, identity.getDataType()))
                        .map(id -> {
                            BeanProperty<T, Object> property = identity.getProperty();
                            d.entity = updateEntityId(property, d.entity, id);
                            return d;
                        });
                });
            } else {
                data = data.flatMap(d -> {
                    if (d.vetoed) {
                        return Mono.just(d);
                    }
                    return executeAndGetRowsUpdatedSingle(statement).map(rowsUpdated -> {
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
                    storedQuery.bindParameters(new R2dbcParameterBinder(ctx, stmt), ctx.invocationContext, d.entity, d.previousValues);
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
                statement = ctx.connection.createStatement(storedQuery.getQuery())
                    .returnGeneratedValues(persistentEntity.getIdentity().getPersistedName());
            } else {
                statement = ctx.connection.createStatement(storedQuery.getQuery());
            }
            setParameters(statement, storedQuery);
            if (hasGeneratedId) {
                entities = entities
                    .flatMap(list -> {
                        List<Data> notVetoedEntities = list.stream().filter(this::notVetoed).collect(Collectors.toList());
                        if (notVetoedEntities.isEmpty()) {
                            return Mono.just(notVetoedEntities);
                        }
                        Mono<List<Object>> ids = executeAndMapEachRow(statement, row
                            -> columnIndexResultSetReader.readDynamic(row, 0, persistentEntity.getIdentity().getDataType())
                        ).collectList();

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
                        List<Data> notVetoedEntities = list.stream().filter(this::notVetoed).collect(Collectors.toList());
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
         * The old deprecated constructor.
         *
         * @param annotationMetadata the annotation metadata
         * @param repositoryType the repository type
         * @param dialect the dialect
         * @param connection the connection
         * @deprecated Use constructor with {@link InvocationContext}.
         */
        @Deprecated
        public R2dbcOperationContext(AnnotationMetadata annotationMetadata, Class<?> repositoryType, Dialect dialect, Connection connection) {
            this(annotationMetadata, null, repositoryType, dialect, connection);
        }

        /**
         * The default constructor.
         *
         * @param annotationMetadata the annotation metadata
         * @param invocationContext the invocation context
         * @param repositoryType the repository type
         * @param dialect the dialect
         * @param connection the connection
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
