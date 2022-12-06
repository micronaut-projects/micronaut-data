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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
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
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
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
import reactor.util.context.ContextView;
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
public class DefaultReactiveMongoRepositoryOperations extends AbstractMongoRepositoryOperations<MongoDatabase>
    implements MongoReactorRepositoryOperations,
    ReactorReactiveRepositoryOperations,
    ReactiveCascadeOperations.ReactiveCascadeOperationsHelper<DefaultReactiveMongoRepositoryOperations.MongoOperationContext>,
    ReactorReactiveTransactionOperations<ClientSession> {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveMongoRepositoryOperations.class);
    private static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    private static final String NAME = "mongodb.reactive";
    private final String serverName;
    private final MongoClient mongoClient;
    private final ReactiveCascadeOperations<MongoOperationContext> cascadeOperations;
    private final String txStatusKey;
    private final String txDefinitionKey;
    private final String currentSessionKey;

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
     * @param collectionNameProvider     The collection name provider
     */
    DefaultReactiveMongoRepositoryOperations(@Parameter String serverName,
                                             BeanContext beanContext,
                                             List<MediaTypeCodec> codecs,
                                             DateTimeProvider<Object> dateTimeProvider,
                                             RuntimeEntityRegistry runtimeEntityRegistry,
                                             DataConversionService conversionService,
                                             AttributeConverterRegistry attributeConverterRegistry,
                                             MongoClient mongoClient,
                                             MongoCollectionNameProvider collectionNameProvider) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry, collectionNameProvider,
            beanContext.getBean(MongoDatabaseNameProvider.class, "Primary".equals(serverName) ? null : Qualifiers.byName(serverName))
        );
        this.serverName = serverName;
        this.mongoClient = mongoClient;
        this.cascadeOperations = new ReactiveCascadeOperations<>(conversionService, this);
        String name = serverName;
        if (name == null) {
            name = "default";
        }
        this.txStatusKey = ReactorReactiveTransactionOperations.TRANSACTION_STATUS_KEY_PREFIX + "." + NAME + "." + name;
        this.txDefinitionKey = ReactorReactiveTransactionOperations.TRANSACTION_DEFINITION_KEY_PREFIX + "." + NAME + "." + name;
        this.currentSessionKey = "io.micronaut." + NAME + ".session." + name;
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
            MongoPreparedQuery<T, R> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
            if (mongoPreparedQuery.isCount()) {
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
            MongoPreparedQuery<T, Boolean> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
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
                MongoStoredQuery<T, ?> mongoStoredQuery = getMongoStoredQuery(storedQuery);
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
                MongoStoredQuery<T, ?> mongoStoredQuery = getMongoStoredQuery(storedQuery);
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
                MongoStoredQuery<T, Number> mongoStoredQuery = (MongoStoredQuery) getMongoStoredQuery(storedQuery);
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
                MongoStoredQuery<T, Number> mongoStoredQuery = (MongoStoredQuery) getMongoStoredQuery(storedQuery);
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
            MongoPreparedQuery<?, Number> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
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
            MongoPreparedQuery<?, Number> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
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

    private <T, R> Flux<R> findAll(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        if (preparedQuery.isCount()) {
            return getCount(clientSession, preparedQuery).flux();
        }
        if (preparedQuery.isAggregate()) {
            return findAllAggregated(clientSession, preparedQuery, preparedQuery.isDtoProjection());
        }
        return Flux.from(find(clientSession, preparedQuery));
    }

    private <T, R> Mono<R> getCount(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        Class<R> resultType = preparedQuery.getResultType();
        MongoDatabase database = getDatabase(preparedQuery);
        RuntimePersistentEntity<T> persistentEntity = preparedQuery.getPersistentEntity();
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
            MongoFindOptions options = find.getOptions();
            Bson filter = options == null ? null : options.getFilter();
            filter = filter == null ? new BsonDocument() : filter;
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'countDocuments' with filter: {}", filter.toBsonDocument().toJson());
            }
            return Mono.from(getCollection(database, persistentEntity, BsonDocument.class)
                    .countDocuments(clientSession, filter))
                .map(count -> conversionService.convertRequired(count, resultType));
        }
    }

    private <T, R> Mono<R> findOneFiltered(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        return Mono.from(find(clientSession, preparedQuery).limit(1).first())
            .map(r -> {
                Class<T> type = preparedQuery.getRootEntity();
                RuntimePersistentEntity<T> persistentEntity = preparedQuery.getPersistentEntity();
                if (type.isInstance(r)) {
                    return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
                }
                return r;
            });
    }

    private <T, R> Mono<R> findOneAggregated(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        Class<R> resultType = preparedQuery.getResultType();
        Class<T> type = preparedQuery.getRootEntity();
        if (!resultType.isAssignableFrom(type)) {
            MongoDatabase database = getDatabase(preparedQuery);
            return Mono.from(aggregate(clientSession, preparedQuery, BsonDocument.class).first())
                .map(bsonDocument -> convertResult(database.getCodecRegistry(), resultType, bsonDocument, preparedQuery.isDtoProjection()));
        }
        return Mono.from(aggregate(clientSession, preparedQuery).first())
            .map(r -> {
                RuntimePersistentEntity<T> persistentEntity = preparedQuery.getPersistentEntity();
                if (type.isInstance(r)) {
                    return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
                }
                return r;
            });
    }

    private <T, R> Flux<R> findAllAggregated(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery, boolean isDtoProjection) {
        Class<T> type = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();
        Flux<R> aggregate;
        if (!resultType.isAssignableFrom(type)) {
            MongoDatabase database = getDatabase(preparedQuery);
            aggregate = Flux.from(aggregate(clientSession, preparedQuery, BsonDocument.class))
                .map(result -> convertResult(database.getCodecRegistry(), resultType, result, isDtoProjection));
        } else {
            aggregate = Flux.from(aggregate(clientSession, preparedQuery));
        }
        return aggregate;
    }

    private <T, R> FindPublisher<R> find(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        return find(clientSession, preparedQuery, preparedQuery.getResultType());
    }

    private <T, R, MR> FindPublisher<MR> find(ClientSession clientSession,
                                              MongoPreparedQuery<T, R> preparedQuery,
                                              Class<MR> resultType) {
        MongoFind find = preparedQuery.getFind();
        if (QUERY_LOG.isDebugEnabled()) {
            logFind(find);
        }
        MongoDatabase database = getDatabase(preparedQuery);
        MongoCollection<MR> collection = getCollection(database, preparedQuery.getPersistentEntity(), resultType);
        FindPublisher<MR> findIterable = collection.find(clientSession, resultType);
        return applyFindOptions(find.getOptions(), findIterable);
    }

    private <MR> FindPublisher<MR> applyFindOptions(@Nullable MongoFindOptions findOptions, FindPublisher<MR> findIterable) {
        if (findOptions == null) {
            return findIterable;
        }
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
                                                        MongoPreparedQuery<T, R> preparedQuery,
                                                        Class<MR> resultType) {
        MongoDatabase database = getDatabase(preparedQuery);
        MongoCollection<MR> collection = getCollection(database, preparedQuery.getPersistentEntity(), resultType);
        MongoAggregation aggregation = preparedQuery.getAggregation();
        if (QUERY_LOG.isDebugEnabled()) {
            logAggregate(aggregation);
        }
        AggregatePublisher<MR> aggregateIterable = collection.aggregate(clientSession, aggregation.getPipeline(), resultType);
        return applyAggregateOptions(aggregation.getOptions(), aggregateIterable);
    }

    private <T, R> AggregatePublisher<R> aggregate(ClientSession clientSession,
                                                   MongoPreparedQuery<T, R> preparedQuery) {
        return aggregate(clientSession, preparedQuery, preparedQuery.getResultType());
    }

    private <MR> AggregatePublisher<MR> applyAggregateOptions(@Nullable MongoAggregationOptions aggregateOptions, AggregatePublisher<MR> aggregateIterable) {
        if (aggregateOptions == null) {
            return aggregateIterable;
        }
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

    @Override
    protected MongoDatabase getDatabase(PersistentEntity persistentEntity, Class<?> repository) {
        return mongoClient.getDatabase(databaseNameProvider.provide(persistentEntity, repository));
    }

    private MongoDatabase getDatabase(MongoPreparedQuery<?, ?> preparedQuery) {
        return getDatabase(preparedQuery.getPersistentEntity(), preparedQuery.getRepositoryType());
    }

    private <E> MongoCollection<E> getCollection(MongoPreparedQuery<E, ?> preparedQuery) {
        return getCollection(getDatabase(preparedQuery), preparedQuery.getPersistentEntity(), preparedQuery.getRootEntity());
    }

    private <E> MongoCollection<E> getCollection(MongoOperationContext ctx, RuntimePersistentEntity<E> persistentEntity) {
        return getCollection(persistentEntity, ctx.repositoryType, persistentEntity.getIntrospection().getBeanType());
    }

    private <T, R> MongoCollection<R> getCollection(MongoDatabase database, RuntimePersistentEntity<T> persistentEntity, Class<R> resultType) {
        return database.getCollection(collectionNameProvider.provide(persistentEntity), resultType);
    }

    private <T, R> MongoCollection<R> getCollection(RuntimePersistentEntity<T> persistentEntity, Class<?> repositoryClass, Class<R> resultType) {
        return getDatabase(persistentEntity, repositoryClass).getCollection(collectionNameProvider.provide(persistentEntity), resultType);
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
        return Mono.from(collection.insertOne(ctx.clientSession, association, getInsertOneOptions(ctx.annotationMetadata))).then();
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
        return Mono.from(collection.insertMany(ctx.clientSession, associations, getInsertManyOptions(ctx.annotationMetadata))).then();
    }

    @Override
    public <T> Mono<T> withClientSession(Function<ClientSession, Mono<? extends T>> function) {
        Objects.requireNonNull(function, "Handler cannot be null");
        return Mono.deferContextual(contextView -> {
            ClientSession clientSession = contextView.getOrDefault(currentSessionKey, null);
            if (clientSession != null) {
                LOG.debug("Reusing client session for MongoDB configuration: {}", serverName);
                return function.apply(clientSession);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating a new client session for MongoDB configuration: {}", serverName);
            }
            return Mono.usingWhen(mongoClient.startSession(), cs -> function.apply(cs)
                .contextWrite(ctx -> ctx.put(currentSessionKey, cs)), connection -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Closing Connection for MongoDB configuration: {}", serverName);
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
            ClientSession clientSession = contextView.getOrDefault(currentSessionKey, null);
            if (clientSession != null) {
                return Flux.from(function.apply(clientSession));
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating a new client session for MongoDB configuration: {}", serverName);
            }
            return Flux.usingWhen(mongoClient.startSession(),
                cs -> function.apply(cs).contextWrite(ctx -> ctx.put(currentSessionKey, cs)),
                connection -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Closing Connection for MongoDB configuration: {}", serverName);
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
                    return Mono.from(collection.insertOne(ctx.clientSession, d.entity, getInsertOneOptions(ctx.annotationMetadata))).map(insertOneResult -> {
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
                    d.filter = createFilterIdAndVersion(persistentEntity, d.entity, collection.getCodecRegistry());
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
                    return Mono.from(collection.replaceOne(ctx.clientSession, filter, bsonDocument, getReplaceOptions(ctx.annotationMetadata))).map(updateResult -> {
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
                entities = entities.map(list -> {
                    for (Data d : list) {
                        if (d.vetoed) {
                            continue;
                        }
                        d.filter = createFilterIdAndVersion(persistentEntity, d.entity, collection.getCodecRegistry());
                    }
                    return list;
                });
            }

            @Override
            protected void execute() throws RuntimeException {
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.flatMap(list -> {
                    List<ReplaceOneModel<BsonDocument>> replaces = new ArrayList<>(list.size());
                    for (Data d : list) {
                        if (d.vetoed) {
                            continue;
                        }
                        Bson filter = (Bson) d.filter;
                        if (QUERY_LOG.isDebugEnabled()) {
                            QUERY_LOG.debug("Executing Mongo 'replaceOne' with filter: {}", filter.toBsonDocument().toJson());
                        }
                        BsonDocument bsonDocument = BsonDocumentWrapper.asBsonDocument(d.entity, mongoDatabase.getCodecRegistry());
                        bsonDocument.remove("_id");
                        replaces.add(new ReplaceOneModel<>(filter, bsonDocument, getReplaceOptions(ctx.annotationMetadata)));
                    }
                    return Mono.from(collection.bulkWrite(ctx.clientSession, replaces)).map(bulkWriteResult -> {
                        if (persistentEntity.getVersion() != null) {
                            checkOptimisticLocking(replaces.size(), bulkWriteResult.getModifiedCount());
                        }
                        return Tuples.of(list, (long) bulkWriteResult.getModifiedCount());
                    });
                }).cache();
                entities = entitiesWithRowsUpdated.flatMap(t -> Mono.just(t.getT1()));
                rowsUpdated = entitiesWithRowsUpdated.map(Tuple2::getT2);
            }
        };
    }

    private <T> MongoReactiveEntitiesOperation<T> createMongoUpdateOneInBulkOperation(MongoOperationContext ctx,
                                                                                      RuntimePersistentEntity<T> persistentEntity,
                                                                                      Iterable<T> entities,
                                                                                      MongoStoredQuery<T, ?> storedQuery) {
        return new MongoReactiveEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            @Override
            protected void execute() throws RuntimeException {
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.flatMap(list -> {
                    List<UpdateOneModel<T>> updates = new ArrayList<>(list.size());
                    for (Data d : list) {
                        if (d.vetoed) {
                            continue;
                        }
                        MongoUpdate updateOne = storedQuery.getUpdateOne(d.entity);
                        updates.add(new UpdateOneModel<>(updateOne.getFilter(), updateOne.getUpdate(), updateOne.getOptions()));
                    }
                    Mono<Long> modifiedCount = Mono.from(getCollection(ctx, persistentEntity).bulkWrite(ctx.clientSession, updates)).map(result -> {
                        if (storedQuery.isOptimisticLock()) {
                            checkOptimisticLocking(updates.size(), result.getModifiedCount());
                        }
                        return (long) result.getModifiedCount();
                    });
                    return modifiedCount.map(count -> Tuples.of(list, count));
                }).cache();
                entities = entitiesWithRowsUpdated.flatMap(t -> Mono.just(t.getT1()));
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
                    d.filter = createFilterIdAndVersion(persistentEntity, d.entity, collection.getCodecRegistry());
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
                    return Mono.from(getCollection(persistentEntity, ctx.repositoryType).deleteOne(ctx.clientSession, filter, getDeleteOptions(ctx.annotationMetadata))).map(deleteResult -> {
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
                entities = entities.map(list -> {
                    for (Data d : list) {
                        if (d.vetoed) {
                            continue;
                        }
                        d.filter = createFilterIdAndVersion(persistentEntity, d.entity, collection.getCodecRegistry());
                    }
                    return list;
                });
            }

            @Override
            protected void execute() throws RuntimeException {
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.flatMap(list -> {
                    List<Bson> filters = list.stream().filter(d -> !d.vetoed).map(d -> ((Bson) d.filter)).collect(Collectors.toList());
                    Mono<Long> modifiedCount;
                    if (!filters.isEmpty()) {
                        Bson filter = Filters.or(filters);
                        if (QUERY_LOG.isDebugEnabled()) {
                            QUERY_LOG.debug("Executing Mongo 'deleteMany' with filter: {}", filter.toBsonDocument().toJson());
                        }
                        modifiedCount = Mono.from(collection.deleteMany(ctx.clientSession, filter, getDeleteOptions(ctx.annotationMetadata))).map(DeleteResult::getDeletedCount);
                    } else {
                        modifiedCount = Mono.just(0L);
                    }
                    if (persistentEntity.getVersion() != null) {
                        modifiedCount = modifiedCount.map(count -> {
                            checkOptimisticLocking(filters.size(), count);
                            return count;
                        });
                    }
                    return modifiedCount.map(count -> Tuples.of(list, count));
                }).cache();
                entities = entitiesWithRowsUpdated.flatMap(t -> Mono.just(t.getT1()));
                rowsUpdated = entitiesWithRowsUpdated.map(Tuple2::getT2);
            }
        };
    }

    private <T> MongoReactiveEntitiesOperation<T> createMongoDeleteOneInBulkOperation(MongoOperationContext ctx,
                                                                                      RuntimePersistentEntity<T> persistentEntity,
                                                                                      Iterable<T> entities,
                                                                                      MongoStoredQuery<T, Number> storedQuery) {
        return new MongoReactiveEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            @Override
            protected void execute() throws RuntimeException {
                Mono<Tuple2<List<Data>, Long>> entitiesWithRowsUpdated = entities.flatMap(list -> {
                    List<DeleteOneModel<T>> deletes = new ArrayList<>(list.size());
                    for (Data d : list) {
                        if (d.vetoed) {
                            continue;
                        }
                        MongoDelete deleteOne = storedQuery.getDeleteOne(d.entity);
                        deletes.add(new DeleteOneModel<>(deleteOne.getFilter(), deleteOne.getOptions()));
                    }
                    return Mono.from(getCollection(ctx, persistentEntity).bulkWrite(ctx.clientSession, deletes)).map(bulkWriteResult -> {
                        if (storedQuery.isOptimisticLock()) {
                            checkOptimisticLocking(deletes.size(), bulkWriteResult.getDeletedCount());
                        }
                        return Tuples.of(list, (long) bulkWriteResult.getDeletedCount());
                    });
                }).cache();
                entities = entitiesWithRowsUpdated.flatMap(t -> Mono.just(t.getT1()));
                rowsUpdated = entitiesWithRowsUpdated.map(Tuple2::getT2);
            }
        };
    }

    private <T> MongoReactiveEntitiesOperation<T> createMongoInsertManyOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoReactiveEntitiesOperation<T>(ctx, persistentEntity, entities, true) {

            @Override
            protected void execute() throws RuntimeException {
                entities = entities.flatMap(list -> {
                    List<T> toInsert = list.stream().filter(d -> !d.vetoed).map(d -> d.entity).collect(Collectors.toList());
                    if (toInsert.isEmpty()) {
                        return Mono.just(list);
                    }

                    MongoCollection<T> collection = getCollection(persistentEntity, ctx.repositoryType);
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'insertMany' for collection: {} with documents: {}", collection.getNamespace().getFullName(), toInsert);
                    }
                    return Mono.from(collection.insertMany(ctx.clientSession, toInsert, getInsertManyOptions(ctx.annotationMetadata))).flatMap(insertManyResult -> {
                        if (hasGeneratedId) {
                            Map<Integer, BsonValue> insertedIds = insertManyResult.getInsertedIds();
                            RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                            BeanProperty<T, Object> idProperty = (BeanProperty<T, Object>) identity.getProperty();
                            int index = 0;
                            for (Data d : list) {
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
                        return Mono.just(list);
                    });

                });
            }
        };
    }

    @Override
    public ReactiveTransactionStatus<ClientSession> getTransactionStatus(ContextView contextView) {
        return contextView.getOrDefault(txStatusKey, null);
    }

    @Override
    public TransactionDefinition getTransactionDefinition(ContextView contextView) {
        return contextView.getOrDefault(txDefinitionKey, null);
    }

    @Override
    @NonNull
    public <T> Flux<T> withTransaction(@NonNull TransactionDefinition definition,
                                       @NonNull ReactiveTransactionOperations.TransactionalCallback<ClientSession, T> handler) {
        Objects.requireNonNull(definition, "Transaction definition cannot be null");
        Objects.requireNonNull(handler, "Callback handler cannot be null");

        return Flux.deferContextual(contextView -> {
            ReactiveTransactionStatus<ClientSession> transactionStatus = getTransactionStatus(contextView);
            TransactionDefinition.Propagation propagationBehavior = definition.getPropagationBehavior();
            if (transactionStatus != null) {
                // existing transaction, use it
                if (propagationBehavior == TransactionDefinition.Propagation.NOT_SUPPORTED || propagationBehavior == TransactionDefinition.Propagation.NEVER) {
                    return Flux.error(new TransactionUsageException("Found an existing transaction but propagation behaviour doesn't support it: " + propagationBehavior));
                }
                ReactiveTransactionStatus<ClientSession> existingTransaction = existingTransaction(transactionStatus);
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
                            return Flux.from(handler.doInTransaction(status)).contextWrite(context -> context.put(txStatusKey, status).put(txDefinitionKey, definition));
                        } catch (Exception e) {
                            return Flux.error(new TransactionSystemException("Error invoking doInTransaction handler: " + e.getMessage(), e));
                        }
                    }, this::doCommit, (sts, throwable) -> {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Rolling back transaction on error: " + throwable.getMessage(), throwable);
                        }
                        Flux<Void> abort;
                        if (definition.rollbackOn(throwable)) {
                            abort = Flux.from(sts.getConnection().abortTransaction());
                        } else {
                            abort = Flux.error(throwable);
                        }
                        return abort.onErrorResume((rollbackError) -> {
                            if (rollbackError != throwable && LOG.isWarnEnabled()) {
                                LOG.warn("Error occurred during transaction rollback: " + rollbackError.getMessage(), rollbackError);
                            }
                            return Mono.error(throwable);
                        }).as(flux -> doFinish(flux, status));

                    }, this::doCommit);
                });
            }
        });

    }

    private ReactiveTransactionStatus<ClientSession> existingTransaction(ReactiveTransactionStatus<ClientSession> existing) {
        return new ReactiveTransactionStatus<ClientSession>() {
            @Override
            public ClientSession getConnection() {
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

    private Publisher<Void> doCommit(DefaultReactiveTransactionStatus status) {
        if (status.isRollbackOnly()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Rolling back transaction on MongoDB configuration {}.", status);
            }
            return Flux.from(status.getConnection().abortTransaction()).as(flux -> doFinish(flux, status));
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Committing transaction for MongoDB configuration {}.", status);
            }
            return Flux.from(status.getConnection().commitTransaction()).as(flux -> doFinish(flux, status));
        }
    }

    private <T> Publisher<Void> doFinish(Flux<T> flux, DefaultReactiveTransactionStatus status) {
        return flux.hasElements().map(ignore -> {
                status.completed = true;
                return ignore;
            }).then();
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
