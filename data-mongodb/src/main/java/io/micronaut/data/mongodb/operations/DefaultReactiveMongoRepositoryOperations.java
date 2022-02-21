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

import com.mongodb.CursorType;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.ClientSession;
import com.mongodb.reactivestreams.client.FindPublisher;
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
import io.micronaut.data.model.PersistentProperty;
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
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.mongodb.conf.RequiresReactiveMongo;
import io.micronaut.data.mongodb.database.ReactiveMongoDatabaseFactory;
import io.micronaut.data.mongodb.operations.options.MongoAggregationOptions;
import io.micronaut.data.mongodb.operations.options.MongoFindOptions;
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
import org.bson.codecs.configuration.CodecRegistry;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The reactive MongoDB repository operations implementation.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@RequiresReactiveMongo
@EachBean(MongoClient.class)
@Internal
public class DefaultReactiveMongoRepositoryOperations extends AbstractMongoRepositoryOperations<MongoDatabase, ClientSession, Object>
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
    DefaultReactiveMongoRepositoryOperations(@Parameter String serverName, BeanContext beanContext, List<MediaTypeCodec> codecs, DateTimeProvider<Object> dateTimeProvider, RuntimeEntityRegistry runtimeEntityRegistry, DataConversionService<?> conversionService, AttributeConverterRegistry attributeConverterRegistry, MongoClient mongoClient) {
        super(serverName, beanContext, codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
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
            MongoDatabase database = getDatabase(persistentEntity, null);
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
            MongoPreparedQuery<T, R, MongoDatabase> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
            if (isCountQuery(preparedQuery)) {
                return getCount(clientSession, mongoPreparedQuery);
            }
            if (mongoPreparedQuery.isAggregate()) {
                return findOneAggregated(clientSession, mongoPreparedQuery);
            } else {
                return findOneFiltered(clientSession, mongoPreparedQuery);
            }
        });
    }

    @Override
    public <T> Mono<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery) {
        return withClientSession(clientSession -> {
            MongoPreparedQuery<T, Boolean, MongoDatabase> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
            if (mongoPreparedQuery.isAggregate()) {
                return Flux.from(aggregate(clientSession, mongoPreparedQuery, BsonDocument.class)).hasElements();
            } else {
                return Flux.from(find(clientSession, mongoPreparedQuery, BsonDocument.class).limit(1)).hasElements();
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
        return withClientSessionMany(clientSession -> findAll(clientSession, getMongoPreparedQuery(preparedQuery)));
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
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            if (storedQuery != null) {
                MongoStoredQuery<T, ?, MongoDatabase> mongoStoredQuery = getMongoStoredQuery(storedQuery);
                MongoReactiveEntitiesOperation<T> op = createMongoUpdateOneInBulkOperation(ctx, mongoStoredQuery.getRuntimePersistentEntity(),
                        Collections.singletonList(operation.getEntity()), mongoStoredQuery);
                op.update();
                return op.getEntities().next();
            }
            return updateOne(ctx, operation.getEntity(), runtimeEntityRegistry.getEntity(operation.getRootEntity()));
        });
    }

    @Override
    public <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        return withClientSessionMany(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getRepositoryType(), operation.getAnnotationMetadata());
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            if (storedQuery != null) {
                MongoStoredQuery<T, ?, MongoDatabase> mongoStoredQuery = getMongoStoredQuery(storedQuery);
                MongoReactiveEntitiesOperation<T> op = createMongoUpdateOneInBulkOperation(ctx, mongoStoredQuery.getRuntimePersistentEntity(), operation, mongoStoredQuery);
                op.update();
                return op.getEntities();
            }
            return updateBatch(ctx, operation, runtimeEntityRegistry.getEntity(operation.getRootEntity()));
        });
    }

    @Override
    public <T> Mono<Number> delete(DeleteOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getRepositoryType(), operation.getAnnotationMetadata());
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            if (storedQuery != null) {
                MongoStoredQuery<T, Number, MongoDatabase> mongoStoredQuery = (MongoStoredQuery) getMongoStoredQuery(storedQuery);
                MongoReactiveEntitiesOperation<T> op = createMongoDeleteOneInBulkOperation(ctx, mongoStoredQuery.getRuntimePersistentEntity(),
                        Collections.singletonList(operation.getEntity()), mongoStoredQuery);
                op.update();
                return op.getRowsUpdated();
            }
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            MongoReactiveEntityOperation<T> op = createMongoDeleteOneOperation(ctx, persistentEntity, operation.getEntity());
            op.delete();
            return op.getRowsUpdated();
        });
    }

    @Override
    public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getRepositoryType(), operation.getAnnotationMetadata());
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            if (storedQuery != null) {
                MongoStoredQuery<T, Number, MongoDatabase> mongoStoredQuery = (MongoStoredQuery) getMongoStoredQuery(storedQuery);
                MongoReactiveEntitiesOperation<T> op = createMongoDeleteOneInBulkOperation(ctx, mongoStoredQuery.getRuntimePersistentEntity(), operation, mongoStoredQuery);
                op.update();
                return op.getRowsUpdated();
            }
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            if (operation.all()) {
                MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
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
            MongoPreparedQuery<?, Number, MongoDatabase> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
            MongoUpdate updateMany = mongoPreparedQuery.getUpdateMany();
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'updateMany' with filter: {} and update: {}", updateMany.getFilter().toBsonDocument().toJson(), updateMany.getUpdate().toBsonDocument().toJson());
            }
            return Mono.from(getCollection(mongoPreparedQuery)
                    .updateMany(clientSession, updateMany.getFilter(), updateMany.getUpdate(), updateMany.getOptions())).map(updateResult -> {
                if (mongoPreparedQuery.isOptimisticLock()) {
                    checkOptimisticLocking(1, (int) updateResult.getModifiedCount());
                }
                return updateResult.getModifiedCount();
            });
        });
    }

    @Override
    public Mono<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return withClientSession(clientSession -> {
            MongoPreparedQuery<?, Number, MongoDatabase> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
            MongoDelete deleteMany = mongoPreparedQuery.getDeleteMany();
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'deleteMany' with filter: {}", deleteMany.getFilter().toBsonDocument().toJson());
            }
            return Mono.from(getCollection(mongoPreparedQuery).
                    deleteMany(clientSession, deleteMany.getFilter(), deleteMany.getOptions())).map(deleteResult -> {
                if (mongoPreparedQuery.isOptimisticLock()) {
                    checkOptimisticLocking(1, (int) deleteResult.getDeletedCount());
                }
                return deleteResult.getDeletedCount();
            });
        });
    }

    private <E> MongoCollection<E> getCollection(MongoPreparedQuery<E, ?, MongoDatabase> preparedQuery) {
        return getCollection(preparedQuery.getDatabase(), preparedQuery.getRuntimePersistentEntity(), preparedQuery.getRootEntity());
    }

    private <E> MongoCollection<E> getCollection(MongoStoredQuery<E, ?, MongoDatabase> storedQuery) {
        return getCollection(storedQuery.getDatabase(), storedQuery.getRuntimePersistentEntity(), storedQuery.getRootEntity());
    }

    private <T, R> Flux<R> findAll(ClientSession clientSession, MongoPreparedQuery<T, R, MongoDatabase> preparedQuery) {
        if (isCountQuery(preparedQuery)) {
            return getCount(clientSession, preparedQuery).flux();
        }
        if (preparedQuery.isAggregate()) {
            return findAllAggregated(clientSession, preparedQuery, preparedQuery.isDtoProjection());
        }
        return Flux.from(find(clientSession, preparedQuery));
    }

    private <T, R> Mono<R> getCount(ClientSession clientSession, MongoPreparedQuery<T, R, MongoDatabase> preparedQuery) {
        Class<R> resultType = preparedQuery.getResultType();
        MongoDatabase database = preparedQuery.getDatabase();
        RuntimePersistentEntity<T> persistentEntity = preparedQuery.getRuntimePersistentEntity();
        if (preparedQuery.isAggregate()) {
            MongoAggregation aggregation = preparedQuery.getAggregation();
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", aggregation.getPipeline().stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
            }
            return Mono.from(aggregate(clientSession, preparedQuery, BsonDocument.class).first())
                    .map(bsonDocument -> convertResult(database.getCodecRegistry(), resultType, bsonDocument, false))
                    .switchIfEmpty(Mono.defer(() -> Mono.just(conversionService.convertRequired(0, resultType))));
        } else {
            MongoFind find = preparedQuery.getFind();
            Bson filter = find.getOptions().getFilter();
            filter = filter == null ? new BsonDocument() : filter;
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'countDocuments' with filter: {}", filter.toBsonDocument().toJson());
            }
            return Mono.from(getCollection(database, persistentEntity, BsonDocument.class)
                            .countDocuments(clientSession, filter))
                    .map(count -> conversionService.convertRequired(count, resultType));
        }
    }

    private <T, R> Mono<R> findOneFiltered(ClientSession clientSession, MongoPreparedQuery<T, R, MongoDatabase> preparedQuery) {
        return Mono.from(find(clientSession, preparedQuery).limit(1).first())
                .map(r -> {
                    Class<T> type = preparedQuery.getRootEntity();
                    RuntimePersistentEntity<T> persistentEntity = preparedQuery.getRuntimePersistentEntity();
                    if (type.isInstance(r)) {
                        return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
                    }
                    return r;
                });
    }

    private <T, R> Mono<R> findOneAggregated(ClientSession clientSession, MongoPreparedQuery<T, R, MongoDatabase> preparedQuery) {
        Class<R> resultType = preparedQuery.getResultType();
        Class<T> type = preparedQuery.getRootEntity();
        if (!resultType.isAssignableFrom(type)) {
            return Mono.from(aggregate(clientSession, preparedQuery, BsonDocument.class).first())
                    .map(bsonDocument -> convertResult(preparedQuery.getDatabase().getCodecRegistry(), resultType, bsonDocument, preparedQuery.isDtoProjection()));
        }
        return Mono.from(aggregate(clientSession, preparedQuery).first())
                .map(r -> {
                    RuntimePersistentEntity<T> persistentEntity = preparedQuery.getRuntimePersistentEntity();
                    if (type.isInstance(r)) {
                        return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
                    }
                    return r;
                });
    }

    private <T, R> Flux<R> findAllAggregated(ClientSession clientSession, MongoPreparedQuery<T, R, MongoDatabase> preparedQuery, boolean isDtoProjection) {
        Class<T> type = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();
        Flux<R> aggregate;
        if (!resultType.isAssignableFrom(type)) {
            aggregate = Flux.from(aggregate(clientSession, preparedQuery, BsonDocument.class))
                    .map(result -> convertResult(preparedQuery.getDatabase().getCodecRegistry(), resultType, result, isDtoProjection));
        } else {
            aggregate = Flux.from(aggregate(clientSession, preparedQuery));
        }
        return aggregate;
    }

    private <T, R> FindPublisher<R> find(ClientSession clientSession, MongoPreparedQuery<T, R, MongoDatabase> preparedQuery) {
        return find(clientSession, preparedQuery, preparedQuery.getResultType());
    }

    private <T, R, MR> FindPublisher<MR> find(ClientSession clientSession,
                                              MongoPreparedQuery<T, R, MongoDatabase> preparedQuery,
                                              Class<MR> resultType) {
        MongoFind find = preparedQuery.getFind();
        if (QUERY_LOG.isDebugEnabled()) {
            logFind(find);
        }
        MongoCollection<MR> collection = getCollection(preparedQuery.getDatabase(), preparedQuery.getRuntimePersistentEntity(), resultType);
        FindPublisher<MR> findIterable = collection.find(clientSession, resultType);
        return applyFindOptions(find.getOptions(), findIterable);
    }

    private <T, R, MR> FindPublisher<MR> applyFindOptions(MongoFindOptions findOptions, FindPublisher<MR> findIterable) {
        Bson filter = findOptions.getFilter();
        if (filter != null) {
            findIterable = findIterable.filter(filter);
        }
        Collation collation = findOptions.getCollation();
        if (collation != null) {
            findIterable = findIterable.collation(collation);
        }
        Integer skip = findOptions.getSkip();
        if (skip != null) {
            findIterable = findIterable.skip(skip);
        }
        Integer limit = findOptions.getLimit();
        if (limit != null) {
            findIterable = findIterable.limit(Math.max(limit, 0));
        }
        Bson sort = findOptions.getSort();
        if (sort != null) {
            findIterable = findIterable.sort(sort);
        }
        Bson projection = findOptions.getProjection();
        if (projection != null) {
            findIterable = findIterable.projection(projection);
        }
        Integer batchSize = findOptions.getBatchSize();
        if (batchSize != null) {
            findIterable = findIterable.batchSize(batchSize);
        }
        Boolean allowDiskUse = findOptions.getAllowDiskUse();
        if (allowDiskUse != null) {
            findIterable = findIterable.allowDiskUse(allowDiskUse);
        }
        Long maxTimeMS = findOptions.getMaxTimeMS();
        if (maxTimeMS != null) {
            findIterable = findIterable.maxTime(maxTimeMS, TimeUnit.MILLISECONDS);
        }
        Long maxAwaitTimeMS = findOptions.getMaxAwaitTimeMS();
        if (maxAwaitTimeMS != null) {
            findIterable = findIterable.maxAwaitTime(maxAwaitTimeMS, TimeUnit.MILLISECONDS);
        }
        String comment = findOptions.getComment();
        if (comment != null) {
            findIterable = findIterable.comment(comment);
        }
        Bson hint = findOptions.getHint();
        if (hint != null) {
            findIterable = findIterable.hint(hint);
        }
        CursorType cursorType = findOptions.getCursorType();
        if (cursorType != null) {
            findIterable = findIterable.cursorType(cursorType);
        }
        Boolean noCursorTimeout = findOptions.getNoCursorTimeout();
        if (noCursorTimeout != null) {
            findIterable = findIterable.noCursorTimeout(noCursorTimeout);
        }
        Boolean partial = findOptions.getPartial();
        if (partial != null) {
            findIterable = findIterable.partial(partial);
        }
        Bson max = findOptions.getMax();
        if (max != null) {
            findIterable = findIterable.max(max);
        }
        Bson min = findOptions.getMin();
        if (min != null) {
            findIterable = findIterable.min(min);
        }
        Boolean returnKey = findOptions.getReturnKey();
        if (returnKey != null) {
            findIterable = findIterable.returnKey(returnKey);
        }
        Boolean showRecordId = findOptions.getShowRecordId();
        if (showRecordId != null) {
            findIterable = findIterable.showRecordId(showRecordId);
        }
        return findIterable;
    }

    private <T, R, MR> AggregatePublisher<MR> aggregate(ClientSession clientSession,
                                                        MongoPreparedQuery<T, R, MongoDatabase> preparedQuery,
                                                        Class<MR> resultType) {
        MongoCollection<MR> collection = getCollection(preparedQuery.getDatabase(), preparedQuery.getRuntimePersistentEntity(), resultType);
        MongoAggregation aggregation = preparedQuery.getAggregation();
        if (QUERY_LOG.isDebugEnabled()) {
            logAggregate(aggregation);
        }
        AggregatePublisher<MR> aggregateIterable = collection.aggregate(clientSession, aggregation.getPipeline(), resultType);
        return applyAggregateOptions(aggregation.getOptions(), aggregateIterable);
    }

    private <T, R> AggregatePublisher<R> aggregate(ClientSession clientSession,
                                                   MongoPreparedQuery<T, R, MongoDatabase> preparedQuery) {
        return aggregate(clientSession, preparedQuery, preparedQuery.getResultType());
    }

    private <MR> AggregatePublisher<MR> applyAggregateOptions(MongoAggregationOptions aggregateOptions, AggregatePublisher<MR> aggregateIterable) {
        if (aggregateOptions.getCollation() != null) {
            aggregateIterable = aggregateIterable.collation(aggregateOptions.getCollation());
        }
        Boolean allowDiskUse = aggregateOptions.getAllowDiskUse();
        if (allowDiskUse != null) {
            aggregateIterable = aggregateIterable.allowDiskUse(allowDiskUse);
        }
        Long maxTimeMS = aggregateOptions.getMaxTimeMS();
        if (maxTimeMS != null) {
            aggregateIterable = aggregateIterable.maxTime(maxTimeMS, TimeUnit.MILLISECONDS);
        }
        Long maxAwaitTimeMS = aggregateOptions.getMaxAwaitTimeMS();
        if (maxTimeMS != null) {
            aggregateIterable = aggregateIterable.maxAwaitTime(maxAwaitTimeMS, TimeUnit.MILLISECONDS);
        }
        Boolean bypassDocumentValidation = aggregateOptions.getBypassDocumentValidation();
        if (bypassDocumentValidation != null) {
            aggregateIterable = aggregateIterable.bypassDocumentValidation(bypassDocumentValidation);
        }
        String comment = aggregateOptions.getComment();
        if (comment != null) {
            aggregateIterable = aggregateIterable.comment(comment);
        }
        Bson hint = aggregateOptions.getHint();
        if (hint != null) {
            aggregateIterable = aggregateIterable.hint(hint);
        }
        return aggregateIterable;
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

    private <T> MongoCollection<T> getCollection(RuntimePersistentEntity<T> persistentEntity, Class<?> repositoryClass) {
        return getDatabase(persistentEntity, repositoryClass).getCollection(persistentEntity.getPersistedName(), persistentEntity.getIntrospection().getBeanType());
    }

    @Override
    public <T> Mono<T> persistOne(MongoOperationContext ctx, T value, RuntimePersistentEntity<T> persistentEntity) {
        MongoReactiveEntityOperation<T> op = createMongoInsertOneOperation(ctx, persistentEntity, value);
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
        MongoReactiveEntityOperation<T> op = createMongoReplaceOneOperation(ctx, persistentEntity, value);
        op.update();
        return op.getEntity();
    }

    private <T> Flux<T> updateBatch(MongoOperationContext ctx, Iterable<T> values, RuntimePersistentEntity<T> persistentEntity) {
        MongoReactiveEntitiesOperation<T> op = createMongoReplaceOneInBulkOperation(ctx, persistentEntity, values);
        op.update();
        return op.getEntities();
    }

    private <T, R> MongoCollection<R> getCollection(MongoDatabase database, RuntimePersistentEntity<T> persistentEntity, Class<R> resultType) {
        return database.getCollection(persistentEntity.getPersistedName(), resultType);
    }

    @Override
    protected MongoDatabase getDatabase(RuntimePersistentEntity<?> persistentEntity, Class<?> repositoryClass) {
        if (repositoryClass != null) {
            String database = repoDatabaseConfig.get(repositoryClass);
            if (database != null) {
                return mongoClient.getDatabase(database);
            }
        }
        return mongoDatabaseFactory.getDatabase(persistentEntity);
    }

    @Override
    protected CodecRegistry getCodecRegistry(MongoDatabase mongoDatabase) {
        return mongoDatabase.getCodecRegistry();
    }

    @Override
    public Mono<Void> persistManyAssociation(MongoOperationContext ctx, RuntimeAssociation runtimeAssociation, Object value, RuntimePersistentEntity<Object> persistentEntity, Object child, RuntimePersistentEntity<Object> childPersistentEntity) {
        String joinCollectionName = runtimeAssociation.getOwner().getNamingStrategy().mappedName(runtimeAssociation);
        MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
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
        MongoCollection<BsonDocument> collection = getDatabase(persistentEntity, ctx.repositoryType).getCollection(joinCollectionName, BsonDocument.class);
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

    private <T> MongoReactiveEntityOperation<T> createMongoInsertOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoReactiveEntityOperation<T>(ctx, persistentEntity, entity, true) {

            @Override
            protected void execute() throws RuntimeException {
                MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
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

    private <T> MongoReactiveEntityOperation<T> createMongoReplaceOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoReactiveEntityOperation<T>(ctx, persistentEntity, entity, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
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

    private <T> MongoReactiveEntitiesOperation<T> createMongoReplaceOneInBulkOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoReactiveEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
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
                    List<ReplaceOneModel<BsonDocument>> replaces = new ArrayList<>(data.size());
                    for (Data d : data) {
                        if (d.vetoed) {
                            continue;
                        }
                        Bson filter = (Bson) d.filter;
                        if (QUERY_LOG.isDebugEnabled()) {
                            QUERY_LOG.debug("Executing Mongo 'replaceOne' with filter: {}", filter.toBsonDocument().toJson());
                        }
                        BsonDocument bsonDocument = BsonDocumentWrapper.asBsonDocument(d.entity, mongoDatabase.getCodecRegistry());
                        bsonDocument.remove("_id");
                        replaces.add(new ReplaceOneModel<>(filter, bsonDocument));
                    }
                    return Mono.from(collection.bulkWrite(ctx.clientSession, replaces)).map(bulkWriteResult -> {
                        if (persistentEntity.getVersion() != null) {
                            checkOptimisticLocking(replaces.size(), bulkWriteResult.getModifiedCount());
                        }
                        return Tuples.of(data, (long) bulkWriteResult.getModifiedCount());
                    });
                }).cache();
                entities = entitiesWithRowsUpdated.flatMapMany(t -> Flux.fromIterable(t.getT1()));
                rowsUpdated = entitiesWithRowsUpdated.map(Tuple2::getT2);
            }
        };
    }

    private <T> MongoReactiveEntitiesOperation<T> createMongoUpdateOneInBulkOperation(MongoOperationContext ctx,
                                                                                      RuntimePersistentEntity<T> persistentEntity,
                                                                                      Iterable<T> entities,
                                                                                      MongoStoredQuery<T, ?, MongoDatabase> storedQuery) {
        return new MongoReactiveEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            @Override
            protected void execute() throws RuntimeException {
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.collectList().flatMap(data -> {
                    List<UpdateOneModel<T>> updates = new ArrayList<>(data.size());
                    for (Data d : data) {
                        if (d.vetoed) {
                            continue;
                        }
                        MongoUpdate updateOne = storedQuery.getUpdateOne(d.entity);
                        updates.add(new UpdateOneModel<>(updateOne.getFilter(), updateOne.getUpdate(), updateOne.getOptions()));
                    }
                    Mono<Long> modifiedCount = Mono.from(getCollection(storedQuery).bulkWrite(ctx.clientSession, updates)).map(result -> {
                        if (storedQuery.isOptimisticLock()) {
                            checkOptimisticLocking(updates.size(), result.getModifiedCount());
                        }
                        return (long) result.getModifiedCount();
                    });
                    return modifiedCount.map(count -> Tuples.of(data, count));
                }).cache();
                entities = entitiesWithRowsUpdated.flatMapMany(t -> Flux.fromIterable(t.getT1()));
                rowsUpdated = entitiesWithRowsUpdated.map(Tuple2::getT2);
            }
        };
    }

    private <T> MongoReactiveEntityOperation<T> createMongoDeleteOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoReactiveEntityOperation<T>(ctx, persistentEntity, entity, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
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
                    return Mono.from(getCollection(persistentEntity, ctx.repositoryType).deleteOne(ctx.clientSession, filter)).map(deleteResult -> {
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

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
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

    private <T> MongoReactiveEntitiesOperation<T> createMongoDeleteOneInBulkOperation(MongoOperationContext ctx,
                                                                                      RuntimePersistentEntity<T> persistentEntity,
                                                                                      Iterable<T> entities,
                                                                                      MongoStoredQuery<T, Number, MongoDatabase> storedQuery) {
        return new MongoReactiveEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            @Override
            protected void execute() throws RuntimeException {
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.collectList().flatMap(data -> {
                    List<DeleteOneModel<T>> deletes = new ArrayList<>(data.size());
                    for (Data d : data) {
                        if (d.vetoed) {
                            continue;
                        }
                        MongoDelete deleteOne = storedQuery.getDeleteOne(d.entity);
                        deletes.add(new DeleteOneModel<>(deleteOne.getFilter(), deleteOne.getOptions()));
                    }
                    return Mono.from(getCollection(storedQuery).bulkWrite(ctx.clientSession, deletes)).map(bulkWriteResult -> {
                        if (storedQuery.isOptimisticLock()) {
                            checkOptimisticLocking(deletes.size(), bulkWriteResult.getDeletedCount());
                        }
                        return Tuples.of(data, (long) bulkWriteResult.getDeletedCount());
                    });
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

                    MongoCollection<T> collection = getCollection(persistentEntity, ctx.repositoryType);
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
