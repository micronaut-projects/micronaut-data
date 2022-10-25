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

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosBulkOperationResponse;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.cosmos.common.Constants;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.cosmos.config.RequiresSyncCosmos;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.EntityInstanceOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntitiesOperations;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.serde.SerdeRegistry;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The default Azure Cosmos DB operations implementation.
 */
@Singleton
@Requires(bean = CosmosClient.class)
@Internal
@RequiresSyncCosmos
final class DefaultCosmosRepositoryOperations extends AbstractCosmosOperations implements
    CosmosRepositoryOperations,
    AsyncCapableRepository,
    ReactiveCapableRepository {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCosmosRepositoryOperations.class);

    private final CosmosDatabase cosmosDatabase;
    private ExecutorService executorService;
    private ExecutorAsyncOperations asyncOperations;

    /**
     * Default constructor.
     *
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     * @param cosmosClient               The Cosmos client
     * @param serdeRegistry              The (de)serialization registry
     * @param objectMapper               The object mapper used for the data (de)serialization
     * @param configuration              The Cosmos database configuration
     * @param executorService            The executor service
     */
    protected DefaultCosmosRepositoryOperations(List<MediaTypeCodec> codecs,
                                                DateTimeProvider<Object> dateTimeProvider,
                                                RuntimeEntityRegistry runtimeEntityRegistry,
                                                DataConversionService<?> conversionService,
                                                AttributeConverterRegistry attributeConverterRegistry,
                                                CosmosClient cosmosClient,
                                                SerdeRegistry serdeRegistry,
                                                ObjectMapper objectMapper,
                                                CosmosDatabaseConfiguration configuration,
                                                @Named("io") @Nullable ExecutorService executorService) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry, serdeRegistry, objectMapper, configuration);
        this.cosmosDatabase = cosmosClient.getDatabase(configuration.getDatabaseName());
        this.executorService = executorService;
    }

    @Override
    public <T> T findOne(@NonNull Class<T> type, Serializable id) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
        CosmosContainer container = getContainer(persistentEntity);
        try {
            final SqlParameter param = new SqlParameter("@ROOT_ID", id.toString());
            final SqlQuerySpec querySpec = new SqlQuerySpec(FIND_ONE_DEFAULT_QUERY, param);
            logQuery(querySpec, Collections.singletonList(param));
            final CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
            if (isIdPartitionKey(persistentEntity)) {
                options.setPartitionKey(new PartitionKey(id.toString()));
            }
            CosmosPagedIterable<ObjectNode> result = container.queryItems(querySpec, options, ObjectNode.class);
            Iterator<ObjectNode> iterator = result.iterator();
            if (iterator.hasNext()) {
                ObjectNode item = iterator.next();
                if (iterator.hasNext()) {
                    throw new NonUniqueResultException();
                }
                return deserialize(item, Argument.of(type));
            }
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpResponseStatus.NOT_FOUND.code()) {
                return null;
            }
            throw e;
        }
        return null;
    }

    /**
     * Gets cosmos results for given prepared query.
     *
     * @param preparedQuery the prepared query
     * @param parameterList the Cosmos Sql parameter list
     * @param itemsType the result iterator items type
     * @param <T> The query entity type
     * @param <R> The query result type
     * @param <I> the Cosmos iterator items type
     * @return CosmosPagedIterable with values of I type
     */
    private <T, R, I> CosmosPagedIterable<I> getCosmosResults(PreparedQuery<T, R> preparedQuery, List<SqlParameter> parameterList, Class<I> itemsType) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        CosmosContainer container = getContainer(persistentEntity);
        SqlQuerySpec querySpec = new SqlQuerySpec(preparedQuery.getQuery(), parameterList);
        logQuery(querySpec, parameterList);
        CosmosQueryRequestOptions requestOptions = new CosmosQueryRequestOptions();
        preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class).ifPresent(requestOptions::setPartitionKey);
        return container.queryItems(querySpec, requestOptions, itemsType);
    }

    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        try {
            List<SqlParameter> paramList = new ParameterBinder().bindParameters(preparedQuery);
            boolean dtoProjection = preparedQuery.isDtoProjection();
            boolean isEntity = preparedQuery.getResultDataType() == DataType.ENTITY;
            if (isEntity || dtoProjection) {
                CosmosPagedIterable<ObjectNode> result = getCosmosResults(preparedQuery, paramList, ObjectNode.class);
                Iterator<ObjectNode> iterator = result.iterator();
                if (iterator.hasNext()) {
                    ObjectNode item = iterator.next();
                    if (iterator.hasNext()) {
                        throw new NonUniqueResultException();
                    }
                    if (preparedQuery.isDtoProjection()) {
                        Class<R> wrapperType = ReflectionUtils.getWrapperType(preparedQuery.getResultType());
                        return deserialize(item, Argument.of(wrapperType));
                    }
                    return deserialize(item, Argument.of(preparedQuery.getResultType()));
                }
            } else {
                DataType dataType = preparedQuery.getResultDataType();
                CosmosPagedIterable<?> result = getCosmosResults(preparedQuery, paramList, getDataTypeClass(dataType));
                Iterator<?> iterator = result.iterator();
                if (iterator.hasNext()) {
                    Object item = iterator.next();
                    if (iterator.hasNext()) {
                        throw new NonUniqueResultException();
                    }
                    Class<R> resultType = preparedQuery.getResultType();
                    if (resultType.isInstance(item)) {
                        return (R) item;
                    } else if (item != null) {
                        return ConversionService.SHARED.convertRequired(item, resultType);
                    }
                    return null;
                }

            }
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpResponseStatus.NOT_FOUND.code()) {
                return null;
            }
            throw e;
        }
        return null;
    }

    @Override
    public <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        List<SqlParameter> paramList = new ParameterBinder().bindParameters(preparedQuery);
        CosmosPagedIterable<ObjectNode> result = getCosmosResults(preparedQuery, paramList, ObjectNode.class);
        Iterator<ObjectNode> iterator = result.iterator();
        return iterator.hasNext();
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(@NonNull PagedQuery<T> query) {
        throw new UnsupportedOperationException("The findAll method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        throw new UnsupportedOperationException("The count method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        try (Stream<R> stream = findStream(preparedQuery)) {
            return stream.collect(Collectors.toList());
        }
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        AtomicBoolean finished = new AtomicBoolean();
        try {
            Spliterator<R> spliterator;
            boolean dtoProjection = preparedQuery.isDtoProjection();
            boolean isEntity = preparedQuery.getResultDataType() == DataType.ENTITY;
            List<SqlParameter> paramList = new ParameterBinder().bindParameters(preparedQuery);
            if (isEntity || dtoProjection) {
                CosmosPagedIterable<ObjectNode> result = getCosmosResults(preparedQuery, paramList, ObjectNode.class);
                Iterator<ObjectNode> iterator = result.iterator();
                Argument<R> argument;
                if (dtoProjection) {
                    argument = Argument.of(ReflectionUtils.getWrapperType(preparedQuery.getResultType()));
                } else {
                    argument = Argument.of(preparedQuery.getResultType());
                }
                spliterator = new EntityOrDtoSpliterator<>(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.IMMUTABLE,
                    finished, iterator, argument);
            } else {
                DataType dataType = preparedQuery.getResultDataType();
                CosmosPagedIterable<?> result = getCosmosResults(preparedQuery, paramList, getDataTypeClass(dataType));
                Iterator<?> iterator = result.iterator();
                Class<R> resultType = preparedQuery.getResultType();
                spliterator = new CustomResultTypeSpliterator<>(Long.MAX_VALUE,
                    Spliterator.ORDERED | Spliterator.IMMUTABLE, finished, iterator, resultType);
            }
            return StreamSupport.stream(spliterator, false).onClose(() -> finished.set(true));
        } catch (Exception e) {
            throw new DataAccessException("Cosmos SQL Error executing Query: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(@NonNull PagedQuery<T> query) {
        throw new UnsupportedOperationException("The findStream method without an explicit query is not supported. Use findStream(PreparedQuery) instead");
    }

    @Override
    public <R> Page<R> findPage(@NonNull PagedQuery<R> query) {
        throw new UnsupportedOperationException("The findPage method without an explicit query is not supported. Use findPage(PreparedQuery) instead");
    }

    @NonNull
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        CosmosContainer container = getContainer(operation);
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosOperationContext<T> ctx = new CosmosOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);
        CosmosEntityOperation<T> op = createCosmosInsertOneOperation(ctx, persistentEntity, operation.getEntity());
        op.persist();
        return op.getEntity();
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        CosmosContainer container = getContainer(operation);
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosOperationContext<T> ctx = new CosmosOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);
        CosmosEntityOperation<T> op = createCosmosReplaceItemOperation(ctx, persistentEntity, operation.getEntity());
        op.update();
        return op.getEntity();

    }

    /**
     * Serializes items from {@link BatchOperation} entities to list of {@link ObjectNode} for update/delete batch operations.
     *
     * @param entities the entities for batch operation (update/delete)
     * @param rootEntity the root entity type
     * @param <T> the entity type
     * @return list of {@link ObjectNode} serialized from entities
     */
    private <T> List<ObjectNode> serializeBatchItems(Iterable<T> entities, Class<T> rootEntity) {
        Argument<T> arg = Argument.of(rootEntity);
        List<ObjectNode> items = new ArrayList<>();
        for (T entity : entities) {
            items.add(serialize(entity, arg));
        }
        return items;
    }

    @NonNull
    @Override
    public <T> Iterable<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosContainer container = getContainer(persistentEntity);
        CosmosOperationContext<T> ctx = new CosmosOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);

        CosmosEntitiesOperation<T> op = createCosmosBulkOperation(ctx, persistentEntity, operation, BulkOperationType.UPDATE);
        op.update();
        return op.getEntities();
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        if (isRawQuery(preparedQuery)) {
            throw new IllegalStateException("Cosmos Db does not support raw update queries.");
        }
        RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        String update = preparedQuery.getAnnotationMetadata().stringValue(Query.class, "update").orElse(null);
        if (update == null) {
            LOG.warn("Could not resolve update properties for Cosmos Db entity {} and query [{}]", persistentEntity.getName(), preparedQuery.getQuery());
            return Optional.of(0);
        }
        List<String> updatePropertyList = Arrays.asList(update.split(","));
        ParameterBinder parameterBinder = new ParameterBinder(true, updatePropertyList);
        List<SqlParameter> parameterList = parameterBinder.bindParameters(preparedQuery);
        Map<String, Object> propertiesToUpdate = parameterBinder.getPropertiesToUpdate();
        if (propertiesToUpdate.isEmpty()) {
            LOG.warn("No properties found to be updated for Cosmos Db entity {} and query [{}]", persistentEntity.getName(), preparedQuery.getQuery());
            return Optional.of(0);
        }
        CosmosContainer container = getContainer(persistentEntity);
        Optional<PartitionKey> optPartitionKey = preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class);
        CosmosPagedIterable<ObjectNode> result = getCosmosResults(preparedQuery, parameterList, ObjectNode.class);
        // Update/replace using provided partition key or partition key calculated from each item
        List<ObjectNode> items = new ArrayList<>();
        for (ObjectNode item : result) {
            items.add(updateProperties(item, propertiesToUpdate));
        }
        return Optional.of(executeBulk(container, items, BulkOperationType.UPDATE, persistentEntity, optPartitionKey));
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosContainer container = getContainer(operation);
        CosmosOperationContext<T> ctx = new CosmosOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);
        CosmosEntityOperation<T> op = createCosmosDeleteOneOperation(ctx, persistentEntity, operation.getEntity());
        op.delete();
        return op.affectedCount;
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosContainer container = getContainer(persistentEntity);
        CosmosOperationContext<T> ctx = new CosmosOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);

        CosmosEntitiesOperation<T> op = createCosmosBulkOperation(ctx, persistentEntity, operation, BulkOperationType.DELETE);
        op.update();
        return Optional.of(op.affectedCount);
    }

    @NonNull
    @Override
    public Optional<Number> executeDelete(@NonNull PreparedQuery<?, Number> preparedQuery) {
        if (isRawQuery(preparedQuery)) {
            throw new IllegalStateException("Cosmos Db does not support raw delete queries.");
        }
        RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        CosmosContainer container = getContainer(persistentEntity);
        Optional<PartitionKey> optPartitionKey = preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class);
        List<SqlParameter> parameterList = new ParameterBinder().bindParameters(preparedQuery);
        CosmosPagedIterable<ObjectNode> result = getCosmosResults(preparedQuery, parameterList, ObjectNode.class);
        int deletedCount = executeBulk(container, result, BulkOperationType.DELETE, persistentEntity, optPartitionKey);
        return Optional.of(deletedCount);
    }

    @NonNull
    @Override
    public ExecutorAsyncOperations async() {
        ExecutorAsyncOperations asyncOperations = this.asyncOperations;
        if (asyncOperations == null) {
            synchronized (this) { // double check
                asyncOperations = this.asyncOperations;
                if (asyncOperations == null) {
                    asyncOperations = new ExecutorAsyncOperations(
                        this,
                        executorService != null ? executorService : newLocalThreadPool()
                    );
                    this.asyncOperations = asyncOperations;
                }
            }
        }
        return asyncOperations;
    }

    @NonNull
    @Override
    public ReactiveRepositoryOperations reactive() {
        return new ExecutorReactiveOperations(async(), conversionService);
    }

    @NonNull
    private ExecutorService newLocalThreadPool() {
        this.executorService = Executors.newCachedThreadPool();
        return executorService;
    }

    /**
     * Executes bulk operation (update or delete) for given iterable of {@link ObjectNode}.
     *
     * @param container the container where documents are being updated or deleted
     * @param items the items being updated or deleted
     * @param bulkOperationType the bulk operation type (DELETE or UPDATE)
     * @param persistentEntity the persistent entity corresponding to the items
     * @param optPartitionKey {@link Optional} with {@link PartitionKey} as value, if empty then will obtain partition key from each item
     * @return number of affected items
     */
    private int executeBulk(CosmosContainer container, Iterable<ObjectNode> items, BulkOperationType bulkOperationType, RuntimePersistentEntity<?> persistentEntity, Optional<PartitionKey> optPartitionKey) {
        List<CosmosItemOperation> bulkOperations = createBulkOperations(items, bulkOperationType, persistentEntity, optPartitionKey);
        return executeBulkOperations(container, bulkOperations, bulkOperationType.expectedOperationStatusCode);
    }

    /**
     * Executes delete or update bulk operations.
     *
     * @param container the container
     * @param bulkOperations the list containing operations to execute
     * @param expectedCode the expected status code for the single operation result
     * @return number of affected items
     */
    private int executeBulkOperations(CosmosContainer container, List<CosmosItemOperation> bulkOperations, int expectedCode) {
        int resultCount = 0;
        Iterable<CosmosBulkOperationResponse<ObjectNode>> bulkOperationResponses = container.executeBulkOperations(bulkOperations);
        for (CosmosBulkOperationResponse<?> bulkOperationResponse : bulkOperationResponses) {
            if (bulkOperationResponse.getResponse().getStatusCode() == expectedCode) {
                resultCount++;
            }
        }
        return resultCount;
    }

    // Container related code

    private <T> CosmosContainer getContainer(EntityInstanceOperation<T> operation) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return getContainer(persistentEntity);
    }

    /**
     * Gets the container for given persistent entity. It is expected that at this point container is created.
     *
     * @param persistentEntity the persistent entity (to be persisted in container)
     * @return the Cosmos container
     */
    private CosmosContainer getContainer(RuntimePersistentEntity<?> persistentEntity) {
        return cosmosDatabase.getContainer(persistentEntity.getPersistedName());
    }

    private <T> CosmosEntityOperation<T> createCosmosInsertOneOperation(CosmosOperationContext<T> ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new CosmosEntityOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, entity, true) {

            @Override
            protected void execute() throws RuntimeException {
                CosmosContainer container = ctx.getContainer();
                if (hasGeneratedId) {
                    RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                    if (identity.getProperty().get(entity) == null && identity.getDataType().equals(DataType.STRING)) {
                        identity.getProperty().convertAndSet(entity, UUID.randomUUID().toString());
                    }
                }
                ObjectNode item = serialize(entity, Argument.of(ctx.getRootEntity()));
                container.createItem(item, new CosmosItemRequestOptions());
            }
        };
    }

    private <T> CosmosEntityOperation<T> createCosmosReplaceItemOperation(CosmosOperationContext<T> ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new CosmosEntityOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, entity, false) {

            @Override
            protected void execute() throws RuntimeException {
                CosmosContainer container = ctx.getContainer();
                ObjectNode item = serialize(entity, Argument.of(ctx.getRootEntity()));
                PartitionKey partitionKey = getPartitionKey(persistentEntity, item);
                String id = getItemId(item);
                container.replaceItem(item, id, partitionKey, new CosmosItemRequestOptions());
            }

        };
    }

    private <T> CosmosEntityOperation<T> createCosmosDeleteOneOperation(CosmosOperationContext<T> ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new CosmosEntityOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, entity, false) {

            @Override
            protected void execute() throws RuntimeException {
                CosmosContainer container = ctx.getContainer();
                ObjectNode item = serialize(entity, Argument.of(ctx.getRootEntity()));
                CosmosItemRequestOptions options = new CosmosItemRequestOptions();
                String id = getItemId(item);
                PartitionKey partitionKey = getPartitionKey(persistentEntity, item);
                CosmosItemResponse<?> cosmosItemResponse = container.deleteItem(id, partitionKey, options);
                if (cosmosItemResponse.getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
                   affectedCount = 1;
                } else {
                    affectedCount = 0;
                }
            }
        };
    }

    private <T> CosmosEntitiesOperation<T> createCosmosBulkOperation(CosmosOperationContext<T> ctx,
                                                                   RuntimePersistentEntity<T> persistentEntity,
                                                                   BatchOperation<T> operation,
                                                                   BulkOperationType operationType) {
        return new CosmosEntitiesOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, operation) {

            @Override
            protected void execute() throws RuntimeException {
                Iterable<T> allowedEntities = entities.stream().filter(d -> !d.vetoed).map(d -> d.entity).collect(Collectors.toList());
                List<ObjectNode> items = serializeBatchItems(allowedEntities, operation.getRootEntity());
                this.affectedCount = executeBulk(ctx.getContainer(), items, operationType, persistentEntity, Optional.empty());
            }
        };
    }

    /**
     * The {@link Spliterator} used when reading an entity or dto projection from the result stream.
     *
     * @param <R> the result type returned by spliterator
     */
    private class EntityOrDtoSpliterator<R> extends Spliterators.AbstractSpliterator<R> {

        private final AtomicBoolean finished;
        private final Iterator<ObjectNode> iterator;
        private final Argument<R> argument;

        EntityOrDtoSpliterator(long est, int additionalCharacteristics,  AtomicBoolean finished,
                               Iterator<ObjectNode> iterator, Argument<R> argument) {
            super(est, additionalCharacteristics);
            this.finished = finished;
            this.iterator = iterator;
            this.argument = argument;
        }

        @Override
        public boolean tryAdvance(Consumer<? super R> action) {
            if (finished.get()) {
                return false;
            }
            try {
                boolean hasNext = iterator.hasNext();
                if (hasNext) {
                    ObjectNode item = iterator.next();
                    R o = deserialize(item, argument);
                    action.accept(o);
                } else {
                    finished.set(true);
                }
                return hasNext;
            } catch (Exception e) {
                throw new DataAccessException("Error retrieving next Cosmos result: " + e.getMessage(), e);
            }
        }
    }

    /**
     * The {@link Spliterator} used when reading custom type (not an entity or dto projection) from the result stream.
     *
     * @param <R> the result type returned by spliterator
     */
    private static class CustomResultTypeSpliterator<R> extends Spliterators.AbstractSpliterator<R> {

        private final AtomicBoolean finished;
        private final Iterator<?> iterator;
        private final Class<R> resultType;

        CustomResultTypeSpliterator(long est, int additionalCharacteristics,  AtomicBoolean finished,
                                    Iterator<?> iterator, Class<R> resultType) {
            super(est, additionalCharacteristics);
            this.finished = finished;
            this.iterator = iterator;
            this.resultType = resultType;
        }

        @Override
        public boolean tryAdvance(Consumer<? super R> action) {
            if (finished.get()) {
                return false;
            }
            try {
                boolean hasNext = iterator.hasNext();
                if (hasNext) {
                    Object v = iterator.next();
                    if (resultType.isInstance(v)) {
                        action.accept((R) v);
                    } else if (v != null) {
                        R r = ConversionService.SHARED.convertRequired(v, resultType);
                        if (r != null) {
                            action.accept(r);
                        }
                    }
                } else {
                    finished.set(true);
                }
                return hasNext;
            } catch (Exception e) {
                throw new DataAccessException("Error retrieving next Cosmos result: " + e.getMessage(), e);
            }
        }
    }

    /**
     * The Cosmos Db operation context.
     *
     * @param <T> the entity type
     */
    private static class CosmosOperationContext<T> extends OperationContext {

        private final CosmosContainer container;
        private final Class<T> rootEntity;

        public CosmosOperationContext(AnnotationMetadata annotationMetadata, Class<?> repositoryType, CosmosContainer container, Class<T> rootEntity) {
            super(annotationMetadata, repositoryType);
            this.container = container;
            this.rootEntity = rootEntity;
        }

        /**
         * @return gets the container in which operation is executing
         */
        public CosmosContainer getContainer() {
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
     * Base class for Cosmos entity operation (insert, update and delete).
     *
     * @param <T> the entity type
     */
    private abstract static class CosmosEntityOperation<T> extends AbstractSyncEntityOperations<CosmosOperationContext<T>, T, RuntimeException> {

        protected int affectedCount;

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
        protected CosmosEntityOperation(EntityEventListener<Object> entityEventListener,
                                        ConversionService<?> conversionService,
                                        CosmosOperationContext<T> ctx,
                                        RuntimePersistentEntity<T> persistentEntity,
                                        T entity,
                                        boolean insert) {
            super(ctx, null, entityEventListener, persistentEntity, conversionService, entity, insert);
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
            // No cascade in Cosmos for now
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
            // No cascade in Cosmos for now
        }
    }

    /**
     * Base class for Cosmos multiple entities operation (update and delete while insert is done manually calling single insert operation).
     *
     * @param <T> the entity type
     */
    private abstract static class CosmosEntitiesOperation<T> extends AbstractSyncEntitiesOperations<CosmosOperationContext<T>, T, RuntimeException> {

        protected int affectedCount;

        /**
         * Default constructor.
         *
         * @param entityEventListener The entity event listener
         * @param conversionService   The conversion service
         * @param ctx                 The context
         * @param persistentEntity    The persistent entity
         * @param entities            The entities
         */
        protected CosmosEntitiesOperation(EntityEventListener<Object> entityEventListener,
                                          ConversionService<?> conversionService,
                                          CosmosOperationContext ctx,
                                          RuntimePersistentEntity<T> persistentEntity,
                                          Iterable<T> entities) {
            super(ctx, null, conversionService, entityEventListener, persistentEntity, entities, false);
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
            // No cascade implemented for Cosmos
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
            // No cascade implemented for Cosmos
        }
    }
}
