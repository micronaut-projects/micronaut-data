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

import com.azure.cosmos.implementation.RequestOptions;
import com.azure.cosmos.implementation.batch.ItemBulkOperation;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosItemOperationType;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.cosmos.common.Constants;
import io.micronaut.data.cosmos.common.CosmosDatabaseInitializer;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
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
import org.slf4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The base class with common code for Cosmos Db operations.
 *
 * @author radovanradic
 * @since 4.0.0
 */
abstract class AbstractCosmosOperations extends AbstractRepositoryOperations implements
    PreparedQueryDecorator,
    MethodContextAwareStoredQueryDecorator {

    // This should return exact collection item by the id in given container
    protected static final String FIND_ONE_DEFAULT_QUERY = "SELECT * FROM root WHERE root.id = @ROOT_ID";

    protected static final Logger QUERY_LOG = DataSettings.QUERY_LOG;

    private final SerdeRegistry serdeRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, CosmosDatabaseConfiguration.CosmosContainerSettings> cosmosContainerSettingsMap;

    /**
     * Default constructor.
     *
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     * @param serdeRegistry              The (de)serialization registry
     * @param objectMapper               The object mapper used for the data (de)serialization
     * @param configuration              The Cosmos database configuration
     */
    protected AbstractCosmosOperations(List<MediaTypeCodec> codecs,
                                                DateTimeProvider<Object> dateTimeProvider,
                                                RuntimeEntityRegistry runtimeEntityRegistry,
                                                DataConversionService<?> conversionService,
                                                AttributeConverterRegistry attributeConverterRegistry,
                                                SerdeRegistry serdeRegistry,
                                                ObjectMapper objectMapper,
                                                CosmosDatabaseConfiguration configuration) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.serdeRegistry = serdeRegistry;
        this.objectMapper = objectMapper;
        cosmosContainerSettingsMap = CollectionUtils.isEmpty(configuration.getContainers()) ? Collections.emptyMap() :
            configuration.getContainers().stream().collect(Collectors.toMap(CosmosDatabaseConfiguration.CosmosContainerSettings::getContainerName, Function.identity()));
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

    /**
     * Serializes given bean to the given type which will be {@link com.fasterxml.jackson.databind.node.ObjectNode} or {@link com.fasterxml.jackson.databind.JsonNode}.
     *
     * @param bean the bean being serialized to JSON
     * @param type the argument type
     * @param <O> the type to be returned
     * @return the serialized bean to JSON (JsonNode or ObjectNode)
     */
    protected <O extends com.fasterxml.jackson.databind.JsonNode> O serialize(Object bean, Argument<?> type) {
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

    /**
     * Deserializes from {@link ObjectNode} to the given bean type.
     *
     * @param objectNode the object node (JSON representation)
     * @param type the argument type
     * @param <T> the type to be returned
     * @return the deserialized object of T type
     */
    protected <T> T deserialize(ObjectNode objectNode, Argument<T> type) {
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

    /**
     * Logs Cosmos Db SQL query being executed along with parameter values (debug level).
     *
     * @param querySpec the SQL query spec
     * @param params the SQL parameters
     */
    protected void logQuery(SqlQuerySpec querySpec, Iterable<SqlParameter> params) {
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing query: {}", querySpec.getQueryText());
            for (SqlParameter param : params) {
                QUERY_LOG.debug("Parameter: name={}, value={}", param.getName(), param.getValue(Object.class));
            }
        }
    }

    /**
     * Gets an indicator telling whether persistent entity identity field matches with the container partition key for that entity.
     *
     * @param persistentEntity persistent entity
     * @return true if persistent entity identity field matches with the container partition key for that entity
     */
    protected boolean isIdPartitionKey(PersistentEntity persistentEntity) {
        String partitionKey = getPartitionKeyDefinition(persistentEntity);
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
    @NonNull
    private String getPartitionKeyDefinition(PersistentEntity persistentEntity) {
        CosmosDatabaseConfiguration.CosmosContainerSettings cosmosContainerSettings = cosmosContainerSettingsMap.get(persistentEntity.getPersistedName());
        if (cosmosContainerSettings == null) {
            return Constants.NO_PARTITION_KEY;
        }
        return CosmosDatabaseInitializer.getPartitionKey(persistentEntity, cosmosContainerSettings);
    }

    /**
     * Gets partition key for a document. Partition keys can be only string or number values.
     * TODO: Later deal with nested paths when we support it.
     *
     * @param persistentEntity the persistent entity
     * @param item item from the Cosmos Db
     * @return partition key, if partition key defined and value set otherwise null
     */
    @Nullable
    protected PartitionKey getPartitionKey(RuntimePersistentEntity<?> persistentEntity, ObjectNode item) {
        String partitionKeyDefinition = getPartitionKeyDefinition(persistentEntity);
        if (partitionKeyDefinition.startsWith("/")) {
            partitionKeyDefinition = partitionKeyDefinition.substring(1);
        }
        com.fasterxml.jackson.databind.JsonNode jsonNode = item.get(partitionKeyDefinition);
        if (jsonNode == null) {
            return null;
        }
        Object value;
        if (jsonNode.isNumber()) {
            value = jsonNode.numberValue();
        } else if (jsonNode.isBoolean()) {
            value = jsonNode.booleanValue();
        } else {
            value = jsonNode.textValue();
        }
        return new PartitionKey(value);
    }

    /**
     * Gets an indicator telling whether {@link PreparedQuery} is raw query.
     *
     * @param preparedQuery the prepared query
     * @return true if prepared query is created from raw query
     */
    boolean isRawQuery(@NonNull PreparedQuery<?, ?> preparedQuery) {
        return preparedQuery.getAnnotationMetadata().stringValue(Query.class, DataMethod.META_MEMBER_RAW_QUERY).isPresent();
    }

    /**
     * Gets the id from {@link ObjectNode} document in Cosmos Db.
     *
     * @param item the item/document in the db
     * @return document id
     */
    String getItemId(ObjectNode item) {
        com.fasterxml.jackson.databind.JsonNode idNode = item.get(Constants.INTERNAL_ID);
        return idNode.textValue();
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
            case BIGDECIMAL:
                return BigDecimal.class;
            case TIME:
                return Time.class;
            default:
                return Object.class;
        }
    }

    /**
     * Updates existing {@link ObjectNode} item with given property values.
     *
     * @param item the {@link ObjectNode} item to be updated
     * @param propertiesToUpdate map with property keys and values to update
     * @return updated {@link ObjectNode} with new values
     */
    ObjectNode updateProperties(ObjectNode item, Map<String, Object> propertiesToUpdate) {
        // iterate through properties, update and replace item
        for (Map.Entry<String, Object> propertyToUpdate : propertiesToUpdate.entrySet()) {
            String property = propertyToUpdate.getKey();
            Object value = propertyToUpdate.getValue();
            com.fasterxml.jackson.databind.JsonNode objectNode;
            if (value == null) {
                objectNode = NullNode.getInstance();
            } else {
                objectNode = serialize(value, Argument.of(value.getClass()));
            }
            item.set(property, objectNode);
        }
        return item;
    }

    /**
     * Creates list of {@link CosmosItemOperation} to be executed in bulk operation.
     *
     * @param items the items to be updated/deleted in a bulk operation
     * @param bulkOperationType the bulk operation type (delete or update)
     * @param persistentEntity the persistent entity
     * @param optPartitionKey the optional partition key, will be used if not empty
     * @return list of {@link CosmosItemOperation}s
     */
    List<CosmosItemOperation> createBulkOperations(Iterable<ObjectNode> items, BulkOperationType bulkOperationType, RuntimePersistentEntity<?> persistentEntity,
                                                   Optional<PartitionKey> optPartitionKey) {
        return createBulkOperations(items, bulkOperationType, persistentEntity, optPartitionKey, null);
    }

    /**
     * Creates list of {@link CosmosItemOperation} to be executed in bulk operation.
     *
     * @param items the items to be updated/deleted in a bulk operation
     * @param bulkOperationType the bulk operation type (delete or update)
     * @param persistentEntity the persistent entity
     * @param optPartitionKey the optional partition key, will be used if not empty
     * @param handleItem function that will apply some changes before adding item to the list, if null then ignored
     * @return list of {@link CosmosItemOperation}s
     */
    List<CosmosItemOperation> createBulkOperations(Iterable<ObjectNode> items, BulkOperationType bulkOperationType, RuntimePersistentEntity<?> persistentEntity,
                                                   Optional<PartitionKey> optPartitionKey , UnaryOperator<ObjectNode> handleItem) {
        List<CosmosItemOperation> bulkOperations = new ArrayList<>();
        RequestOptions requestOptions = new RequestOptions();
        for (ObjectNode item : items) {
            if (handleItem != null) {
                item = handleItem.apply(item);
            }
            String id = getItemId(item);
            ObjectNode finalItem = item;
            PartitionKey partitionKey = optPartitionKey.orElseGet(() -> getPartitionKey(persistentEntity, finalItem));
            bulkOperations.add(new ItemBulkOperation<>(bulkOperationType.cosmosItemOperationType, id, partitionKey, requestOptions, item, null));
        }
        return bulkOperations;
    }

    /**
     * Custom class used for binding parameters for Cosmos sql queries.
     * Needed to be able to extract update parameters for update actions, so we can call replace API.
     */
    class ParameterBinder {

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
            boolean isRawQuery = isRawQuery(preparedQuery);
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
            List<SqlParameter> parameterList = new ArrayList<>();
            SqlPreparedQuery<T, R> sqlPreparedQuery = getSqlPreparedQuery(preparedQuery);
            sqlPreparedQuery.bindParameters(new SqlStoredQuery.Binder() {

                @NonNull
                @Override
                public Object autoPopulateRuntimeProperty(@NonNull RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
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
                public void bindOne(@NonNull QueryParameterBinding binding, Object value) {
                    if (updateQuery) {
                        String property = getUpdateProperty(binding, persistentEntity);
                        if (property != null) {
                            propertiesToUpdate.put(property, value);
                        }
                    }
                    String parameterName = getParameterName(binding, isRawQuery);
                    parameterList.add(new SqlParameter("@" + parameterName, value));
                }

                @Override
                public void bindMany(@NonNull QueryParameterBinding binding, @NonNull Collection<Object> values) {
                    bindOne(binding, values);
                }

                @Override
                public int currentIndex() {
                    return 0;
                }

            });
            return parameterList;
        }

        private String getParameterName(QueryParameterBinding binding, boolean isRawQuery) {
            if (isRawQuery) {
                // raw query parameters get rewritten as p1, p2... and binding.getRequiredName remains as original, so we need to bind proper param name
                return "p" + (binding.getParameterIndex() + 1);
            }
            return binding.getRequiredName();
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

    /**
     * The bulk operation type used when creating bulk operations against Cosmos Db.
     * Need to know what type (supported DELETE and REPLACE) and what expected status code
     * for each item is to be treated as successful.
     */
    enum BulkOperationType {

        DELETE(CosmosItemOperationType.DELETE, HttpResponseStatus.NO_CONTENT.code()),
        UPDATE(CosmosItemOperationType.REPLACE, HttpResponseStatus.OK.code());

        final CosmosItemOperationType cosmosItemOperationType;
        final int expectedOperationStatusCode;

        BulkOperationType(CosmosItemOperationType cosmosItemOperationType, int expectedOperationStatusCode) {
            this.cosmosItemOperationType = cosmosItemOperationType;
            this.expectedOperationStatusCode = expectedOperationStatusCode;
        }
    }
}
