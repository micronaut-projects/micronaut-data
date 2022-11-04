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
import com.azure.cosmos.implementation.RequestOptions;
import com.azure.cosmos.implementation.batch.ItemBulkOperation;
import com.azure.cosmos.models.CosmosItemOperation;
import com.azure.cosmos.models.CosmosItemOperationType;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedFlux;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.cosmos.common.Constants;
import io.micronaut.data.cosmos.common.CosmosAccessException;
import io.micronaut.data.cosmos.common.CosmosDatabaseInitializer;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.document.model.query.builder.CosmosSqlQueryBuilder;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.BatchOperation;
import io.micronaut.data.model.runtime.DelegatingQueryParameterBinding;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.EntityInstanceOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
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
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntitiesOperations;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntityOperations;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.data.runtime.operations.internal.sql.DefaultSqlStoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlPreparedQuery;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.http.codec.MediaTypeCodec;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The reactive Cosmos DB repository operations implementation.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Singleton
@Internal
public final class DefaultReactiveCosmosRepositoryOperations extends AbstractRepositoryOperations implements
    ReactorReactiveRepositoryOperations,
    ReactiveRepositoryOperations,
    MethodContextAwareStoredQueryDecorator,
    PreparedQueryDecorator {

    // This should return exact collection item by the id in given container
    private static final String FIND_ONE_DEFAULT_QUERY = "SELECT * FROM root WHERE root.id = @ROOT_ID";
    private static final String FAILED_TO_QUERY_ITEMS = "Failed to query items: ";

    private static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveCosmosRepositoryOperations.class);

    private final CosmosSerde cosmosSerde;
    private final CosmosAsyncDatabase cosmosAsyncDatabase;
    private final Map<String, CosmosDatabaseConfiguration.CosmosContainerSettings> cosmosContainerSettingsMap;
    private final Map<PersistentEntity, String> partitionKeyByPersistentEntity = new ConcurrentHashMap<>();
    private CosmosSqlQueryBuilder defaultCosmosSqlQueryBuilder;

    /**
     * Default constructor.
     *
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     * @param cosmosAsyncClient          The Cosmos async client
     * @param cosmosSerde                The Cosmos de/serialization helper
     * @param configuration              The Cosmos database configuration
     */
    public DefaultReactiveCosmosRepositoryOperations(List<MediaTypeCodec> codecs,
                                                     DateTimeProvider<Object> dateTimeProvider,
                                                     RuntimeEntityRegistry runtimeEntityRegistry,
                                                     DataConversionService<?> conversionService,
                                                     AttributeConverterRegistry attributeConverterRegistry,
                                                     CosmosAsyncClient cosmosAsyncClient,
                                                     CosmosSerde cosmosSerde,
                                                     CosmosDatabaseConfiguration configuration) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.cosmosSerde = cosmosSerde;
        this.cosmosAsyncDatabase = cosmosAsyncClient.getDatabase(configuration.getDatabaseName());
        this.cosmosContainerSettingsMap = CollectionUtils.isEmpty(configuration.getContainers()) ? Collections.emptyMap() :
            configuration.getContainers().stream().collect(Collectors.toMap(CosmosDatabaseConfiguration.CosmosContainerSettings::getContainerName, Function.identity()));
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return new CosmosSqlPreparedQuery<>(preparedQuery);
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        if (defaultCosmosSqlQueryBuilder == null) {
            defaultCosmosSqlQueryBuilder = new CosmosSqlQueryBuilder(context.getAnnotationMetadata());
        }
        RuntimePersistentEntity<E> runtimePersistentEntity = runtimeEntityRegistry.getEntity(storedQuery.getRootEntity());
        return new DefaultSqlStoredQuery<>(storedQuery, runtimePersistentEntity, defaultCosmosSqlQueryBuilder);
    }

    @Override
    @NonNull
    public <T> Mono<T> findOne(@NonNull Class<T> type, Serializable id) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
        CosmosAsyncContainer container = getContainer(persistentEntity);
        final SqlParameter param = new SqlParameter("@ROOT_ID", id.toString());
        final SqlQuerySpec querySpec = new SqlQuerySpec(FIND_ONE_DEFAULT_QUERY, param);
        logQuery(querySpec);
        final CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        if (isIdPartitionKey(persistentEntity)) {
            options.setPartitionKey(new PartitionKey(id.toString()));
        }
        CosmosPagedFlux<ObjectNode> result = container.queryItems(querySpec, options, ObjectNode.class);
        return result.byPage().flatMap(fluxResponse -> {
            Iterator<ObjectNode> iterator = fluxResponse.getResults().iterator();
            if (iterator.hasNext()) {
                ObjectNode item = iterator.next();
                if (iterator.hasNext()) {
                    return Flux.error(new NonUniqueResultException());
                }
                return Mono.just(cosmosSerde.deserialize(persistentEntity, item, Argument.of(type)));
            }
            return Flux.empty();
        }).onErrorResume(e ->  Flux.error(new CosmosAccessException("Failed to query item by id: " + e.getMessage(), e))).next();
    }

    @Override
    public <T> Mono<Boolean> exists(@NonNull PreparedQuery<T, Boolean> pq) {
        SqlPreparedQuery<T, Boolean> preparedQuery = getSqlPreparedQuery(pq);
        preparedQuery.attachPageable(preparedQuery.getPageable(), true);
        preparedQuery.prepare(null);
        SqlQuerySpec querySpec = new SqlQuerySpec(preparedQuery.getQuery(), new ParameterBinder().bindParameters(preparedQuery));
        logQuery(querySpec);
        CosmosPagedFlux<ObjectNode> result = getCosmosResults(preparedQuery, querySpec, ObjectNode.class);
        return result.byPage().flatMap(cosmosResponse -> Mono.just(cosmosResponse.getResults().iterator().hasNext()))
            .onErrorResume(e ->  Flux.error(new CosmosAccessException("Failed to execute exists query: " + e.getMessage(), e))).next();
    }

    @Override
    @NonNull
    public <T, R> Mono<R> findOne(@NonNull PreparedQuery<T, R> pq) {
        SqlPreparedQuery<T, R> preparedQuery = getSqlPreparedQuery(pq);
        preparedQuery.attachPageable(preparedQuery.getPageable(), true);
        preparedQuery.prepare(null);
        SqlQuerySpec querySpec = new SqlQuerySpec(preparedQuery.getQuery(), new ParameterBinder().bindParameters(preparedQuery));
        logQuery(querySpec);
        boolean dtoProjection = preparedQuery.isDtoProjection();
        boolean isEntity = preparedQuery.getResultDataType() == DataType.ENTITY;
        if (isEntity || dtoProjection) {
            return findOneEntityOrDto(preparedQuery, querySpec);
        } else {
            return findOneCustomResult(preparedQuery, querySpec);
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
    public <T, R> Flux<R> findAll(@NonNull PreparedQuery<T, R> pq) {
        SqlPreparedQuery<T, R> preparedQuery = getSqlPreparedQuery(pq);
        preparedQuery.attachPageable(preparedQuery.getPageable(), false);
        preparedQuery.prepare(null);
        boolean dtoProjection = preparedQuery.isDtoProjection();
        boolean isEntity = preparedQuery.getResultDataType() == DataType.ENTITY;
        SqlQuerySpec querySpec = new SqlQuerySpec(preparedQuery.getQuery(), new ParameterBinder().bindParameters(preparedQuery));
        logQuery(querySpec);
        if (isEntity || dtoProjection) {
            CosmosPagedFlux<ObjectNode> result = getCosmosResults(preparedQuery, querySpec, ObjectNode.class);
            Argument<R> argument;
            if (dtoProjection) {
                argument = Argument.of(ReflectionUtils.getWrapperType(preparedQuery.getResultType()));
                return result.map(item -> cosmosSerde.deserialize(item, argument)).onErrorResume(e ->  Flux.error(new CosmosAccessException(FAILED_TO_QUERY_ITEMS + e.getMessage(), e)));
            } else {
                argument = Argument.of(preparedQuery.getResultType());
                return result.map(item -> cosmosSerde.deserialize(preparedQuery.getPersistentEntity(), item, argument)).onErrorResume(e ->  Flux.error(new CosmosAccessException(FAILED_TO_QUERY_ITEMS + e.getMessage(), e)));
            }
        }
        DataType dataType = preparedQuery.getResultDataType();
        Class<R> resultType = preparedQuery.getResultType();
        CosmosPagedFlux<?> result = getCosmosResults(preparedQuery, querySpec, getDataTypeClass(dataType));
        return result.mapNotNull(item -> {
            if (resultType.isInstance(item)) {
                return (R) item;
            }
            if (item != null) {
                return conversionService.convertRequired(item, resultType);
            }
            return null;
        }).onErrorResume(e ->  Flux.error(new CosmosAccessException(FAILED_TO_QUERY_ITEMS + e.getMessage(), e)));
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
        Class<T> rootEntity = operation.getRootEntity();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(rootEntity);
        CosmosAsyncContainer container = getContainer(persistentEntity);
        CosmosReactiveOperationContext<T> ctx = new CosmosReactiveOperationContext<>(operation.getAnnotationMetadata(),
            operation.getRepositoryType(), container, rootEntity);
        CosmosReactiveEntitiesOperation<T> op = createCosmosReactiveBulkOperation(ctx, persistentEntity, operation, BulkOperationType.CREATE);
        op.persist();
        return op.getEntities();
    }

    @Override
    @NonNull
    public Mono<Number> executeUpdate(@NonNull PreparedQuery<?, Number> pq) {
        if (isRawQuery(pq)) {
            return Mono.error(new IllegalStateException("Cosmos Db does not support raw update queries."));
        }
        SqlPreparedQuery<?, Number> preparedQuery = getSqlPreparedQuery(pq);
        preparedQuery.prepare(null);
        RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        String update = preparedQuery.getAnnotationMetadata().stringValue(Query.class, "update").orElse(null);
        if (update == null) {
            LOG.warn("Could not resolve update properties for Cosmos Db entity {} and query [{}]", persistentEntity.getPersistedName(), preparedQuery.getQuery());
            return Mono.just(0);
        }
        List<String> updatePropertyList = Arrays.asList(update.split(","));
        ParameterBinder parameterBinder = new ParameterBinder(true, updatePropertyList);
        SqlQuerySpec querySpec = new SqlQuerySpec(preparedQuery.getQuery(), parameterBinder.bindParameters(preparedQuery));
        Map<String, Object> propertiesToUpdate = parameterBinder.getPropertiesToUpdate();
        if (propertiesToUpdate.isEmpty()) {
            LOG.warn("No properties found to be updated for Cosmos Db entity {} and query [{}]", persistentEntity.getPersistedName(), preparedQuery.getQuery());
            return Mono.just(0);
        }
        CosmosAsyncContainer container = getContainer(persistentEntity);
        Optional<PartitionKey> optPartitionKey = preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class);
        CosmosPagedFlux<ObjectNode> items = getCosmosResults(preparedQuery, querySpec, ObjectNode.class);
        return executeBulk(container, items, BulkOperationType.UPDATE, persistentEntity, optPartitionKey, item -> updateProperties(item, propertiesToUpdate));
    }

    @Override
    @NonNull
    public Mono<Number> executeDelete(@NonNull PreparedQuery<?, Number> pq) {
        if (isRawQuery(pq)) {
            return Mono.error(new IllegalStateException("Cosmos Db does not support raw delete queries."));
        }
        SqlPreparedQuery<?, Number> preparedQuery = getSqlPreparedQuery(pq);
        preparedQuery.prepare(null);
        RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        CosmosAsyncContainer container = getContainer(persistentEntity);
        Optional<PartitionKey> optPartitionKey = preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class);
        SqlQuerySpec querySpec = new SqlQuerySpec(preparedQuery.getQuery(), new ParameterBinder().bindParameters(preparedQuery));
        CosmosPagedFlux<ObjectNode> items = getCosmosResults(preparedQuery, querySpec, ObjectNode.class);
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
        return op.getRowsUpdated();
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

    // Query related methods

    /**
     * Gets cosmos reactive results for given prepared query.
     *
     * @param preparedQuery the prepared query
     * @param querySpec the Cosmos Sql query spec
     * @param itemsType the result iterator items type
     * @param <T> The query entity type
     * @param <R> The query result type
     * @param <I> the Cosmos iterator items type
     * @return CosmosPagedFlux with values of I type
     */
    private <T, R, I> CosmosPagedFlux<I> getCosmosResults(PreparedQuery<T, R> preparedQuery, SqlQuerySpec querySpec, Class<I> itemsType) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
        CosmosAsyncContainer container = getContainer(persistentEntity);
        CosmosQueryRequestOptions requestOptions = new CosmosQueryRequestOptions();
        preparedQuery.getParameterInRole(Constants.PARTITION_KEY_ROLE, PartitionKey.class).ifPresent(requestOptions::setPartitionKey);
        return container.queryItems(querySpec, requestOptions, itemsType);
    }

    /**
     * Finds one entity or DTO projection.
     *
     * @param preparedQuery the prepared query
     * @param querySpec the Cosmos SQL query
     * @param <T> The entity type
     * @param <R> The result type
     * @return entity or DTO projection
     */
    private <T, R> Mono<R> findOneEntityOrDto(PreparedQuery<T, R> preparedQuery, SqlQuerySpec querySpec) {
        CosmosPagedFlux<ObjectNode> result = getCosmosResults(preparedQuery, querySpec, ObjectNode.class);
        return result.byPage().flatMap(fluxResponse -> {
            Iterator<ObjectNode> iterator = fluxResponse.getResults().iterator();
            if (iterator.hasNext()) {
                ObjectNode item = iterator.next();
                if (iterator.hasNext()) {
                    return Flux.error(new NonUniqueResultException());
                }
                if (preparedQuery.isDtoProjection()) {
                    Class<R> wrapperType = ReflectionUtils.getWrapperType(preparedQuery.getResultType());
                    return Mono.just(cosmosSerde.deserialize(item, Argument.of(wrapperType)));
                }
                RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
                return Mono.just(cosmosSerde.deserialize(persistentEntity, item, Argument.of(preparedQuery.getResultType())));
            }
            return Flux.empty();
        }).onErrorResume(e ->  Flux.error(new CosmosAccessException("Failed to query item: " + e.getMessage(), e))).next();
    }

    /**
     * Finds query and returns as custom result type.
     *
     * @param preparedQuery the prepared query
     * @param querySpec the Cosmos SQL query
     * @param <T> The entity type
     * @param <R> The result type
     * @return custom result type as a result of prepared query execution
     */
    private <T, R> Mono<R> findOneCustomResult(PreparedQuery<T, R> preparedQuery, SqlQuerySpec querySpec) {
        DataType dataType = preparedQuery.getResultDataType();
        Class<R> resultType = preparedQuery.getResultType();
        CosmosPagedFlux<?> result = getCosmosResults(preparedQuery, querySpec, getDataTypeClass(dataType));
        return result.byPage().flatMap(fluxResponse -> {
            if (dataType.isArray()) {
                Collection<?> collection = CollectionUtils.iterableToList(fluxResponse.getElements());
                if (collection.isEmpty()) {
                    return Mono.just((R) collection.toArray());
                }
                return Mono.just(conversionService.convertRequired(collection, resultType));
            }
            Iterator<?> iterator = fluxResponse.getResults().iterator();
            if (iterator.hasNext()) {
                Object item = iterator.next();
                if (iterator.hasNext()) {
                    return Flux.error(new NonUniqueResultException());
                }
                if (resultType.isInstance(item)) {
                    return Mono.just((R) item);
                }
                if (item != null) {
                    return Mono.just(conversionService.convertRequired(item, resultType));
                }
            }
            return Flux.empty();
        }).onErrorResume(e -> Flux.error(new CosmosAccessException("Failed to query item: " + e.getMessage(), e))).next();
    }

    /**
     * Logs Cosmos Db SQL query being executed along with parameter values (debug level).
     *
     * @param querySpec the SQL query spec
     */
    private void logQuery(SqlQuerySpec querySpec) {
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing query: {}", querySpec.getQueryText());
            for (SqlParameter param : querySpec.getParameters()) {
                QUERY_LOG.debug("Parameter: name={}, value={}", param.getName(), param.getValue(Object.class));
            }
        }
    }

    /**
     * Gets an indicator telling whether {@link PreparedQuery} is raw query.
     *
     * @param preparedQuery the prepared query
     * @return true if prepared query is created from raw query
     */
    private boolean isRawQuery(@NonNull PreparedQuery<?, ?> preparedQuery) {
        return preparedQuery.getAnnotationMetadata().stringValue(Query.class, DataMethod.META_MEMBER_RAW_QUERY).isPresent();
    }

    private <E, R> SqlPreparedQuery<E, R> getSqlPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        if (preparedQuery instanceof SqlPreparedQuery) {
            return (SqlPreparedQuery<E, R>) preparedQuery;
        }
        throw new IllegalStateException("Expected for prepared query to be of type: SqlPreparedQuery got: " + preparedQuery.getClass().getName());
    }

    // Container util methods

    /**
     * Gets the async container for given persistent entity. It is expected that at this point container is created.
     *
     * @param persistentEntity the persistent entity (to be persisted in container)
     * @return the Cosmos async container
     */
    private CosmosAsyncContainer getContainer(RuntimePersistentEntity<?> persistentEntity) {
        return cosmosAsyncDatabase.getContainer(persistentEntity.getPersistedName());
    }

    /**
     * Gets the async container for given operation.
     *
     * @param operation the entity instance operation
     * @param <T> the entity type
     * @return the Cosmos async container for given entity operation
     */
    private <T> CosmosAsyncContainer getContainer(EntityInstanceOperation<T> operation) {
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
        return getContainer(persistentEntity);
    }

    // Partition key logic

    /**
     * Gets an indicator telling whether persistent entity identity field matches with the container partition key for that entity.
     *
     * @param persistentEntity persistent entity
     * @return true if persistent entity identity field matches with the container partition key for that entity
     */
    private boolean isIdPartitionKey(PersistentEntity persistentEntity) {
        PersistentProperty identity = persistentEntity.getIdentity();
        if (identity == null) {
            return false;
        }
        String partitionKey = getPartitionKeyDefinition(persistentEntity);
        return partitionKey.equals(Constants.PARTITION_KEY_SEPARATOR + identity.getName());
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
        return partitionKeyByPersistentEntity.computeIfAbsent(persistentEntity, this::doGetPartitionKeyDefinition);
    }

    private String doGetPartitionKeyDefinition(PersistentEntity persistentEntity) {
        CosmosDatabaseConfiguration.CosmosContainerSettings cosmosContainerSettings = cosmosContainerSettingsMap.get(persistentEntity.getPersistedName());
        return CosmosDatabaseInitializer.getPartitionKeyDefinition(persistentEntity, cosmosContainerSettings);
    }

    /**
     * Gets partition key for a document. Partition keys can be only string or number values.
     *
     * @param persistentEntity the persistent entity
     * @param item item from the Cosmos Db
     * @return partition key, if partition key defined and value set otherwise null
     */
    @Nullable
    private PartitionKey getPartitionKey(RuntimePersistentEntity<?> persistentEntity, ObjectNode item) {
        String partitionKeyDefinition = getPartitionKeyDefinition(persistentEntity);
        if (partitionKeyDefinition.startsWith(Constants.PARTITION_KEY_SEPARATOR)) {
            partitionKeyDefinition = partitionKeyDefinition.substring(1);
        }
        return getPartitionKey(partitionKeyDefinition, item);
    }

    /**
     * Gets partition key for a document. Partition keys can be only string or number values.
     * TODO: Later deal with nested paths when we support it.
     *
     * @param partitionKeyField the partition key field without "/" at the beginning
     * @param item item from the Cosmos Db
     * @return partition key, if partition key defined and value set otherwise null
     */
    @Nullable
    private PartitionKey getPartitionKey(String partitionKeyField, ObjectNode item) {
        com.fasterxml.jackson.databind.JsonNode jsonNode = item.get(partitionKeyField);
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
     * Gets the id from {@link ObjectNode} document in Cosmos Db. Can return null if document ({@link ObjectNode} not yet persisted.
     *
     * @param item the item/document in the db
     * @return document id
     */
    private String getItemId(ObjectNode item) {
        com.fasterxml.jackson.databind.JsonNode idNode = item.get(Constants.INTERNAL_ID);
        if (idNode == null) {
            return null;
        }
        return idNode.textValue();
    }

    /**
     * Gets underlying java class for the {@link DataType}.
     *
     * @param dataType the data type
     * @return java class for the data type
     */
    private Class<?> getDataTypeClass(DataType dataType) {
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

    // Create, update, delete

    /**
     * If entity has auto generated id, we can generate only String and UUID.
     *
     * @param persistentEntity the persistent entity
     * @param entity the entity
     * @param <T> the entity type
     */
    private <T> void generateId(RuntimePersistentEntity<T> persistentEntity, T entity) {
        RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
        if (identity.getProperty().get(entity) == null) {
            if (identity.getDataType().equals(DataType.STRING)) {
                identity.getProperty().convertAndSet(entity, UUID.randomUUID().toString());
            }
            if (identity.getDataType().equals(DataType.UUID)) {
                identity.getProperty().convertAndSet(entity, UUID.randomUUID());
            }
        }
    }

    /**
     * Updates existing {@link ObjectNode} item with given property values.
     *
     * @param item the {@link ObjectNode} item to be updated
     * @param propertiesToUpdate map with property keys and values to update
     * @return updated {@link ObjectNode} with new values
     */
    private ObjectNode updateProperties(ObjectNode item, Map<String, Object> propertiesToUpdate) {
        // iterate through properties, update and replace item
        for (Map.Entry<String, Object> propertyToUpdate : propertiesToUpdate.entrySet()) {
            String property = propertyToUpdate.getKey();
            Object value = propertyToUpdate.getValue();
            com.fasterxml.jackson.databind.JsonNode objectNode;
            if (value == null) {
                objectNode = NullNode.getInstance();
            } else {
                objectNode = cosmosSerde.serialize(value, Argument.of(value.getClass()));
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
     * @param handleItem function that will apply some changes before adding item to the list, if null then ignored
     * @return list of {@link CosmosItemOperation}s
     */
    private List<CosmosItemOperation> createBulkOperations(Iterable<ObjectNode> items, BulkOperationType bulkOperationType, RuntimePersistentEntity<?> persistentEntity,
                                                           Optional<PartitionKey> optPartitionKey, UnaryOperator<ObjectNode> handleItem) {
        List<CosmosItemOperation> bulkOperations = new ArrayList<>();
        RequestOptions requestOptions = new RequestOptions();
        String partitionKeyDefinition = getPartitionKeyDefinition(persistentEntity);
        if (partitionKeyDefinition.startsWith(Constants.PARTITION_KEY_SEPARATOR)) {
            partitionKeyDefinition = partitionKeyDefinition.substring(1);
        }
        final String partitionKeyField = partitionKeyDefinition;
        for (ObjectNode item : items) {
            if (handleItem != null) {
                item = handleItem.apply(item);
            }
            String id = getItemId(item);
            ObjectNode finalItem = item;
            PartitionKey partitionKey = optPartitionKey.orElseGet(() -> getPartitionKey(partitionKeyField, finalItem));
            bulkOperations.add(new ItemBulkOperation<>(bulkOperationType.cosmosItemOperationType, id, partitionKey, requestOptions, item, null));
        }
        return bulkOperations;
    }

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
        Flux<CosmosItemOperation> updateItems = items.byPage().flatMap(itemsMap -> {
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
                        generateId(persistentEntity, d.entity);
                    }
                    ObjectNode item = cosmosSerde.serialize(persistentEntity, d.entity, Argument.of(ctx.getRootEntity()));
                    PartitionKey partitionKey = getPartitionKey(persistentEntity, item);
                    return Mono.from(container.createItem(item, partitionKey, new CosmosItemRequestOptions())).map(insertOneResult -> d);
                });
            }
        };
    }

    private <T> CosmosReactiveEntityOperation<T> createCosmosReactiveReplaceItemOperation(CosmosReactiveOperationContext<T> ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new CosmosReactiveEntityOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, entity, false) {

            @Override
            protected void execute() throws RuntimeException {
                CosmosAsyncContainer container = ctx.getContainer();
                data = data.flatMap(d -> {
                    ObjectNode item = cosmosSerde.serialize(persistentEntity, d.entity, Argument.of(ctx.getRootEntity()));
                    PartitionKey partitionKey = getPartitionKey(persistentEntity, item);
                    String id = getItemId(item);
                    Mono<CosmosItemResponse<ObjectNode>> response = container.replaceItem(item, id, partitionKey, new CosmosItemRequestOptions());
                    return Mono.from(response).map(updateOneResult -> {
                        if (updateOneResult.getStatusCode() != HttpResponseStatus.OK.code()) {
                            LOG.debug("Failed to update entity with id {} in container {}", id, container.getId());
                        }
                        return d;
                    });
                });
            }

        };
    }

    private <T> CosmosReactiveEntityOperation<T> createCosmosReactiveDeleteOneOperation(CosmosReactiveOperationContext<T> ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new CosmosReactiveEntityOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, entity, false) {

            @Override
            protected void execute() throws RuntimeException {
                CosmosAsyncContainer container = ctx.getContainer();
                data = data.flatMap(d -> {
                    ObjectNode item = cosmosSerde.serialize(persistentEntity, d.entity, Argument.of(ctx.getRootEntity()));
                    CosmosItemRequestOptions options = new CosmosItemRequestOptions();
                    String id = getItemId(item);
                    PartitionKey partitionKey = getPartitionKey(persistentEntity, item);
                    Mono<CosmosItemResponse<Object>> response = container.deleteItem(id, partitionKey, options);
                    return Mono.from(response).map(deleteOneResult -> {
                        if (deleteOneResult.getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
                            d.rowsUpdated = 1;
                        } else {
                            d.rowsUpdated = 0;
                        }
                        return d;
                    });
                });
            }
        };
    }

    private <T> CosmosReactiveEntitiesOperation<T> createCosmosReactiveBulkOperation(CosmosReactiveOperationContext<T> ctx,
                                                                                     RuntimePersistentEntity<T> persistentEntity,
                                                                                     BatchOperation<T> operation,
                                                                                     BulkOperationType operationType) {
        return new CosmosReactiveEntitiesOperation<T>(entityEventRegistry, conversionService, ctx, persistentEntity, operation,
            BulkOperationType.CREATE.equals(operationType)) {

            @Override
            protected void execute() throws RuntimeException {
                Argument<T> arg = Argument.of(ctx.getRootEntity());
                RequestOptions requestOptions = new RequestOptions();

                String partitionKeyDefinition = getPartitionKeyDefinition(persistentEntity);
                if (partitionKeyDefinition.startsWith(Constants.PARTITION_KEY_SEPARATOR)) {
                    partitionKeyDefinition = partitionKeyDefinition.substring(1);
                }
                final String partitionKeyField = partitionKeyDefinition;
                boolean generateId = hasGeneratedId && BulkOperationType.CREATE.equals(operationType);

                // Update/replace using partition key calculated from each item
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.collectList()
                    .flatMap(e -> {
                        List<ItemBulkOperation<?, ?>> notVetoedEntities = e.stream().filter(this::notVetoed).map(x -> {
                            if (generateId) {
                                generateId(persistentEntity, x.entity);
                            }
                            ObjectNode item = cosmosSerde.serialize(persistentEntity, x.entity, arg);
                            String id = getItemId(item);
                            PartitionKey partitionKey = getPartitionKey(partitionKeyField, item);
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

    // Helper classes, enums

    /**
     * Custom class used for binding parameters for Cosmos sql queries.
     * Needed to be able to extract update parameters for update actions, so we can call replace API.
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

        /**
         * Returns list of {@link SqlParameter} after binding parameters for {@link PreparedQuery}.
         *
         * @param preparedQuery the prepared query
         * @param <T> The entity type of prepared query
         * @param <R> The result type of prepared query
         * @return SqlParameter list to be used in Azure Cosmos SQL query
         */
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
                    // Query params were expanded, so we must expand parameters and bind query with newly created parameters
                    String parameterName = getParameterName(binding, isRawQuery);
                    int index = 1;
                    for (Object value : values) {
                        final String expandedParameterName = String.format("%s_%d", parameterName, index++);
                        QueryParameterBinding newBinding = new DelegatingQueryParameterBinding(binding) {
                            @Override
                            @NonNull
                            public String getRequiredName() {
                                return expandedParameterName;
                            }
                        };
                        bindOne(newBinding, value);
                    }
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
    }

    /**
     * The bulk operation type used when creating bulk operations against Cosmos Db.
     * Need to know what type (supported CREATE, DELETE and REPLACE) and what expected status code
     * for each item is to be treated as successful.
     */
    private enum BulkOperationType {

        CREATE(CosmosItemOperationType.CREATE, HttpResponseStatus.CREATED.code()),
        DELETE(CosmosItemOperationType.DELETE, HttpResponseStatus.NO_CONTENT.code()),
        UPDATE(CosmosItemOperationType.REPLACE, HttpResponseStatus.OK.code());

        final CosmosItemOperationType cosmosItemOperationType;
        final int expectedOperationStatusCode;

        BulkOperationType(CosmosItemOperationType cosmosItemOperationType, int expectedOperationStatusCode) {
            this.cosmosItemOperationType = cosmosItemOperationType;
            this.expectedOperationStatusCode = expectedOperationStatusCode;
        }
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
         * @param insert              Whether the operation is inserting
         */
        protected CosmosReactiveEntitiesOperation(EntityEventListener<Object> entityEventListener,
                                                  ConversionService<?> conversionService,
                                                  CosmosReactiveOperationContext<T> ctx,
                                                  RuntimePersistentEntity<T> persistentEntity,
                                                  Iterable<T> entities,
                                                  boolean insert) {
            super(ctx, null, conversionService, entityEventListener, persistentEntity, entities, insert);
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
