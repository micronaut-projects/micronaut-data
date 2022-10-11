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
import com.azure.cosmos.implementation.RequestOptions;
import com.azure.cosmos.implementation.batch.ItemBulkOperation;
import com.azure.cosmos.models.CosmosBulkOperationResponse;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosItemOperationType;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.cosmos.common.Constants;
import io.micronaut.data.cosmos.common.CosmosDatabaseInitializer;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.EntityInstanceOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlStoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.jackson.JacksonDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The default Azure Cosmos DB operations implementation.
 */
@Singleton
@Requires(bean = CosmosClient.class)
@Internal
final class DefaultCosmosRepositoryOperations extends AbstractRepositoryOperations implements CosmosRepositoryOperations,
    PreparedQueryDecorator, MethodContextAwareStoredQueryDecorator {

    // This should return exact collection item by the id in given container
    private static final String FIND_ONE_DEFAULT_QUERY = "SELECT * FROM root WHERE root.id = @ROOT_ID";

    private static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCosmosRepositoryOperations.class);

    private final SerdeRegistry serdeRegistry;
    private final ObjectMapper objectMapper;
    private final CosmosDatabase cosmosDatabase;

    private final Map<String, CosmosDatabaseConfiguration.CosmosContainerSettings> cosmosContainerSettingsMap;

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
     */
    protected DefaultCosmosRepositoryOperations(List<MediaTypeCodec> codecs,
                                                DateTimeProvider<Object> dateTimeProvider,
                                                RuntimeEntityRegistry runtimeEntityRegistry,
                                                DataConversionService<?> conversionService,
                                                AttributeConverterRegistry attributeConverterRegistry,
                                                CosmosClient cosmosClient,
                                                SerdeRegistry serdeRegistry,
                                                ObjectMapper objectMapper,
                                                CosmosDatabaseConfiguration configuration) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.serdeRegistry = serdeRegistry;
        this.objectMapper = objectMapper;
        this.cosmosDatabase = cosmosClient.getDatabase(configuration.getDatabaseName());
        cosmosContainerSettingsMap = CollectionUtils.isEmpty(configuration.getContainers()) ? Collections.emptyMap() :
            configuration.getContainers().stream().collect(Collectors.toMap(CosmosDatabaseConfiguration.CosmosContainerSettings::getContainerName, Function.identity()));
    }

    @Override
    public <T> T findOne(Class<T> type, Serializable id) {
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
                ObjectNode beanTree = iterator.next();
                if (iterator.hasNext()) {
                    throw new NonUniqueResultException();
                }
                return deserialize(beanTree, Argument.of(type));
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
    public <T, R> R findOne(PreparedQuery<T, R> preparedQuery) {
        try {
            List<SqlParameter> paramList = new ParameterBinder().bindParameters(preparedQuery);
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
                return deserialize(item, Argument.of((Class<R>) preparedQuery.getRootEntity()));
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
    public <T> boolean exists(PreparedQuery<T, Boolean> preparedQuery) {
        List<SqlParameter> paramList = new ParameterBinder().bindParameters(preparedQuery);
        CosmosPagedIterable<ObjectNode> result = getCosmosResults(preparedQuery, paramList, ObjectNode.class);
        Iterator<ObjectNode> iterator = result.iterator();
        return iterator.hasNext();
    }

    @Override
    public <T> Iterable<T> findAll(PagedQuery<T> query) {
        return null;
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        return 0;
    }

    @Override
    public <T, R> Iterable<R> findAll(PreparedQuery<T, R> preparedQuery) {
        try (Stream<R> stream = findStream(preparedQuery)) {
            return stream.collect(Collectors.toList());
        }
    }

    @Override
    public <T, R> Stream<R> findStream(PreparedQuery<T, R> preparedQuery) {
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
                    argument = Argument.of((Class<R>) preparedQuery.getRootEntity());
                }
                spliterator = new EntityOrDtoSpliterator<>(Long.MAX_VALUE,Spliterator.ORDERED | Spliterator.IMMUTABLE,
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

    @Override
    public <T> Stream<T> findStream(PagedQuery<T> query) {
        return null;
    }

    @Override
    public <R> Page<R> findPage(PagedQuery<R> query) {
        return null;
    }

    @Override
    public <T> T persist(InsertOperation<T> operation) {
        CosmosContainer container = getContainer(operation);
        T entity = operation.getEntity();
        ObjectNode tree = serialize(entity, Argument.of(operation.getRootEntity()));
        container.createItem(tree, new CosmosItemRequestOptions());
        return entity;
    }

    @Override
    public <T> T update(UpdateOperation<T> operation) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        T entity = operation.getEntity();
        CosmosContainer container = getContainer(persistentEntity);
        ObjectNode item = serialize(entity, Argument.of(operation.getRootEntity()));
        Object partitionKeyFieldValue = getPartitionKeyValue(persistentEntity, item);
        if (partitionKeyFieldValue != null) {
            String id = getItemId(item);
            container.replaceItem(item, id, new PartitionKey(partitionKeyFieldValue), new CosmosItemRequestOptions());
        } else {
            container.upsertItem(item);
        }
        return entity;
    }

    @Override
    public <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        CosmosContainer container = getContainer(persistentEntity);
        Argument<T> arg = Argument.of(operation.getRootEntity());
        String partitionKeyDefinition = getPartitionKey(persistentEntity);
        if (StringUtils.isEmpty(partitionKeyDefinition)) {
            LOG.debug("updateAll items one by one since partition key not provided or defined for entity {}", persistentEntity.getName());
            for (T entity : operation) {
                ObjectNode item = serialize(entity, arg);
                Object partitionKeyFieldValue = getPartitionKeyValue(persistentEntity, item);
                if (partitionKeyFieldValue != null) {
                    String id = getItemId(item);
                    container.replaceItem(item, id, new PartitionKey(partitionKeyFieldValue), new CosmosItemRequestOptions());
                } else {
                    container.upsertItem(item);
                }
            }
        } else {
            List<ObjectNode> items = new ArrayList<>();
            for (T entity : operation) {
                items.add(serialize(entity, arg));
            }
            executeBulk(container, items, BulkOperationType.UPDATE, persistentEntity, Optional.empty());
        }

        return operation;
    }

    @Override
    public Optional<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        boolean isRawQuery = preparedQuery.getAnnotationMetadata().stringValue(Query.class, DataMethod.META_MEMBER_RAW_QUERY).isPresent();
        if (isRawQuery) {
            throw new IllegalStateException("Cosmos Db does not support raw update queries.");
        }
        RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        String update = preparedQuery.getAnnotationMetadata().stringValue(Query.class, "update").orElse(null);
        if (update == null) {
            throw new IllegalStateException("Could not resolve update properties for query");
        }
        List<String> updatePropertyList = Arrays.asList(update.split(","));
        ParameterBinder parameterBinder = new ParameterBinder(true, updatePropertyList);
        List<SqlParameter> parameterList = parameterBinder.bindParameters(preparedQuery);
        Map<String, Object> propertiesToUpdate = parameterBinder.getPropertiesToUpdate();

        if (propertiesToUpdate.isEmpty()) {
            LOG.debug("No properties found to be updated for Cosmos Db entity {} and query [{}]", persistentEntity.getName(), preparedQuery.getQuery());
            return Optional.of(0);
        }
        CosmosContainer container = getContainer(persistentEntity);
        Optional<PartitionKey> optPartitionKey = preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class);
        CosmosPagedIterable<ObjectNode> result = getCosmosResults(preparedQuery, parameterList, ObjectNode.class);
        String partitionKeyDefinition = getPartitionKey(persistentEntity);
        if (!optPartitionKey.isPresent() && StringUtils.isEmpty(partitionKeyDefinition)) {
            LOG.debug("executeUpdate items one by one since partition key not provided or defined for entity {}", persistentEntity.getName());
            int updateCount = 0;
            for (ObjectNode objectNode : result) {
                ObjectNode item = updateProperties(objectNode, propertiesToUpdate);
                CosmosItemResponse<?> cosmosItemResponse = container.upsertItem(item);
                if (cosmosItemResponse.getStatusCode() == HttpResponseStatus.OK.code()) {
                    updateCount++;
                }
            }
            return Optional.of(updateCount);
        }
        // Upsert using provided partition key or partition key calculated from each item
        List<ObjectNode> items = new ArrayList<>();
        for (ObjectNode item : result) {
            items.add(updateProperties(item, propertiesToUpdate));
        }
        return Optional.of(executeBulk(container, items, BulkOperationType.UPDATE, persistentEntity, optPartitionKey));
    }

    private ObjectNode updateProperties(ObjectNode item, Map<String, Object> propertiesToUpdate) {
        // iterate through properties, update and upsert item
        for (Map.Entry<String, Object> propertyToUpdate : propertiesToUpdate.entrySet()) {
            String property = propertyToUpdate.getKey();
            Object value = propertyToUpdate.getValue();
            com.fasterxml.jackson.databind.JsonNode objectNode = serialize(value, Argument.of(value.getClass()));
            item.remove(property);
            item.set(property, objectNode);
        }
        return item;
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        T entity = operation.getEntity();
        CosmosContainer container = getContainer(persistentEntity);
        ObjectNode item = serialize(entity, Argument.of(operation.getRootEntity()));
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        CosmosItemResponse<?> cosmosItemResponse = container.deleteItem(item, options);
        if (cosmosItemResponse.getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
            return 1;
        }
        return 0;
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        CosmosContainer container = getContainer(persistentEntity);
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        Argument<T> arg = Argument.of(operation.getRootEntity());
        String partitionKeyDefinition = getPartitionKey(persistentEntity);
        int deletedCount;
        if (StringUtils.isEmpty(partitionKeyDefinition)) {
            LOG.debug("deleteAll items one by one since partition key not provided or defined for entity {}", persistentEntity.getName());
            deletedCount = 0;
            // Bulk operations delete won't work without partition key so deleting one by one document
            for (T entity : operation) {
                ObjectNode tree = serialize(entity, arg);
                CosmosItemResponse<?> cosmosItemResponse = container.deleteItem(tree, options);
                if (cosmosItemResponse.getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
                    deletedCount++;
                }
            }
        } else {
            List<ObjectNode> items = new ArrayList<>();
            for (T entity : operation) {
                items.add(serialize(entity, arg));
            }
            deletedCount = executeBulk(container, items, BulkOperationType.DELETE, persistentEntity, Optional.empty());
        }
        return Optional.of(deletedCount);
    }

    @Override
    public Optional<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        boolean isRawQuery = preparedQuery.getAnnotationMetadata().stringValue(Query.class, DataMethod.META_MEMBER_RAW_QUERY).isPresent();
        if (isRawQuery) {
            throw new IllegalStateException("Cosmos Db does not support raw delete queries.");
        }
        RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        CosmosContainer container = getContainer(persistentEntity);
        Optional<PartitionKey> optPartitionKey = preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class);
        List<SqlParameter> parameterList = new ParameterBinder().bindParameters(preparedQuery);
        CosmosPagedIterable<ObjectNode> result = getCosmosResults(preparedQuery, parameterList, ObjectNode.class);
        String partitionKeyDefinition = getPartitionKey(persistentEntity);
        int deletedCount;
        if (optPartitionKey.isPresent()) {
            // Do bulk delete if there is partition key provided
            deletedCount = executeBulk(container, result, BulkOperationType.DELETE, persistentEntity, optPartitionKey);
        } else if (StringUtils.isNotEmpty(partitionKeyDefinition)) {
            // Bulk delete if there is partition key defined on the entity, so we can get its value
            deletedCount = executeBulk(container, result, BulkOperationType.DELETE, persistentEntity, Optional.empty());
        } else {
            // If no partition key provided or defined on the entity (should not happen), delete one by one since partition key is needed for bulk delete
            LOG.debug("executeDelete items one by one since partition key not provided or defined for entity {}", persistentEntity.getName());
            deletedCount = 0;
            CosmosItemRequestOptions options = new CosmosItemRequestOptions();
            for (ObjectNode item : result) {
                CosmosItemResponse<?> cosmosItemResponse = container.deleteItem(item, options);
                if (cosmosItemResponse.getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
                    deletedCount++;
                }
            }
        }
        return Optional.of(deletedCount);
    }

    /**
     * Executes bulk delete for given iterable of {@link ObjectNode}.
     *
     * @param container the container where documents are being deleted
     * @param items the items being deleted
     * @param bulkOperationType the bulk operation type (DELETE or UPDATE)
     * @param persistentEntity the persistent entity corresponding to the items
     * @param optPartitionKey {@link Optional} with {@link PartitionKey} as value, if empty then will obtain partition key from each item
     * @return number of deleted items
     */
    private int executeBulk(CosmosContainer container, Iterable<ObjectNode> items, BulkOperationType bulkOperationType, RuntimePersistentEntity<?> persistentEntity, Optional<PartitionKey> optPartitionKey) {
        List<CosmosItemOperation> bulkOperations = new ArrayList<>();
        RequestOptions requestOptions = new RequestOptions();
        for (ObjectNode item : items) {
            String id = getItemId(item);
            PartitionKey partitionKey;
            if (optPartitionKey.isPresent()) {
                partitionKey = optPartitionKey.get();
            } else {
                Object partitionKeyValue = getPartitionKeyValue(persistentEntity, item);
                partitionKey = partitionKeyValue != null ? new PartitionKey(partitionKeyValue) : null;
                if (partitionKey == null) {
                    LOG.debug("Partition key not found for persistent entity {} with id {}", persistentEntity.getName(), id);
                }
            }
            bulkOperations.add(new ItemBulkOperation<>(bulkOperationType.cosmosItemOperationType, id, partitionKey, requestOptions, item, null));
        }
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

    /**
     * Gets the id from {@link ObjectNode} document in Cosmos Db.
     *
     * @param item the item/document in the db
     * @return document id
     */
    private String getItemId(ObjectNode item) {
        com.fasterxml.jackson.databind.JsonNode idNode = item.get(Constants.INTERNAL_ID);
        return idNode.textValue();
    }

    /**
     * Custom class used for binding parameters for Cosmos sql queries.
     * Needed to be able to extract update parameters for update actions, so we can call upsert API.
     */
    private class ParameterBinder {

        private final boolean updateQuery;
        private final List<String> updatingProperties;

        private final Map<String, Object> propertiesToUpdate = new HashMap<>();

        ParameterBinder() {
            this.updateQuery = false;
            this.updatingProperties = Collections.emptyList();
        }

        ParameterBinder(boolean updateQuery, List<String> updateProperties) {
            this.updateQuery = updateQuery;
            this.updatingProperties = updateProperties;
        }

        <T, R> List<SqlParameter> bindParameters(PreparedQuery<T, R> preparedQuery) {
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
            List<SqlParameter> parameterList = new ArrayList<>();
            SqlPreparedQuery<T, R> sqlPreparedQuery = getSqlPreparedQuery(preparedQuery);
            sqlPreparedQuery.bindParameters(new SqlStoredQuery.Binder() {

                @Override
                public Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
                    return runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
                }

                @Override
                public Object convert(Object value, RuntimePersistentProperty<?> property) {
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
                        return ConversionContext.of(property.getArgument());
                    }
                    if (argument != null) {
                        return ConversionContext.of(argument);
                    }
                    return ConversionContext.DEFAULT;
                }

                @Override
                public void bindOne(QueryParameterBinding binding, Object value) {
                    if (updateQuery) {
                        String property = getUpdateProperty(binding, persistentEntity);
                        if (property != null) {
                            propertiesToUpdate.put(property, value);
                        }
                    }
                    parameterList.add(new SqlParameter("@" + binding.getRequiredName(), value));
                }

                @Override
                public void bindMany(QueryParameterBinding binding, Collection<Object> values) {
                    bindOne(binding, values);
                }

                @Override
                public int currentIndex() {
                    return 0;
                }

            });
            return parameterList;
        }

        private String getUpdateProperty(QueryParameterBinding binding, PersistentEntity persistentEntity) {
            String[] propertyPath = binding.getRequiredPropertyPath();
            PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
            if (pp != null) {
                String propertyName = pp.getPath();
                if (CollectionUtils.isNotEmpty(updatingProperties) && updatingProperties.contains(propertyName)) {
                    return propertyName;
                }
            }
            return null;
        }

        Map<String, Object> getPropertiesToUpdate() {
            return propertiesToUpdate;
        }

        private <E, R> SqlPreparedQuery<E, R> getSqlPreparedQuery(PreparedQuery<E, R> preparedQuery) {
            if (preparedQuery instanceof SqlPreparedQuery) {
                return (SqlPreparedQuery<E, R>) preparedQuery;
            }
            throw new IllegalStateException("Expected for prepared query to be of type: SqlPreparedQuery got: " + preparedQuery.getClass().getName());
        }
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return new DefaultSqlPreparedQuery<>(preparedQuery);
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        SqlQueryBuilder queryBuilder = new SqlQueryBuilder();
        RuntimePersistentEntity<E> runtimePersistentEntity = runtimeEntityRegistry.getEntity(storedQuery.getRootEntity());
        return new DefaultSqlStoredQuery<>(storedQuery, runtimePersistentEntity, queryBuilder);
    }

    private <O> O serialize(Object bean, Argument<?> type) {
        try {
            Serializer.EncoderContext encoderContext = serdeRegistry.newEncoderContext(null);
            Serializer<? super Object> typeSerializer = serdeRegistry.findSerializer(type);
            Serializer<Object> serializer = typeSerializer.createSpecific(encoderContext, type);
            JsonNodeEncoder encoder = JsonNodeEncoder.create();
            serializer.serialize(encoder, encoderContext, type, bean);
            // First serialize to Micronaut Serde tree model and then convert it to Jackson's tree model
            JsonNode jsonNode = encoder.getCompletedValue();
            try (JsonParser jsonParser = JsonNodeTreeCodec.getInstance().treeAsTokens(jsonNode)) {
                return objectMapper.readTree(jsonParser);
            }
        } catch (IOException e) {
            throw new DataAccessException("Failed to serialize: " + e.getMessage(), e);
        }
    }

    private <T> T deserialize(ObjectNode objectNode, Argument<T> type) {
        try {
            Deserializer.DecoderContext decoderContext = serdeRegistry.newDecoderContext(null);
            Deserializer<? extends T> typeDeserializer = serdeRegistry.findDeserializer(type);
            Deserializer<? extends T> deserializer = typeDeserializer.createSpecific(decoderContext, type);
            JsonParser parser = objectNode.traverse();
            if (!parser.hasCurrentToken()) {
                parser.nextToken();
            }
            final Decoder decoder = JacksonDecoder.create(parser, Object.class);
            return deserializer.deserialize(decoder, decoderContext, type);
        } catch (IOException e) {
            throw new DataAccessException("Failed to deserialize: " + e.getMessage(), e);
        }
    }

    private void logQuery(SqlQuerySpec querySpec, Iterable<SqlParameter> params) {
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing query: {}", querySpec.getQueryText());
            for (SqlParameter param : params) {
                QUERY_LOG.debug("Parameter: name={}, value={}", param.getName(), param.getValue(Object.class));
            }
        }
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

    private boolean isIdPartitionKey(PersistentEntity persistentEntity) {
        String partitionKey = getPartitionKey(persistentEntity);
        if (partitionKey == null) {
            return false;
        }
        PersistentProperty identity = persistentEntity.getIdentity();
        if (identity == null) {
            return false;
        }
        return partitionKey.equals("/" + identity.getName());
    }

    /**
     * Gets partition key definition for given persistent entity.
     * It may happen that persistent entity does not have defined partition key and in that case we return empty string (or null).
     *
     * @param persistentEntity the persistent entity
     * @return partition key definition it exists for persistent entity, otherwise empty/null string
     */
    private String getPartitionKey(PersistentEntity persistentEntity) {
        CosmosDatabaseConfiguration.CosmosContainerSettings cosmosContainerSettings = cosmosContainerSettingsMap.get(persistentEntity.getPersistedName());
        if (cosmosContainerSettings == null) {
            return null;
        }
        return CosmosDatabaseInitializer.getPartitionKey(persistentEntity, cosmosContainerSettings);
    }

    /**
     * Gets partition key value for a document. Partition keys can be only string or number values.
     * TODO: Later deal with nested paths when we support it.
     *
     * @param persistentEntity the persistent entity
     * @param item item from the Cosmos Db
     * @return partition key value, if partition key defined and value set
     */
    private Object getPartitionKeyValue(RuntimePersistentEntity<?> persistentEntity, ObjectNode item) {
        String partitionKey = getPartitionKey(persistentEntity);
        if (partitionKey == null) {
            return null;
        }
        if (partitionKey.startsWith("/")) {
            partitionKey = partitionKey.substring(1);
        }
        com.fasterxml.jackson.databind.JsonNode jsonNode = item.get(partitionKey);
        if (jsonNode == null) {
            return  null;
        }
        return jsonNode.isNumber() ? jsonNode.numberValue() : jsonNode.textValue();
    }

    /**
     * Gets underlying java class for the {@link DataType}.
     *
     * @param dataType the data type
     * @return java class for the data type
     */
    Class<?> getDataTypeClass(DataType dataType) {
        switch (dataType) {
            case STRING:
            case JSON:
                return String.class;
            case UUID:
                return UUID.class;
            case LONG:
                return Long.class;
            case INTEGER:
                return Integer.class;
            case BOOLEAN:
                return Boolean.class;
            case BYTE:
                return Byte.class;
            case TIMESTAMP:
            case DATE:
                return Date.class;
            case CHARACTER:
                return Character.class;
            case FLOAT:
                return Float.class;
            case SHORT:
                return Short.class;
            case DOUBLE:
                return Double.class;
            case BYTE_ARRAY:
                return Byte[].class;
            case BIGDECIMAL:
                return BigDecimal.class;
            case BOOLEAN_ARRAY:
                return Boolean[].class;
            case LONG_ARRAY:
                return Long[].class;
            case FLOAT_ARRAY:
                return Float[].class;
            case SHORT_ARRAY:
                return Short[].class;
            case DOUBLE_ARRAY:
                return Double[].class;
            case CHARACTER_ARRAY:
                return Character[].class;
            case INTEGER_ARRAY:
                return Integer[].class;
            case STRING_ARRAY:
                return String[].class;
            case TIME:
                return Time.class;
            default:
                return Object.class;
        }
    }

    /**
     * The bulk operation type used when creating bulk operations against Cosmos Db.
     * Need to know what type (supported DELETE and UPSERT) and what expected status code
     * for each item is to be treated as successful.
     */
    private enum BulkOperationType {

        DELETE(CosmosItemOperationType.DELETE, HttpResponseStatus.NO_CONTENT.code()),
        UPDATE(CosmosItemOperationType.REPLACE, HttpResponseStatus.OK.code());

        private final CosmosItemOperationType cosmosItemOperationType;
        private final int expectedOperationStatusCode;

        BulkOperationType(CosmosItemOperationType cosmosItemOperationType, int expectedOperationStatusCode) {
            this.cosmosItemOperationType = cosmosItemOperationType;
            this.expectedOperationStatusCode = expectedOperationStatusCode;
        }
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
                    ObjectNode beanTree = iterator.next();
                    R o = deserialize(beanTree, argument);
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
}
