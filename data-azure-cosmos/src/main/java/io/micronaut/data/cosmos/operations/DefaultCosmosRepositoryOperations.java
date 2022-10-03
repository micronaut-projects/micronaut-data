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
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.cosmos.annotation.CosmosContainerDef;
import io.micronaut.data.cosmos.common.Constants;
import io.micronaut.data.cosmos.common.CosmosContainerProps;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final CosmosClient cosmosClient;
    private final SerdeRegistry serdeRegistry;
    private final ObjectMapper objectMapper;
    private final CosmosDatabase cosmosDatabase;

    private final Map<PersistentEntity, CosmosContainerProps> containersByEntityMap = new ConcurrentHashMap<>();

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
        this.cosmosClient = cosmosClient;
        this.serdeRegistry = serdeRegistry;
        this.objectMapper = objectMapper;
        this.cosmosDatabase = initDatabase(configuration);
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
                return deserializeFromTree(beanTree, Argument.of(type));
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
    public <T, R> R findOne(PreparedQuery<T, R> preparedQuery) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        CosmosContainer container = getContainer(persistentEntity);
        List<SqlParameter> paramList = bindParameters(preparedQuery);
        SqlQuerySpec querySpec = new SqlQuerySpec(preparedQuery.getQuery(), paramList);
        logQuery(querySpec, paramList);
        CosmosQueryRequestOptions requestOptions = new CosmosQueryRequestOptions();
        preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class).ifPresent(requestOptions::setPartitionKey);
        CosmosPagedIterable<ObjectNode> result = container.queryItems(querySpec, requestOptions, ObjectNode.class);
        Iterator<ObjectNode> iterator = result.iterator();
        if (iterator.hasNext()) {
            ObjectNode beanTree = iterator.next();
            if (iterator.hasNext()) {
                throw new NonUniqueResultException();
            }
            // TODO: DTOs etc
            return deserializeFromTree(beanTree, Argument.of((Class<R>) preparedQuery.getRootEntity()));
        }
        return null;
    }

    @Override
    public <T> boolean exists(PreparedQuery<T, Boolean> preparedQuery) {
        return false;
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
        return null;
    }

    @Override
    public <T, R> Stream<R> findStream(PreparedQuery<T, R> preparedQuery) {
        return null;
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
        ObjectNode tree = serializeToTree(entity, Argument.of(operation.getRootEntity()));
        container.createItem(tree, new CosmosItemRequestOptions());
        return entity;
    }

    @Override
    public <T> T update(UpdateOperation<T> operation) {
        return null;
    }

    @Override
    public Optional<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return Optional.empty();
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        return 0;
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return Optional.empty();
    }

    private <T, R> List<SqlParameter> bindParameters(PreparedQuery<T, R> preparedQuery) {
        List<SqlParameter> paramList = new ArrayList<>();
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
                paramList.add(new SqlParameter("@" + binding.getRequiredName(), value));
            }

            @Override
            public void bindMany(QueryParameterBinding binding, Collection<Object> values) {
                bindOne(binding, values);
            }

            @Override
            public int currentIndex() {
                return 0;
            }

        }); return paramList;
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

    private ObjectNode serializeToTree(Object bean, Argument<?> type) {
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

    private <T> T deserializeFromTree(ObjectNode objectNode, Argument<T> type) {
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

    private <E, R> SqlPreparedQuery<E, R> getSqlPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        if (preparedQuery instanceof SqlPreparedQuery) {
            return (SqlPreparedQuery<E, R>) preparedQuery;
        }
        throw new IllegalStateException("Expected for prepared query to be of type: SqlPreparedQuery got: " + preparedQuery.getClass().getName());
    }

    private <E, R> SqlStoredQuery<E, R> getSqlStoredQuery(StoredQuery<E, R> storedQuery) {
        if (storedQuery instanceof SqlStoredQuery) {
            SqlStoredQuery<E, R> sqlStoredQuery = (SqlStoredQuery<E, R>) storedQuery;
            if (sqlStoredQuery.isExpandableQuery() && !(sqlStoredQuery instanceof SqlPreparedQuery)) {
                return new DefaultSqlPreparedQuery<>(sqlStoredQuery);
            }
            return sqlStoredQuery;
        }
        throw new IllegalStateException("Expected for prepared query to be of type: SqlStoredQuery got: " + storedQuery.getClass().getName());
    }

    private void logQuery(SqlQuerySpec querySpec, Iterable<SqlParameter> params) {
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing query: {}", querySpec.getQueryText());
            for (SqlParameter param : params) {
                QUERY_LOG.debug("Parameter: name={}, value={}", param.getName(), param.getValue(Object.class));
            }
        }
    }

    // Init database and containers

    private CosmosDatabase initDatabase(CosmosDatabaseConfiguration configuration) {
        CosmosDatabaseResponse databaseResponse;
        ThroughputProperties throughputProperties = createThroughputProperties(configuration);
        if (throughputProperties == null) {
            databaseResponse = cosmosClient.createDatabaseIfNotExists(configuration.getDatabaseName());
        } else {
            databaseResponse = cosmosClient.createDatabaseIfNotExists(configuration.getDatabaseName(), throughputProperties);
        }
        CosmosDatabase cosmosDatabase = cosmosClient.getDatabase(databaseResponse.getProperties().getId());
        initContainers(cosmosDatabase);
        return cosmosDatabase;
    }

    private ThroughputProperties createThroughputProperties(CosmosDatabaseConfiguration configuration) {
        // Throughput properties for the database
        if (configuration.getThroughputRequestUnits() != null) {
            if (configuration.isThroughputAutoScale()) {
                return ThroughputProperties.createAutoscaledThroughput(configuration.getThroughputRequestUnits());
            } else {
                return ThroughputProperties.createManualThroughput(configuration.getThroughputRequestUnits());
            }
        }
        return null;
    }

    private void initContainers(CosmosDatabase cosmosDatabase) {
        // Find container definitions
        List<BeanIntrospection<Object>> introspections = BeanIntrospector.SHARED.findIntrospections(CosmosContainerDef.class).stream()
            // filter out inner / internal / abstract(MappedSuperClass) classes
            .filter(i -> !i.getBeanType().getName().contains("$"))
            .filter(i -> !java.lang.reflect.Modifier.isAbstract(i.getBeanType().getModifiers()))
            .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(introspections)) {
            LOG.warn("Did not find any CosmosContainerDef definition");
            return;
        }
        for (BeanIntrospection<Object> introspection : introspections) {
            processCosmosContainerDef(introspection, cosmosDatabase);
        }
    }

    private void processCosmosContainerDef(BeanIntrospection<Object> introspection, CosmosDatabase cosmosDatabase) {
        AnnotationValue<CosmosContainerDef> cosmosContainerDefAnnotationValue = introspection.getAnnotation(CosmosContainerDef.class);
        AnnotationClassValue<Object>[] mappedEntitiesClassValues = (AnnotationClassValue<Object>[]) cosmosContainerDefAnnotationValue.annotationClassValues("mappedEntities");
        List<PersistentEntity> persistentEntities = new ArrayList<>(mappedEntitiesClassValues.length);
        for (AnnotationClassValue<Object> mappedEntitiesClassValue : mappedEntitiesClassValues) {
            if (!mappedEntitiesClassValue.getType().isPresent()) {
                LOG.warn("Mapped entity class not found {}", mappedEntitiesClassValue.getName());
                continue;
            }
            Optional<BeanIntrospection<Object>> optMappedEntity = BeanIntrospector.SHARED.findIntrospection(mappedEntitiesClassValue.getType().get());
            if (!optMappedEntity.isPresent() || !optMappedEntity.get().hasAnnotation(MappedEntity.class)) {
                LOG.warn("Class is not annotated with MappedEntity {}", mappedEntitiesClassValue.getName());
            } else {
                BeanIntrospection<Object> mappedEntity = optMappedEntity.get();
                if (!mappedEntity.getBeanType().getName().contains("$") && !java.lang.reflect.Modifier.isAbstract(mappedEntity.getBeanType().getModifiers())) {
                    persistentEntities.add(runtimeEntityRegistry.getEntity(mappedEntity.getBeanType()));
                }
            }

        }
        if (persistentEntities.isEmpty()) {
            LOG.warn("Skipping container for {} because it does not have mapped entities defined or found on class path.", introspection.getBeanType());
        } else {
            // Initialize container and add to cache map with container by entities
            CosmosContainerProps cosmosContainerProps = getCosmosContainerProps(cosmosContainerDefAnnotationValue);
            for (PersistentEntity persistentEntity : persistentEntities) {
                containersByEntityMap.put(persistentEntity, cosmosContainerProps);
                initContainer(cosmosContainerProps, cosmosDatabase);
            }
        }
    }

    private void initContainer(CosmosContainerProps props, CosmosDatabase cosmosDatabase) {
        String containerName = props.getContainerName();
        String partitionKey = getPartitionKey(props);
        CosmosContainerProperties containerProperties = new CosmosContainerProperties(containerName, partitionKey);
        ThroughputProperties throughputProperties = props.getThroughputProperties();
        if (throughputProperties == null) {
            cosmosDatabase.createContainerIfNotExists(containerProperties);
        } else {
            cosmosDatabase.createContainerIfNotExists(containerProperties, throughputProperties);
        }
    }

    /**
     * Creates {@link CosmosContainerProps} from the {@link CosmosContainerDef} annotation values.
     *
     * @param cosmosContainerDefData the annotation value for {@link CosmosContainerDef}
     * @return new {@link CosmosContainerProps}
     */
    private CosmosContainerProps getCosmosContainerProps(@NonNull AnnotationValue<CosmosContainerDef> cosmosContainerDefData) {
        String containerName = cosmosContainerDefData.stringValue("name").orElse("");
        if (StringUtils.isEmpty(containerName)) {
            throw new IllegalArgumentException("Container name may not be empty");
        }
        String partitionKeyPath = cosmosContainerDefData.stringValue("partitionKeyPath").orElse("");
        if (StringUtils.isNotEmpty(partitionKeyPath)) {
            LOG.info("It is suggested to define partition key on the container");
        }
        int throughputRequestUnits = cosmosContainerDefData.intValue("throughputRequestUnits").orElse(0);
        ThroughputProperties throughputProperties = null;
        if (throughputRequestUnits > 0) {
            boolean throughputAutoScale = cosmosContainerDefData.booleanValue("throughputAutoScale").orElse(false);
            if (throughputAutoScale) {
                throughputProperties = ThroughputProperties.createAutoscaledThroughput(throughputRequestUnits);
            } else {
                throughputProperties = ThroughputProperties.createManualThroughput(throughputRequestUnits);
            }
        }
        return new CosmosContainerProps(containerName, partitionKeyPath, throughputProperties);
    }

    // Container related code

    private String getPartitionKey(CosmosContainerProps props) {
        if (props != null && StringUtils.isNotEmpty(props.getPartitionKeyPath())) {
            String partitionKey = props.getPartitionKeyPath();
            if (!partitionKey.startsWith("/")) {
                partitionKey = "/" + partitionKey;
            }
            return partitionKey;
        }
        return "/null";
    }

    private <T> CosmosContainer getContainer(InsertOperation<T> operation) {
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
        CosmosContainerProps props = containersByEntityMap.get(persistentEntity);
        if (props == null) {
            throw new DataAccessException("Entity is not registered in any container " + persistentEntity.getName());
        }
        return cosmosDatabase.getContainer(props.getContainerName());
    }

    private boolean isIdPartitionKey(PersistentEntity persistentEntity) {
        CosmosContainerProps props = containersByEntityMap.get(persistentEntity);
        if (StringUtils.isEmpty(props.getPartitionKeyPath())) {
            return false;
        }
        PersistentProperty identity = persistentEntity.getIdentity();
        if (identity == null) {
            return false;
        }
        return getPartitionKey(props).equals("/" + identity.getName());
    }
}
