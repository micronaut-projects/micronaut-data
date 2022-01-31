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
package io.micronaut.data.mongodb.operations;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.mongodb.database.ReactiveMongoDatabaseFactory;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntitiesOperations;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.data.runtime.operations.internal.ReactiveCascadeOperations;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionSystemException;
import io.micronaut.transaction.exceptions.TransactionUsageException;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import io.micronaut.transaction.reactive.ReactiveTransactionStatus;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The reactive MongoDB repository operations implementation.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
//@RequiresSyncMongo
@EachBean(MongoClient.class)
@Internal
public class DefaultReactiveMongoRepositoryOperations extends AbstractMongoRepositoryOperations<ClientSession, Object>
        implements MongoReactorRepositoryOperations,
        ReactorReactiveRepositoryOperations,
        ReactiveCascadeOperations.ReactiveCascadeOperationsHelper<DefaultReactiveMongoRepositoryOperations.MongoOperationContext>,
        ReactiveTransactionOperations<ClientSession> {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveMongoRepositoryOperations.class);
    private static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    private final String serverName;
    private final MongoClient mongoClient;
    private final ReactiveCascadeOperations<MongoOperationContext> cascadeOperations;
    private final ReactiveMongoDatabaseFactory mongoDatabaseFactory;

    /**
     * Default constructor.
     *
     * @param serverName                 The server name
     * @param beanContext                The bean context
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     * @param mongoClient                The reactive mongo client
     */
    protected DefaultReactiveMongoRepositoryOperations(@Parameter String serverName, BeanContext beanContext, List<MediaTypeCodec> codecs, DateTimeProvider<Object> dateTimeProvider, RuntimeEntityRegistry runtimeEntityRegistry, DataConversionService<?> conversionService, AttributeConverterRegistry attributeConverterRegistry, MongoClient mongoClient) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.serverName = serverName;
        this.mongoClient = mongoClient;
        this.cascadeOperations = new ReactiveCascadeOperations<>(conversionService, this);
        boolean isPrimary = "Primary".equals(serverName);
        this.mongoDatabaseFactory = beanContext.getBean(ReactiveMongoDatabaseFactory.class, isPrimary ? null : Qualifiers.byName(serverName));
    }

    @Override
    public <T> Mono<T> findOne(Class<T> type, Serializable id) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
            MongoDatabase database = getDatabase(persistentEntity);
            MongoCollection<T> collection = getCollection(database, persistentEntity, type);
            Bson filter = MongoUtils.filterById(conversionService, persistentEntity, id, collection.getCodecRegistry());
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'find' with filter: {}", filter.toBsonDocument().toJson());
            }
            return Mono.from(collection.find(clientSession, filter, type).first());
        });
    }

    @Override
    public <T, R> Mono<R> findOne(PreparedQuery<T, R> preparedQuery) {
        return withClientSession(clientSession -> {
            Class<T> type = preparedQuery.getRootEntity();
            Class<R> resultType = preparedQuery.getResultType();
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
            MongoDatabase database = getDatabase(persistentEntity);
            FetchOptions fetchOptions = getFetchOptions(database.getCodecRegistry(), preparedQuery, persistentEntity);
            if (isCountQuery(preparedQuery)) {
                return getCount(clientSession, database, type, resultType, persistentEntity, fetchOptions);
            }
            if (fetchOptions.pipeline == null) {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'find' with filter: {}", fetchOptions.filter.toBsonDocument().toJson());
                }
                return Mono.from(getCollection(database, persistentEntity, resultType).find(clientSession, fetchOptions.filter, resultType).limit(1).first()).map(r -> {
                    if (type.isInstance(r)) {
                        return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
                    }
                    return r;
                });
            } else {
                return findOneAggregated(clientSession, preparedQuery, type, resultType, persistentEntity, database, fetchOptions.pipeline);
            }
        });
    }

    @Override
    public <T> Mono<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery) {
        return withClientSession(clientSession -> {
            Class<T> type = preparedQuery.getRootEntity();
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
            MongoDatabase database = getDatabase(persistentEntity);
            FetchOptions fetchOptions = getFetchOptions(database.getCodecRegistry(), preparedQuery, persistentEntity);
            if (fetchOptions.filter == null) {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", fetchOptions.pipeline.stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
                }
                return Flux.from(getCollection(database, persistentEntity, persistentEntity.getIntrospection().getBeanType()).aggregate(clientSession, fetchOptions.pipeline)).hasElements();
            } else {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing exists Mongo 'find' with filter: {}", fetchOptions.filter.toBsonDocument().toJson());
                }
                return Flux.from(getCollection(database, persistentEntity, persistentEntity.getIntrospection().getBeanType()).find(clientSession, type).limit(1).filter(fetchOptions.filter)).hasElements();
            }
        });
    }

    @Override
    public <T> Flux<T> findAll(PagedQuery<T> query) {
        throw new DataAccessException("Not supported!");
    }

    @Override
    public <T> Mono<Long> count(PagedQuery<T> pagedQuery) {
        throw new DataAccessException("Not supported!");
    }

    @Override
    public <T, R> Flux<R> findAll(PreparedQuery<T, R> preparedQuery) {
        return withClientSessionMany(clientSession -> findAll(clientSession, preparedQuery, false));
    }

    @Override
    public <T> Mono<T> findOptional(Class<T> type, Serializable id) {
        return findOne(type, id);
    }

    @Override
    public <T, R> Mono<R> findOptional(PreparedQuery<T, R> preparedQuery) {
        return findOne(preparedQuery);
    }

    @Override
    public <R> Mono<Page<R>> findPage(PagedQuery<R> pagedQuery) {
        throw new DataAccessException("Not supported!");
    }

    @Override
    public <T> Mono<T> persist(InsertOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getRepositoryType(), operation.getAnnotationMetadata());
            return persistOne(ctx, operation.getEntity(), runtimeEntityRegistry.getEntity(operation.getRootEntity()));
        });
    }

    @Override
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        return withClientSessionMany(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getRepositoryType(), operation.getAnnotationMetadata());
            return persistBatch(ctx, operation, runtimeEntityRegistry.getEntity(operation.getRootEntity()), null);
        });
    }

    @Override
    public <T> Mono<T> update(UpdateOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getRepositoryType(), operation.getAnnotationMetadata());
            return updateOne(ctx, operation.getEntity(), runtimeEntityRegistry.getEntity(operation.getRootEntity()));
        });
    }

    @Override
    public <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        return withClientSessionMany(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getRepositoryType(), operation.getAnnotationMetadata());
            return updateBatch(ctx, operation, runtimeEntityRegistry.getEntity(operation.getRootEntity()));
        });
    }

    @Override
    public <T> Mono<Number> delete(DeleteOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getRepositoryType(), operation.getAnnotationMetadata());
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            MongoReactiveEntityOperation<T> op = createMongoDbDeleteOneOperation(ctx, persistentEntity, operation.getEntity());
            op.delete();
            return op.getRowsUpdated();
        });
    }

    @Override
    public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getRepositoryType(), operation.getAnnotationMetadata());
            if (operation.all()) {
                MongoDatabase mongoDatabase = getDatabase(persistentEntity);
                return Mono.from(getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType()).deleteMany(EMPTY)).map(DeleteResult::getDeletedCount);
            }
            MongoReactiveEntitiesOperation<T> op = createMongoDeleteManyOperation(ctx, persistentEntity, operation);
            op.delete();
            return op.getRowsUpdated();
        });
    }

    @Override
    public Mono<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
            MongoDatabase database = getDatabase(persistentEntity);
            UpdateOptions updateOptions = getUpdateOptions(database.getCodecRegistry(), preparedQuery, persistentEntity);
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'updateMany' with filter: {} and update: {}", updateOptions.filter.toBsonDocument().toJson(), updateOptions.update.toBsonDocument().toJson());
            }
            return Mono.from(getCollection(database, persistentEntity, persistentEntity.getIntrospection().getBeanType()).updateMany(clientSession, updateOptions.filter, updateOptions.update)).map(updateResult -> {
                if (preparedQuery.isOptimisticLock()) {
                    checkOptimisticLocking(1, (int) updateResult.getModifiedCount());
                }
                return updateResult.getModifiedCount();
            });
        });
    }

    @Override
    public Mono<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
            MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            Bson filter = getFilter(mongoDatabase.getCodecRegistry(), preparedQuery, persistentEntity);
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'deleteMany' with filter: {}", filter.toBsonDocument().toJson());
            }
            return Mono.from(getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType()).deleteMany(clientSession, filter)).map(deleteResult -> {
                if (preparedQuery.isOptimisticLock()) {
                    checkOptimisticLocking(1, (int) deleteResult.getDeletedCount());
                }
                return deleteResult.getDeletedCount();
            });
        });
    }

    private <T, R> Flux<R> findAll(ClientSession clientSession, PreparedQuery<T, R> preparedQuery, boolean stream) {
        Pageable pageable = preparedQuery.getPageable();

        Class<T> type = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
        MongoDatabase database = getDatabase(persistentEntity);

        FetchOptions fetchOptions = getFetchOptions(database.getCodecRegistry(), preparedQuery, persistentEntity);
        if (isCountQuery(preparedQuery)) {
            return getCount(clientSession, database, type, resultType, persistentEntity, fetchOptions).flux();
        }
        if (fetchOptions.pipeline == null) {
            return findAll(clientSession, database, pageable, resultType, persistentEntity, fetchOptions.filter, stream);
        }
        return findAllAggregated(clientSession, database, pageable, resultType, preparedQuery.isDtoProjection(), persistentEntity, fetchOptions.pipeline, stream);
    }

    private <T, R> Mono<R> getCount(ClientSession clientSession, MongoDatabase mongoDatabase, Class<T> type, Class<R> resultType, RuntimePersistentEntity<T> persistentEntity, FetchOptions fetchOptions) {
        if (fetchOptions.pipeline == null) {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'countDocuments' with filter: {}", fetchOptions.filter.toBsonDocument().toJson());
            }
            return Mono.from(getCollection(mongoDatabase, persistentEntity, BsonDocument.class).countDocuments(clientSession, fetchOptions.filter)).map(count -> conversionService.convertRequired(count, resultType));
        } else {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", fetchOptions.pipeline.stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
            }
            return Mono.from(getCollection(mongoDatabase, persistentEntity, type).aggregate(clientSession, fetchOptions.pipeline, BsonDocument.class).first()).map(bsonDocument -> convertResult(mongoDatabase.getCodecRegistry(), resultType, bsonDocument, false)).switchIfEmpty(Mono.defer(() -> Mono.just(conversionService.convertRequired(0, resultType))));
        }
    }

    private <T, R> Mono<R> findOneAggregated(ClientSession clientSession, PreparedQuery<T, R> preparedQuery, Class<T> type, Class<R> resultType, RuntimePersistentEntity<T> persistentEntity, MongoDatabase database, List<BsonDocument> pipeline) {
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", pipeline.stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
        }
        boolean isProjection = pipeline.stream().anyMatch(stage -> stage.containsKey("$group") || stage.containsKey("$project"));
        if (isProjection) {
            return Mono.from(getCollection(database, persistentEntity, BsonDocument.class).aggregate(clientSession, pipeline, BsonDocument.class).first()).map(bsonDocument -> convertResult(database.getCodecRegistry(), resultType, bsonDocument, preparedQuery.isDtoProjection()));
        }
        return Mono.from(getCollection(database, persistentEntity, resultType).aggregate(clientSession, pipeline).first()).map(r -> {
            if (type.isInstance(r)) {
                return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
            }
            return r;
        });
    }

    private <T, R> Flux<R> findAllAggregated(ClientSession clientSession, MongoDatabase database, Pageable pageable, Class<R> resultType, boolean isDtoProjection, RuntimePersistentEntity<T> persistentEntity, List<BsonDocument> pipeline, boolean stream) {
        applyPageable(pageable, pipeline);
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", pipeline.stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
        }
        boolean isProjection = pipeline.stream().anyMatch(stage -> stage.containsKey("$group") || stage.containsKey("$project"));
        Flux<R> aggregate;
        if (isProjection) {
            aggregate = Flux.from(getCollection(database, persistentEntity, BsonDocument.class).aggregate(clientSession, pipeline, BsonDocument.class)).map(result -> convertResult(database.getCodecRegistry(), resultType, result, isDtoProjection));
        } else {
            aggregate = Flux.from(getCollection(database, persistentEntity, resultType).aggregate(clientSession, pipeline, resultType));
        }
        return aggregate;
    }

    private <T, R> Flux<R> findAll(ClientSession clientSession, MongoDatabase database, Pageable pageable, Class<R> resultType, RuntimePersistentEntity<T> persistentEntity, Bson filter, boolean stream) {
        Bson sort = null;
        int skip = 0;
        int limit = 0;
        if (pageable != Pageable.UNPAGED) {
            skip = (int) pageable.getOffset();
            limit = pageable.getSize();
            Sort pageableSort = pageable.getSort();
            if (pageableSort.isSorted()) {
                sort = pageableSort.getOrderBy().stream().map(order -> order.isAscending() ? Sorts.ascending(order.getProperty()) : Sorts.descending(order.getProperty())).collect(Collectors.collectingAndThen(Collectors.toList(), Sorts::orderBy));
            }
        }
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'find' with filter: {} skip: {} limit: {}", filter.toBsonDocument().toJson(), skip, limit);
        }
        return Flux.from(getCollection(database, persistentEntity, resultType).find(clientSession, filter, resultType).skip(skip).limit(Math.max(limit, 0)).sort(sort));
    }

    private <K> K triggerPostLoad(AnnotationMetadata annotationMetadata, RuntimePersistentEntity<K> persistentEntity, K entity) {
        if (persistentEntity.hasPostLoadEventListeners()) {
            entity = triggerPostLoad(entity, persistentEntity, annotationMetadata);
        }
        for (PersistentProperty pp : persistentEntity.getPersistentProperties()) {
            if (pp instanceof RuntimeAssociation) {
                RuntimeAssociation runtimeAssociation = (RuntimeAssociation) pp;
                Object o = runtimeAssociation.getProperty().get(entity);
                if (o == null) {
                    continue;
                }
                RuntimePersistentEntity associatedEntity = runtimeAssociation.getAssociatedEntity();
                switch (runtimeAssociation.getKind()) {
                    case MANY_TO_MANY:
                    case ONE_TO_MANY:
                        if (o instanceof Iterable) {
                            for (Object value : ((Iterable) o)) {
                                triggerPostLoad(value, associatedEntity, annotationMetadata);
                            }
                        }
                        continue;
                    case MANY_TO_ONE:
                    case ONE_TO_ONE:
                    case EMBEDDED:
                        triggerPostLoad(o, associatedEntity, annotationMetadata);
                        continue;
                    default:
                        throw new IllegalStateException("Unknown kind: " + runtimeAssociation.getKind());
                }
            }
        }
        return entity;
    }

    @Override
    protected ConversionContext createTypeConversionContext(ClientSession connection, RuntimePersistentProperty<?> property, Argument<?> argument) {
        return null;
    }

    @Override
    public void setStatementParameter(Object preparedStatement, int index, DataType dataType, Object value, Dialect dialect) {

    }

    private <T> MongoCollection<T> getCollection(RuntimePersistentEntity<T> persistentEntity) {
        return getDatabase(persistentEntity).getCollection(persistentEntity.getPersistedName(), persistentEntity.getIntrospection().getBeanType());
    }

    @Override
    public <T> Mono<T> persistOne(MongoOperationContext ctx, T value, RuntimePersistentEntity<T> persistentEntity) {
        MongoReactiveEntityOperation<T> op = createMongoDbInsertOneOperation(ctx, persistentEntity, value);
        op.persist();
        return op.getEntity();
    }

    @Override
    public <T> Flux<T> persistBatch(MongoOperationContext ctx, Iterable<T> values, RuntimePersistentEntity<T> persistentEntity, Predicate<T> predicate) {
        MongoReactiveEntitiesOperation<T> op = createMongoInsertManyOperation(ctx, persistentEntity, values);
        if (predicate != null) {
            op.veto(predicate);
        }
        op.persist();
        return op.getEntities();
    }

    @Override
    public <T> Mono<T> updateOne(MongoOperationContext ctx, T value, RuntimePersistentEntity<T> persistentEntity) {
        MongoReactiveEntityOperation<T> op = createMongoDbReplaceOneOperation(ctx, persistentEntity, value);
        op.update();
        return op.getEntity();
    }

    private <T> Flux<T> updateBatch(MongoOperationContext ctx, Iterable<T> values, RuntimePersistentEntity<T> persistentEntity) {
        MongoReactiveEntitiesOperation<T> op = createMongoReplaceManyOperation(ctx, persistentEntity, values);
        op.update();
        return op.getEntities();
    }

    private <T, R> MongoCollection<R> getCollection(MongoDatabase database, RuntimePersistentEntity<T> persistentEntity, Class<R> resultType) {
        return database.getCollection(persistentEntity.getPersistedName(), resultType);
    }

    private MongoDatabase getDatabase(PersistentEntity persistentEntity) {
        return mongoDatabaseFactory.getDatabase(persistentEntity);
    }

    @Override
    public Mono<Void> persistManyAssociation(MongoOperationContext ctx, RuntimeAssociation runtimeAssociation, Object value, RuntimePersistentEntity<Object> persistentEntity, Object child, RuntimePersistentEntity<Object> childPersistentEntity) {
        String joinCollectionName = runtimeAssociation.getOwner().getNamingStrategy().mappedName(runtimeAssociation);
        MongoDatabase mongoDatabase = getDatabase(persistentEntity);
        MongoCollection<BsonDocument> collection = mongoDatabase.getCollection(joinCollectionName, BsonDocument.class);
        BsonDocument association = association(collection.getCodecRegistry(), value, persistentEntity, child, childPersistentEntity);
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'insertOne' for collection: {} with document: {}", collection.getNamespace().getFullName(), association);
        }
        return Mono.from(collection.insertOne(ctx.clientSession, association)).then();
    }

    @Override
    public Mono<Void> persistManyAssociationBatch(MongoOperationContext ctx, RuntimeAssociation runtimeAssociation, Object value, RuntimePersistentEntity<Object> persistentEntity, Iterable<Object> child, RuntimePersistentEntity<Object> childPersistentEntity, Predicate<Object> veto) {
        String joinCollectionName = runtimeAssociation.getOwner().getNamingStrategy().mappedName(runtimeAssociation);
        MongoCollection<BsonDocument> collection = getDatabase(persistentEntity).getCollection(joinCollectionName, BsonDocument.class);
        List<BsonDocument> associations = new ArrayList<>();
        for (Object c : child) {
            associations.add(association(collection.getCodecRegistry(), value, persistentEntity, c, childPersistentEntity));
        }
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'insertMany' for collection: {} with associations: {}", collection.getNamespace().getFullName(), associations);
        }
        return Mono.from(collection.insertMany(ctx.clientSession, associations)).then();
    }

    @Override
    public <T> Mono<T> withClientSession(Function<ClientSession, Mono<? extends T>> function) {
        Objects.requireNonNull(function, "Handler cannot be null");
        return Mono.deferContextual(contextView -> {
            ClientSession clientSession = contextView.getOrDefault(MongoReactorRepositoryOperations.CLIENT_SESSION_CONTEXT_KEY, null);
            if (clientSession != null) {
                LOG.debug("Reusing client session for MongoDB configuration: " + serverName);
                return function.apply(clientSession);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating a new client session for MongoDB configuration: " + serverName);
            }
            return Mono.usingWhen(mongoClient.startSession(), cs -> function.apply(cs).contextWrite(ctx -> ctx.put(CLIENT_SESSION_CONTEXT_KEY, cs)), connection -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closing Connection for MongoDB configuration: " + serverName);
                }
                connection.close();
                return Mono.empty();
            });
        });
    }

    @Override
    public <T> Flux<T> withClientSessionMany(Function<ClientSession, Flux<? extends T>> function) {
        Objects.requireNonNull(function, "Handler cannot be null");
        return Flux.deferContextual(contextView -> {
            ClientSession clientSession = contextView.getOrDefault(MongoReactorRepositoryOperations.CLIENT_SESSION_CONTEXT_KEY, null);
            if (clientSession != null) {
                return Flux.from(function.apply(clientSession));
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating a new client session for MongoDB configuration: " + serverName);
            }
            return Flux.usingWhen(mongoClient.startSession(),
                    cs -> function.apply(cs).contextWrite(ctx -> ctx.put(CLIENT_SESSION_CONTEXT_KEY, cs)),
                    connection -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closing Connection for MongoDB configuration: " + serverName);
                }
                connection.close();
                return Mono.empty();
            });
        });
    }

    private <T> MongoReactiveEntityOperation<T> createMongoDbInsertOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoReactiveEntityOperation<T>(ctx, persistentEntity, entity, true) {

            @Override
            protected void execute() throws RuntimeException {
                MongoDatabase mongoDatabase = getDatabase(persistentEntity);
                MongoCollection<T> collection = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType());

                data = data.flatMap(d -> {
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'insertOne' with entity: {}", d.entity);
                    }
                    return Mono.from(collection.insertOne(ctx.clientSession, d.entity)).map(insertOneResult -> {
                        BsonValue insertedId = insertOneResult.getInsertedId();
                        BeanProperty<T, Object> property = (BeanProperty<T, Object>) persistentEntity.getIdentity().getProperty();
                        if (property.get(d.entity) == null) {
                            d.entity = updateEntityId(property, d.entity, insertedId);
                        }
                        return d;
                    });
                });
            }
        };
    }

    private <T> MongoReactiveEntityOperation<T> createMongoDbReplaceOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoReactiveEntityOperation<T>(ctx, persistentEntity, entity, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            final MongoCollection<BsonDocument> collection = getCollection(mongoDatabase, persistentEntity, BsonDocument.class);

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                data = data.map(d -> {
                    d.filter = MongoUtils.filterByIdAndVersion(conversionService, persistentEntity, d.entity, collection.getCodecRegistry());
                    return d;
                });
            }

            @Override
            protected void execute() throws RuntimeException {
                data = data.flatMap(d -> {
                    Bson filter = (Bson) d.filter;
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'replaceOne' with filter: {}", filter.toBsonDocument().toJson());
                    }
                    BsonDocument bsonDocument = BsonDocumentWrapper.asBsonDocument(d.entity, mongoDatabase.getCodecRegistry());
                    bsonDocument.remove("_id");
                    return Mono.from(collection.replaceOne(ctx.clientSession, filter, bsonDocument)).map(updateResult -> {
                        d.rowsUpdated = updateResult.getModifiedCount();
                        if (persistentEntity.getVersion() != null) {
                            checkOptimisticLocking(1, (int) d.rowsUpdated);
                        }
                        return d;
                    });
                });

            }

        };
    }

    private <T> MongoReactiveEntitiesOperation<T> createMongoReplaceManyOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoReactiveEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            final MongoCollection<BsonDocument> collection = getCollection(mongoDatabase, persistentEntity, BsonDocument.class);

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                entities = entities.map(d -> {
                    if (d.vetoed) {
                        return d;
                    }
                    d.filter = MongoUtils.filterByIdAndVersion(conversionService, persistentEntity, d.entity, collection.getCodecRegistry());
                    return d;
                });
            }

            @Override
            protected void execute() throws RuntimeException {
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.collectList().flatMap(data -> {
                    Mono<Long> modifiedCount = Mono.just(0L);
                    int expectedToBeUpdated = 0;
                    for (Data d : data) {
                        if (d.vetoed) {
                            continue;
                        }
                        expectedToBeUpdated++;
                        Bson filter = (Bson) d.filter;
                        if (QUERY_LOG.isDebugEnabled()) {
                            QUERY_LOG.debug("Executing Mongo 'replaceOne' with filter: {}", filter.toBsonDocument().toJson());
                        }
                        BsonDocument bsonDocument = BsonDocumentWrapper.asBsonDocument(d.entity, mongoDatabase.getCodecRegistry());
                        bsonDocument.remove("_id");
                        modifiedCount = modifiedCount.flatMap(count -> Mono.from(collection.replaceOne(ctx.clientSession, filter, bsonDocument)).map(updateResult -> count + updateResult.getModifiedCount()));
                    }
                    int finalExpectedToBeUpdated = expectedToBeUpdated;
                    modifiedCount = modifiedCount.map(count -> {
                        if (persistentEntity.getVersion() != null) {
                            checkOptimisticLocking(finalExpectedToBeUpdated, count);
                        }
                        return count;
                    });
                    return modifiedCount.map(count -> Tuples.of(data, count));
                }).cache();
                entities = entitiesWithRowsUpdated.flatMapMany(t -> Flux.fromIterable(t.getT1()));
                rowsUpdated = entitiesWithRowsUpdated.map(Tuple2::getT2);
            }
        };
    }

    private <T> MongoReactiveEntityOperation<T> createMongoDbDeleteOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoReactiveEntityOperation<T>(ctx, persistentEntity, entity, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            final MongoCollection<T> collection = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType());

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                data = data.map(d -> {
                    if (d.vetoed) {
                        return d;
                    }
                    d.filter = MongoUtils.filterByIdAndVersion(conversionService, persistentEntity, d.entity, collection.getCodecRegistry());
                    return d;
                });
            }

            @Override
            protected void execute() throws RuntimeException {
                data = data.flatMap(d -> {
                    Bson filter = (Bson) d.filter;
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'deleteOne' with filter: {}", filter.toBsonDocument().toJson());
                    }
                    return Mono.from(getCollection(persistentEntity).deleteOne(ctx.clientSession, filter)).map(deleteResult -> {
                        d.rowsUpdated = (int) deleteResult.getDeletedCount();
                        if (persistentEntity.getVersion() != null) {
                            checkOptimisticLocking(1, d.rowsUpdated);
                        }
                        return d;
                    });
                });

            }

        };
    }

    private <T> MongoReactiveEntitiesOperation<T> createMongoDeleteManyOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoReactiveEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            final MongoCollection<T> collection = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType());

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                entities = entities.map(d -> {
                    if (d.vetoed) {
                        return d;
                    }
                    d.filter = MongoUtils.filterByIdAndVersion(conversionService, persistentEntity, d.entity, collection.getCodecRegistry());
                    return d;
                });
            }

            @Override
            protected void execute() throws RuntimeException {
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.collectList().flatMap(data -> {
                    List<Bson> filters = data.stream().filter(d -> !d.vetoed).map(d -> ((Bson) d.filter)).collect(Collectors.toList());
                    Mono<Long> modifiedCount;
                    if (!filters.isEmpty()) {
                        Bson filter = Filters.or(filters);
                        if (QUERY_LOG.isDebugEnabled()) {
                            QUERY_LOG.debug("Executing Mongo 'deleteMany' with filter: {}", filter.toBsonDocument().toJson());
                        }
                        modifiedCount = Mono.from(collection.deleteMany(ctx.clientSession, filter)).map(DeleteResult::getDeletedCount);
                    } else {
                        modifiedCount = Mono.just(0L);
                    }
                    if (persistentEntity.getVersion() != null) {
                        modifiedCount = modifiedCount.map(count -> {
                            checkOptimisticLocking(filters.size(), count);
                            return count;
                        });
                    }
                    return modifiedCount.map(count -> Tuples.of(data, count));
                }).cache();
                entities = entitiesWithRowsUpdated.flatMapMany(t -> Flux.fromIterable(t.getT1()));
                rowsUpdated = entitiesWithRowsUpdated.map(Tuple2::getT2);
            }
        };
    }

    private <T> MongoReactiveEntitiesOperation<T> createMongoInsertManyOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoReactiveEntitiesOperation<T>(ctx, persistentEntity, entities, true) {

            @Override
            protected void execute() throws RuntimeException {
                entities = entities.collectList().flatMapMany(data -> {
                    List<T> toInsert = data.stream().filter(d -> !d.vetoed).map(d -> d.entity).collect(Collectors.toList());

                    MongoCollection<T> collection = getCollection(persistentEntity);
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'insertMany' for collection: {} with documents: {}", collection.getNamespace().getFullName(), toInsert);
                    }
                    return Mono.from(collection.insertMany(ctx.clientSession, toInsert)).flatMapMany(insertManyResult -> {
                        if (hasGeneratedId) {
                            Map<Integer, BsonValue> insertedIds = insertManyResult.getInsertedIds();
                            RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                            BeanProperty<T, Object> idProperty = (BeanProperty<T, Object>) identity.getProperty();
                            int index = 0;
                            for (Data d : data) {
                                if (!d.vetoed) {
                                    BsonValue id = insertedIds.get(index);
                                    if (id == null) {
                                        throw new DataAccessException("Failed to generate ID for entity: " + d.entity);
                                    }
                                    d.entity = updateEntityId(idProperty, d.entity, id);
                                }
                                index++;
                            }
                        }
                        return Flux.fromIterable(data);
                    });

                });
            }
        };
    }

    @Override
    @NonNull
    public <T> Publisher<T> withTransaction(@NonNull ReactiveTransactionStatus<ClientSession> status, @NonNull ReactiveTransactionOperations.TransactionalCallback<ClientSession, T> handler) {
        Objects.requireNonNull(status, "Transaction status cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");
        return Flux.defer(() -> {
            try {
                return handler.doInTransaction(status);
            } catch (Exception e) {
                return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
            }
        }).contextWrite(context -> context.put(ReactiveTransactionStatus.STATUS, status));
    }

    @Override
    public @NonNull
    <T> Flux<T> withTransaction(@NonNull TransactionDefinition definition, @NonNull ReactiveTransactionOperations.TransactionalCallback<ClientSession, T> handler) {
        Objects.requireNonNull(definition, "Transaction definition cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");

        return Flux.deferContextual(contextView -> {
            Object o = contextView.getOrDefault(ReactiveTransactionStatus.STATUS, null);
            TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
            if (o instanceof ReactiveTransactionStatus) {
                // existing transaction, use it
                if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED || propagationBehavior == TransactionDefinition.Propagation.NEVER) {
                    return Flux.error(new TransactionUsageException("Found an existing transaction but propagation behaviour doesn't support it: " + propagationBehavior));
                }
                try {
                    return handler.doInTransaction((ReactiveTransactionStatus<ClientSession>) o);
                } catch (Exception e) {
                    return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                }
            } else {

                if (propagationBehavior == TransactionDefinition.Propagation.MANDATORY) {
                    return Flux.error(new NoTransactionException("Expected an existing transaction, but none was found in the Reactive context."));
                }
                return withClientSessionMany(clientSession -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Transaction Begin for MongoDB configuration: {}", serverName);
                    }
                    DefaultReactiveTransactionStatus status = new DefaultReactiveTransactionStatus(clientSession, true);
                    if (definition.getIsolationLevel() != TransactionDefinition.DEFAULT.getIsolationLevel()) {
                        throw new TransactionUsageException("Isolation level not supported");
                    } else {
                        clientSession.startTransaction();
                    }

                    return Flux.usingWhen(Mono.just(status), sts -> {
                        try {
                            return Flux.from(handler.doInTransaction(status)).contextWrite(context -> context.put(ReactiveTransactionStatus.STATUS, status).put(ReactiveTransactionStatus.ATTRIBUTE, definition));
                        } catch (Exception e) {
                            return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                        }
                    }, this::doCommit, (sts, throwable) -> {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Rolling back transaction on error: " + throwable.getMessage(), throwable);
                        }
                        return Flux.from(sts.getConnection().abortTransaction()).hasElements().onErrorResume((rollbackError) -> {
                            if (rollbackError != throwable && LOG.isWarnEnabled()) {
                                LOG.warn("Error occurred during transaction rollback: " + rollbackError.getMessage(), rollbackError);
                            }
                            return Mono.error(throwable);
                        }).doFinally((sig) -> status.completed = true);

                    }, this::doCommit);
                });
            }
        });

    }

    private Publisher<Void> doCommit(DefaultReactiveTransactionStatus status) {
        if (status.isRollbackOnly()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Rolling back transaction on MongoDB configuration {}.", status);
            }
            return Flux.from(status.getConnection().abortTransaction()).doFinally(sig -> status.completed = true);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Committing transaction for MongoDB configuration {}.", status);
            }
            return Flux.from(status.getConnection().commitTransaction()).doFinally(sig -> status.completed = true);
        }
    }

    /**
     * Represents the current reactive transaction status.
     */
    private static final class DefaultReactiveTransactionStatus implements ReactiveTransactionStatus<ClientSession> {
        private final ClientSession connection;
        private final boolean isNew;
        private boolean rollbackOnly;
        private boolean completed;

        public DefaultReactiveTransactionStatus(ClientSession connection, boolean isNew) {
            this.connection = connection;
            this.isNew = isNew;
        }

        @Override
        public ClientSession getConnection() {
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

    abstract class MongoReactiveEntityOperation<T> extends AbstractReactiveEntityOperations<MongoOperationContext, T, RuntimeException> {

        /**
         * Create a new instance.
         *
         * @param ctx              The context
         * @param persistentEntity The RuntimePersistentEntity
         * @param entity           The entity instance
         * @param insert           If the operation does the insert
         */
        protected MongoReactiveEntityOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity, boolean insert) {
            super(ctx, DefaultReactiveMongoRepositoryOperations.this.cascadeOperations, DefaultReactiveMongoRepositoryOperations.this.conversionService, DefaultReactiveMongoRepositoryOperations.this.entityEventRegistry, persistentEntity, entity, insert);
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
        }
    }

    abstract class MongoReactiveEntitiesOperation<T> extends AbstractReactiveEntitiesOperations<MongoOperationContext, T, RuntimeException> {

        protected MongoReactiveEntitiesOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, boolean insert) {
            super(ctx, DefaultReactiveMongoRepositoryOperations.this.cascadeOperations, DefaultReactiveMongoRepositoryOperations.this.conversionService, DefaultReactiveMongoRepositoryOperations.this.entityEventRegistry, persistentEntity, entities, insert);
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
        }

    }

    protected static class MongoOperationContext extends OperationContext {

        private final ClientSession clientSession;

        public MongoOperationContext(ClientSession clientSession, Class<?> repositoryType, AnnotationMetadata annotationMetadata) {
            super(annotationMetadata, repositoryType);
            this.clientSession = clientSession;
        }
    }
}
