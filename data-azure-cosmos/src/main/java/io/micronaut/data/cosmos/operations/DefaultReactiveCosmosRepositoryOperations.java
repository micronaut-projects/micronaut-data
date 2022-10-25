/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.cosmos.operations;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.RequestOptions;
import com.azure.cosmos.implementation.batch.ItemBulkOperation;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedFlux;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.cosmos.common.Constants;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.cosmos.config.RequiresReactiveCosmos;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.EntityInstanceOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntitiesOperations;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.serde.SerdeRegistry;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The reactive Cosmos DB repository operations implementation.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Singleton
@Requires(bean = CosmosAsyncClient.class)
@Internal
@RequiresReactiveCosmos
public final class DefaultReactiveCosmosRepositoryOperations extends AbstractCosmosOperations implements
    ReactorReactiveRepositoryOperations, ReactiveRepositoryOperations {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveCosmosRepositoryOperations.class);

    private final CosmosAsyncDatabase cosmosAsyncDatabase;

    /**
     * Default constructor.
     *
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     * @param cosmosAsyncClient          The Cosmos async client
     * @param serdeRegistry              The (de)serialization registry
     * @param objectMapper               The object mapper used for the data (de)serialization
     * @param configuration              The Cosmos database configuration
     */
    public DefaultReactiveCosmosRepositoryOperations(List<MediaTypeCodec> codecs,
                                                     DateTimeProvider<Object> dateTimeProvider,
                                                     RuntimeEntityRegistry runtimeEntityRegistry,
                                                     DataConversionService<?> conversionService,
                                                     AttributeConverterRegistry attributeConverterRegistry,
                                                     CosmosAsyncClient cosmosAsyncClient,
                                                     SerdeRegistry serdeRegistry,
                                                     ObjectMapper objectMapper,
                                                     CosmosDatabaseConfiguration configuration) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry, serdeRegistry, objectMapper, configuration);
        this.cosmosAsyncDatabase = cosmosAsyncClient.getDatabase(configuration.getDatabaseName());
    }

    @Override
    @NonNull
    public <T> Mono<T> findOne(@NonNull Class<T> type, Serializable id) {
        try {
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
            CosmosAsyncContainer container = getContainer(persistentEntity);
            final SqlParameter param = new SqlParameter("@ROOT_ID", id.toString());
            final SqlQuerySpec querySpec = new SqlQuerySpec(FIND_ONE_DEFAULT_QUERY, param);
            logQuery(querySpec, Collections.singletonList(param));
            final CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            if (isIdPartitionKey(persistentEntity)) {
                options.setPartitionKey(new PartitionKey(id.toString()));
            }
            CosmosPagedFlux<ObjectNode> result = container.queryItems(querySpec, options, ObjectNode.class);
            return result.byPage().publishOn(Schedulers.parallel()).flatMap(fluxResponse -> {
                Iterator<ObjectNode> iterator = fluxResponse.getResults().iterator();
                if (iterator.hasNext()) {
                    ObjectNode item = iterator.next();
                    if (iterator.hasNext()) {
                        return Flux.error(new NonUniqueResultException());
                    }
                    return Mono.just(deserialize(item, Argument.of(type)));
                }
                return Flux.empty();
            }).next();
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpResponseStatus.NOT_FOUND.code()) {
                return Mono.empty();
            }
            return Mono.error(e);
        }
    }

    /**
     * Gets cosmos reactive results for given prepared query.
     *
     * @param preparedQuery the prepared query
     * @param parameterList the Cosmos Sql parameter list
     * @param itemsType the result iterator items type
     * @param <T> The query entity type
     * @param <R> The query result type
     * @param <I> the Cosmos iterator items type
     * @return CosmosPagedFlux with values of I type
     */
    private <T, R, I> CosmosPagedFlux<I> getCosmosResults(PreparedQuery<T, R> preparedQuery, List<SqlParameter> parameterList, Class<I> itemsType) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        CosmosAsyncContainer container = getContainer(persistentEntity);
        SqlQuerySpec querySpec = new SqlQuerySpec(preparedQuery.getQuery(), parameterList);
        logQuery(querySpec, parameterList);
        CosmosQueryRequestOptions requestOptions = new CosmosQueryRequestOptions();
        preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class).ifPresent(requestOptions::setPartitionKey);
        return container.queryItems(querySpec, requestOptions, itemsType);
    }

    @Override
    public <T> Mono<Boolean> exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        List<SqlParameter> paramList = new ParameterBinder().bindParameters(preparedQuery);
        CosmosPagedFlux<ObjectNode> result = getCosmosResults(preparedQuery, paramList, ObjectNode.class);
        return result.byPage().publishOn(Schedulers.parallel())
            .flatMap(cosmosResponse -> Mono.just(cosmosResponse.getResults().iterator().hasNext())).next();
    }

    /**
     * Finds one entity or DTO projection.
     *
     * @param preparedQuery the prepared query
     * @param paramList the Cosmos SQL parameter list
     * @param <T> The entity type
     * @param <R> The result type
     * @return entity or DTO projection
     */
    private <T, R> Mono<R> findOneEntityOrDto(PreparedQuery<T, R> preparedQuery, List<SqlParameter> paramList) {
        CosmosPagedFlux<ObjectNode> result = getCosmosResults(preparedQuery, paramList, ObjectNode.class);
        return result.byPage().publishOn(Schedulers.parallel()).flatMap(fluxResponse -> {
            Iterator<ObjectNode> iterator = fluxResponse.getResults().iterator();
            if (iterator.hasNext()) {
                ObjectNode item = iterator.next();
                if (iterator.hasNext()) {
                    return Flux.error(new NonUniqueResultException());
                }
                if (preparedQuery.isDtoProjection()) {
                    Class<R> wrapperType = ReflectionUtils.getWrapperType(preparedQuery.getResultType());
                    return Mono.just(deserialize(item, Argument.of(wrapperType)));
                }
                return Mono.just(deserialize(item, Argument.of(preparedQuery.getResultType())));
            }
            return Flux.empty();
        }).next();
    }

    /**
     * Finds query and returns as custom result type.
     *
     * @param preparedQuery the prepared query
     * @param paramList the Cosmos SQL parameter list
     * @param <T> The entity type
     * @param <R> The result type
     * @return custom result type as a result of prepared query execution
     */
    private <T, R> Mono<R> findOneCustomResult(PreparedQuery<T, R> preparedQuery, List<SqlParameter> paramList) {
        DataType dataType = preparedQuery.getResultDataType();
        CosmosPagedFlux<?> result = getCosmosResults(preparedQuery, paramList, getDataTypeClass(dataType));
        return result.byPage().publishOn(Schedulers.parallel()).flatMap(fluxResponse -> {
            Iterator<?> iterator = fluxResponse.getResults().iterator();
            if (iterator.hasNext()) {
                Object item = iterator.next();
                if (iterator.hasNext()) {
                    return Flux.error(new NonUniqueResultException());
                }
                Class<R> resultType = preparedQuery.getResultType();
                if (resultType.isInstance(item)) {
                    return Mono.just((R) item);
                } else if (item != null) {
                    return Mono.just(ConversionService.SHARED.convertRequired(item, resultType));
                }
            }
            return Flux.empty();
        }).next();
    }

    @Override
    @NonNull
    public <T, R> Mono<R> findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        try {
            List<SqlParameter> paramList = new DefaultCosmosRepositoryOperations.ParameterBinder().bindParameters(preparedQuery);
            boolean dtoProjection = preparedQuery.isDtoProjection();
            boolean isEntity = preparedQuery.getResultDataType() == DataType.ENTITY;
            if (isEntity || dtoProjection) {
                return findOneEntityOrDto(preparedQuery, paramList);
            } else {
                return findOneCustomResult(preparedQuery, paramList);
            }
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpResponseStatus.NOT_FOUND.code()) {
                return Mono.empty();
            }
            return Mono.error(e);
        }
    }

    @Override
    @NonNull
    public <T> Mono<T> findOptional(@NonNull Class<T> type, @NonNull Serializable id) {
        return findOne(type, id).onErrorReturn(EmptyResultException.class, (T) Mono.empty());
    }

    @Override
    @NonNull
    public <T, R> Mono<R> findOptional(@NonNull PreparedQuery<T, R> preparedQuery) {
        return findOne(preparedQuery).onErrorReturn(EmptyResultException.class, (R) Mono.empty());
    }

    @Override
    @NonNull
    public <T> Flux<T> findAll(PagedQuery<T> pagedQuery) {
        throw new UnsupportedOperationException("The findAll method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
    }

    @Override
    @NonNull
    public <T> Mono<Long> count(PagedQuery<T> pagedQuery) {
        throw new UnsupportedOperationException("The count method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
    }

    @Override
    @NonNull
    public <T, R> Flux<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        try {
            boolean dtoProjection = preparedQuery.isDtoProjection();
            boolean isEntity = preparedQuery.getResultDataType() == DataType.ENTITY;
            List<SqlParameter> paramList = new ParameterBinder().bindParameters(preparedQuery);
            if (isEntity || dtoProjection) {
                Argument<R> argument;
                if (dtoProjection) {
                    argument = Argument.of(ReflectionUtils.getWrapperType(preparedQuery.getResultType()));
                } else {
                    argument = Argument.of(preparedQuery.getResultType());
                }
                CosmosPagedFlux<ObjectNode> result = getCosmosResults(preparedQuery, paramList, ObjectNode.class);
                return result.map(item -> deserialize(item, argument));
            } else {
                DataType dataType = preparedQuery.getResultDataType();
                Class<R> resultType = preparedQuery.getResultType();
                CosmosPagedFlux<?> result = getCosmosResults(preparedQuery, paramList, getDataTypeClass(dataType));
                return result.mapNotNull(item -> {
                    if (resultType.isInstance(item)) {
                        return (R) item;
                    } else if (item != null) {
                        return ConversionService.SHARED.convertRequired(item, resultType);
                    }
                    return null;
                });
            }
        } catch (Exception e) {
            return Flux.error(new DataAccessException("Cosmos SQL Error executing Query: " + e.getMessage(), e));
        }
    }

    @Override
    @NonNull
    public <T> Mono<T> persist(@NonNull InsertOperation<T> operation) {
        CosmosAsyncContainer container = getContainer(operation);
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosReactiveOperationContext<T> ctx = new CosmosReactiveOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);
        CosmosReactiveEntityOperation<T> op = createCosmosInsertOneOperation(ctx, persistentEntity, operation.getEntity());
        op.persist();
        return op.getEntity();
    }

    @Override
    @NonNull
    public <T> Mono<T> update(@NonNull UpdateOperation<T> operation) {
        CosmosAsyncContainer container = getContainer(operation);
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosReactiveOperationContext<T> ctx = new CosmosReactiveOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);
        CosmosReactiveEntityOperation<T> op = createCosmosReactiveReplaceItemOperation(ctx, persistentEntity, operation.getEntity());
        op.update();
        return op.getEntity();
    }

    @Override
    @NonNull
    public  <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosAsyncContainer container = getContainer(persistentEntity);
        CosmosReactiveOperationContext<T> ctx = new CosmosReactiveOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);
        CosmosReactiveEntitiesOperation<T> op = createCosmosReactiveBulkOperation(ctx, persistentEntity, operation, BulkOperationType.UPDATE);
        op.update();
        return op.getEntities();
    }

    @Override
    @NonNull
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        Flux<T> result = Flux.empty();
        for (InsertOperation<T> insertOperation : operation.split()) {
            result = result.concatWith(persist(insertOperation));
        }
        return result;
    }

    @Override
    @NonNull
    public Mono<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        if (isRawQuery(preparedQuery)) {
            return Mono.error(new IllegalStateException("Cosmos Db does not support raw update queries."));
        }
        RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        String update = preparedQuery.getAnnotationMetadata().stringValue(Query.class, "update").orElse(null);
        if (update == null) {
            LOG.warn("Could not resolve update properties for Cosmos Db entity {} and query [{}]", persistentEntity.getName(), preparedQuery.getQuery());
            return Mono.just(0);
        }
        List<String> updatePropertyList = Arrays.asList(update.split(","));
        ParameterBinder parameterBinder = new ParameterBinder(true, updatePropertyList);
        List<SqlParameter> parameterList = parameterBinder.bindParameters(preparedQuery);
        Map<String, Object> propertiesToUpdate = parameterBinder.getPropertiesToUpdate();
        if (propertiesToUpdate.isEmpty()) {
            LOG.warn("No properties found to be updated for Cosmos Db entity {} and query [{}]", persistentEntity.getName(), preparedQuery.getQuery());
            return Mono.just(0);
        }
        CosmosAsyncContainer container = getContainer(persistentEntity);
        Optional<PartitionKey> optPartitionKey = preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class);
        CosmosPagedFlux<ObjectNode> items = getCosmosResults(preparedQuery, parameterList, ObjectNode.class);
        return executeBulk(container, items, BulkOperationType.UPDATE, persistentEntity, optPartitionKey, item -> updateProperties(item, propertiesToUpdate));
    }

    @Override
    @NonNull
    public Mono<Number> executeDelete(@NonNull PreparedQuery<?, Number> preparedQuery) {
        if (isRawQuery(preparedQuery)) {
            return Mono.error(new IllegalStateException("Cosmos Db does not support raw delete queries."));
        }
        RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        CosmosAsyncContainer container = getContainer(persistentEntity);
        Optional<PartitionKey> optPartitionKey = preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class);
        List<SqlParameter> parameterList = new ParameterBinder().bindParameters(preparedQuery);
        CosmosPagedFlux<ObjectNode> items = getCosmosResults(preparedQuery, parameterList, ObjectNode.class);

        return executeBulk(container, items, BulkOperationType.DELETE, persistentEntity, optPartitionKey, null);
    }

    @Override
    @NonNull
    public <T> Mono<Number> delete(DeleteOperation<T> operation) {
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosAsyncContainer container = getContainer(operation);
        CosmosReactiveOperationContext<T> ctx = new CosmosReactiveOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);
        CosmosReactiveEntityOperation<T> op = createCosmosReactiveDeleteOneOperation(ctx, persistentEntity, operation.getEntity());
        op.delete();
        return op.affectedCount;
    }

    @Override
    @NonNull
    public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosAsyncContainer container = getContainer(persistentEntity);
        CosmosReactiveOperationContext<T> ctx = new CosmosReactiveOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);
        CosmosReactiveEntitiesOperation<T> op = createCosmosReactiveBulkOperation(ctx, persistentEntity, operation, BulkOperationType.DELETE);
        op.update();
        return op.getRowsUpdated();
    }

    @Override
    @NonNull
    public <R> Mono<Page<R>> findPage(@NonNull PagedQuery<R> pagedQuery) {
        throw new UnsupportedOperationException("Not supported");
    }

    /**
     * Gets the async container for given persistent entity. It is expected that at this point container is created.
     *
     * @param persistentEntity the persistent entity (to be persisted in container)
     * @return the Cosmos async container
     */
    private CosmosAsyncContainer getContainer(RuntimePersistentEntity<?> persistentEntity) {
        return cosmosAsyncDatabase.getContainer(persistentEntity.getPersistedName());
    }

    private <T> CosmosAsyncContainer getContainer(EntityInstanceOperation<T> operation) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return getContainer(persistentEntity);
    }

    // Create, update, delete operations with entities

    /**
     * Executes bulk operation (update or delete) for given iterable of {@link ObjectNode}.
     *
     * @param container the container where documents are being updated or deleted
     * @param items the items being updated or deleted
     * @param bulkOperationType the bulk operation type (DELETE or UPDATE)
     * @param persistentEntity the persistent entity corresponding to the items
     * @param optPartitionKey {@link Optional} with {@link PartitionKey} as value, if empty then will obtain partition key from each item
     * @param handleItem function that will apply some changes before adding item to the list, if null then ignored
     * @return number of affected items
     */
    private Mono<Number> executeBulk(CosmosAsyncContainer container, CosmosPagedFlux<ObjectNode> items, BulkOperationType bulkOperationType, RuntimePersistentEntity<?> persistentEntity, Optional<PartitionKey> optPartitionKey,
                                     UnaryOperator<ObjectNode> handleItem) {

        // Update/replace using provided partition key or partition key calculated from each item
        Flux<CosmosItemOperation> updateItems = items.byPage().publishOn(Schedulers.parallel()).flatMap(itemsMap -> {
            List<CosmosItemOperation> bulkOperations = createBulkOperations(itemsMap.getResults(), bulkOperationType, persistentEntity, optPartitionKey, handleItem);
            return Flux.fromIterable(bulkOperations);
        });
        return container.executeBulkOperations(updateItems).reduce(0, (affectedCount, response) -> {
            if (response.getResponse().getStatusCode() == bulkOperationType.expectedOperationStatusCode) {
                affectedCount = (int) affectedCount + 1;
            }
            return affectedCount;
        });
    }

    private <T> CosmosReactiveEntityOperation<T> createCosmosInsertOneOperation(CosmosReactiveOperationContext<T> ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new CosmosReactiveEntityOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, entity, true) {

            @Override
            protected void execute() throws RuntimeException {
                CosmosAsyncContainer container = ctx.getContainer();
                data = data.flatMap(d -> {
                    if (hasGeneratedId) {
                        RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                        if (identity.getProperty().get(d.entity) == null && identity.getDataType().equals(DataType.STRING)) {
                            identity.getProperty().convertAndSet(d.entity, UUID.randomUUID().toString());
                        }
                    }
                    ObjectNode item = serialize(d.entity, Argument.of(ctx.getRootEntity()));
                    return Mono.from(container.createItem(item, new CosmosItemRequestOptions())).map(insertOneResult -> d);
                });
            }
        };
    }

    private <T> CosmosReactiveEntityOperation<T> createCosmosReactiveReplaceItemOperation(CosmosReactiveOperationContext<T> ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new CosmosReactiveEntityOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, entity, false) {

            @Override
            protected void execute() throws RuntimeException {
                CosmosAsyncContainer container = ctx.getContainer();
                ObjectNode item = serialize(entity, Argument.of(ctx.getRootEntity()));
                PartitionKey partitionKey = getPartitionKey(persistentEntity, item);
                String id = getItemId(item);
                CosmosItemResponse<?> response = container.replaceItem(item, id, partitionKey, new CosmosItemRequestOptions()).block();
                if (response != null && response.getStatusCode() != HttpResponseStatus.OK.code()) {
                    LOG.debug("Failed to update entity with id {} in container {}", id, container.getId());
                }
            }

        };
    }

    private <T> CosmosReactiveEntityOperation<T> createCosmosReactiveDeleteOneOperation(CosmosReactiveOperationContext<T> ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new CosmosReactiveEntityOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, entity, false) {

            @Override
            protected void execute() throws RuntimeException {
                CosmosAsyncContainer container = ctx.getContainer();
                ObjectNode item = serialize(entity, Argument.of(ctx.getRootEntity()));
                CosmosItemRequestOptions options = new CosmosItemRequestOptions();
                String id = getItemId(item);
                PartitionKey partitionKey = getPartitionKey(persistentEntity, item);
                CosmosItemResponse<Object> cosmosItemResponse = container.deleteItem(id, partitionKey, options).block();
                if (cosmosItemResponse != null && cosmosItemResponse.getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
                    affectedCount = Mono.just(1);
                } else {
                    affectedCount = Mono.just(0);
                }
            }
        };
    }

    private <T> CosmosReactiveEntitiesOperation<T> createCosmosReactiveBulkOperation(CosmosReactiveOperationContext<T> ctx,
                                                                                                       RuntimePersistentEntity<T> persistentEntity,
                                                                                                       BatchOperation<T> operation,
                                                                                                       BulkOperationType operationType) {
        return new CosmosReactiveEntitiesOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, operation) {

            @Override
            protected void execute() throws RuntimeException {
                Argument<T> arg = Argument.of(ctx.getRootEntity());
                RequestOptions requestOptions = new RequestOptions();

                // Update/replace using provided partition key or partition key calculated from each item
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.collectList()
                    .flatMap(e -> {
                        List<ItemBulkOperation<?, ?>> notVetoedEntities = e.stream().filter(this::notVetoed).map(x -> {
                            ObjectNode item = serialize(x.entity, arg);
                            String id = getItemId(item);
                            PartitionKey partitionKey = getPartitionKey(persistentEntity, item);
                            return new ItemBulkOperation<>(operationType.cosmosItemOperationType, id, partitionKey, requestOptions, item, null);
                        }).collect(Collectors.toList());
                        if (notVetoedEntities.isEmpty()) {
                            return Mono.just(Tuples.of(e, 0L));
                        }
                        return executeAndGetRowsUpdated(notVetoedEntities)
                            .map(Number::longValue)
                            .map(rowsUpdated -> Tuples.of(e, rowsUpdated));
                    }).cache();
                entities = entitiesWithRowsUpdated.flatMapMany(t -> Flux.fromIterable(t.getT1()));
                rowsUpdated = entitiesWithRowsUpdated.map(Tuple2::getT2);
            }

            private Mono<Number> executeAndGetRowsUpdated(List<ItemBulkOperation<?, ?>> bulkOperations) {
                return ctx.getContainer().executeBulkOperations(Flux.fromIterable(bulkOperations)).reduce(0, (count, response) -> {
                    if (response.getResponse().getStatusCode() == operationType.expectedOperationStatusCode) {
                        count = (int) count + 1;
                    }
                    return count;
                });
            }
        };
    }

    /**
     * The Cosmos Db reactive operation context.
     *
     * @param <T> the entity type
     */
    private static class CosmosReactiveOperationContext<T> extends OperationContext {

        private final CosmosAsyncContainer container;
        private final Class<T> rootEntity;

        public CosmosReactiveOperationContext(AnnotationMetadata annotationMetadata, Class<?> repositoryType, CosmosAsyncContainer container, Class<T> rootEntity) {
            super(annotationMetadata, repositoryType);
            this.container = container;
            this.rootEntity = rootEntity;
        }

        /**
         * @return gets the container in which operation is executing
         */
        public CosmosAsyncContainer getContainer() {
            return container;
        }

        /**
         * @return the root entity class
         */
        public Class<T> getRootEntity() {
            return rootEntity;
        }
    }

    /**
     * Base class for Cosmos reactive entity operation (insert, update and delete).
     *
     * @param <T> the entity type
     */
    private abstract static class CosmosReactiveEntityOperation<T> extends AbstractReactiveEntityOperations<CosmosReactiveOperationContext<T>, T, RuntimeException> {

        protected Mono<Number> affectedCount;

        /**
         * Default constructor.
         *
         * @param entityEventListener The entity event listener
         * @param conversionService   The conversion service
         * @param ctx                 The context
         * @param persistentEntity    The persistent entity
         * @param entity              The entity
         * @param insert              The insert
         */
        protected CosmosReactiveEntityOperation(EntityEventListener<Object> entityEventListener,
                                                ConversionService<?> conversionService,
                                                CosmosReactiveOperationContext<T> ctx,
                                                RuntimePersistentEntity<T> persistentEntity,
                                                T entity,
                                                boolean insert) {
            super(ctx, null, conversionService, entityEventListener, persistentEntity, entity, insert);
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
        }
    }

    /**
     * Base class for Cosmos reactive multiple entities operation.
     *
     * @param <T> the entity type
     */
    private abstract static class CosmosReactiveEntitiesOperation<T> extends AbstractReactiveEntitiesOperations<CosmosReactiveOperationContext<T>, T, RuntimeException> {

        /**
         * Default constructor.
         *
         * @param entityEventListener The entity event listener
         * @param conversionService   The conversion service
         * @param ctx                 The context
         * @param persistentEntity    The persistent entity
         * @param entities            The entities
         */
        protected CosmosReactiveEntitiesOperation(EntityEventListener<Object> entityEventListener,
                                                  ConversionService<?> conversionService,
                                                  CosmosReactiveOperationContext<T> ctx,
                                                  RuntimePersistentEntity<T> persistentEntity,
                                                  Iterable<T> entities) {
            super(ctx, null, conversionService, entityEventListener, persistentEntity, entities, false);
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
        }
    }
}
