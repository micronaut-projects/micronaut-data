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
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
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
import io.micronaut.data.model.Pageable;
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
import io.micronaut.data.mongodb.conf.RequiresSyncMongo;
import io.micronaut.data.mongodb.operations.options.MongoAggregationOptions;
import io.micronaut.data.mongodb.operations.options.MongoFindOptions;
import io.micronaut.data.mongodb.transaction.MongoSynchronousTransactionManagerImpl;
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
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Named;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Default Mongo repository operations.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@RequiresSyncMongo
@EachBean(MongoClient.class)
@Internal
public final class DefaultMongoRepositoryOperations extends AbstractMongoRepositoryOperations<MongoDatabase> implements
        MongoRepositoryOperations,
        AsyncCapableRepository,
        ReactiveCapableRepository,
        SyncCascadeOperations.SyncCascadeOperationsHelper<DefaultMongoRepositoryOperations.MongoOperationContext> {
    private final MongoClient mongoClient;
    private final SyncCascadeOperations<MongoOperationContext> cascadeOperations;
    private final MongoSynchronousTransactionManagerImpl transactionManager;
    private ExecutorAsyncOperations asyncOperations;
    private ExecutorService executorService;

    /**
     * Default constructor.
     *
     * @param serverName                  The server name
     * @param beanContext                 The bean context
     * @param codecs                      The media type codecs
     * @param dateTimeProvider            The date time provider
     * @param runtimeEntityRegistry       The entity registry
     * @param conversionService           The conversion service
     * @param attributeConverterRegistry  The attribute converter registry
     * @param mongoClient                 The Mongo client
     * @param collectionNameProvider      The Mongo collection name provider
     * @param executorService             The executor service
     */
    DefaultMongoRepositoryOperations(@Nullable @Parameter String serverName,
                                     BeanContext beanContext,
                                     List<MediaTypeCodec> codecs,
                                     DateTimeProvider<Object> dateTimeProvider,
                                     RuntimeEntityRegistry runtimeEntityRegistry,
                                     DataConversionService conversionService,
                                     AttributeConverterRegistry attributeConverterRegistry,
                                     MongoClient mongoClient,
                                     MongoCollectionNameProvider collectionNameProvider,
                                     @Named("io") @Nullable ExecutorService executorService) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry, collectionNameProvider,
            beanContext.getBean(MongoDatabaseNameProvider.class, "Primary".equals(serverName) ? null : Qualifiers.byName(serverName)));
        this.mongoClient = mongoClient;
        this.cascadeOperations = new SyncCascadeOperations<>(conversionService, this);
        boolean isPrimary = "Primary".equals(serverName);
        this.transactionManager = beanContext.getBean(MongoSynchronousTransactionManagerImpl.class, isPrimary ? null : Qualifiers.byName(serverName));
        this.executorService = executorService;
    }

    @Override
    public <T> T findOne(Class<T> type, Serializable id) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
            MongoDatabase database = getDatabase(persistentEntity, null);
            MongoCollection<T> collection = getCollection(database, persistentEntity, type);
            Bson filter = MongoUtils.filterById(conversionService, persistentEntity, id, collection.getCodecRegistry());
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'find' with filter: {}", filter.toBsonDocument().toJson());
            }
            return collection.find(clientSession, filter, type).first();
        });
    }

    @Override
    public <T, R> R findOne(PreparedQuery<T, R> preparedQuery) {
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

    private <T, R> R getCount(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        Class<R> resultType = preparedQuery.getResultType();
        RuntimePersistentEntity<T> persistentEntity = preparedQuery.getPersistentEntity();
        MongoDatabase database = getDatabase(preparedQuery);
        if (preparedQuery.isAggregate()) {
            MongoAggregation aggregation = preparedQuery.getAggregation();
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", aggregation.getPipeline().stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
            }
            R result = aggregate(clientSession, preparedQuery, BsonDocument.class)
                    .map(bsonDocument -> convertResult(database.getCodecRegistry(), resultType, bsonDocument, false))
                    .first();
            if (result == null) {
                result = conversionService.convertRequired(0, resultType);
            }
            return result;
        } else {
            MongoFind find = preparedQuery.getFind();
            MongoFindOptions options = find.getOptions();
            Bson filter = options == null ? null : options.getFilter();
            filter = filter == null ? new BsonDocument() : filter;
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'countDocuments' with filter: {}", filter.toBsonDocument().toJson());
            }
            long count = getCollection(database, persistentEntity, BsonDocument.class)
                    .countDocuments(clientSession, filter);
            return conversionService.convertRequired(count, resultType);
        }
    }

    @Override
    public <T> boolean exists(PreparedQuery<T, Boolean> preparedQuery) {
        return withClientSession(clientSession -> {
            MongoPreparedQuery<T, Boolean> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
            if (mongoPreparedQuery.isAggregate()) {
                return aggregate(clientSession, mongoPreparedQuery, BsonDocument.class).iterator().hasNext();
            } else {
                return find(clientSession, mongoPreparedQuery)
                        .limit(1)
                        .iterator().hasNext();
            }
        });
    }

    @Override
    public <T> Iterable<T> findAll(PagedQuery<T> query) {
        throw new DataAccessException("Not supported!");
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        throw new DataAccessException("Not supported!");
    }

    @Override
    public <T> Stream<T> findStream(PagedQuery<T> query) {
        throw new DataAccessException("Not supported!");
    }

    @Override
    public <R> Page<R> findPage(PagedQuery<R> query) {
        throw new DataAccessException("Not supported!");
    }

    @Override
    public <T, R> Iterable<R> findAll(PreparedQuery<T, R> preparedQuery) {
        return withClientSession(clientSession -> findAll(clientSession, getMongoPreparedQuery(preparedQuery), false));
    }

    @Override
    public <T, R> Stream<R> findStream(PreparedQuery<T, R> preparedQuery) {
        return withClientSession(clientSession -> {
            MongoIterable<R> iterable = (MongoIterable<R>) findAll(clientSession, getMongoPreparedQuery(preparedQuery), true);
            MongoCursor<R> iterator = iterable.iterator();
            Spliterators.AbstractSpliterator<R> spliterator = new Spliterators.AbstractSpliterator<R>(Long.MAX_VALUE,
                    Spliterator.ORDERED | Spliterator.IMMUTABLE) {
                @Override
                public boolean tryAdvance(Consumer<? super R> action) {
                    if (iterator.hasNext()) {
                        action.accept(iterator.next());
                        return true;
                    }
                    iterator.close();
                    return false;
                }
            };
            return StreamSupport.stream(spliterator, false).onClose(iterator::close);
        });
    }

    private <T, R> Iterable<R> findAll(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery, boolean stream) {
        if (preparedQuery.isCount()) {
            return Collections.singletonList(getCount(clientSession, preparedQuery));
        }
        if (preparedQuery.isAggregate()) {
            return findAllAggregated(clientSession, preparedQuery, stream);
        }
        return findAllFiltered(clientSession, preparedQuery, stream);
    }

    private <T, R> R findOneFiltered(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        return find(clientSession, preparedQuery)
                .limit(1)
                .map(r -> {
                    Class<T> type = preparedQuery.getRootEntity();
                    RuntimePersistentEntity<T> persistentEntity = preparedQuery.getPersistentEntity();
                    if (type.isInstance(r)) {
                        return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
                    }
                    return r;
                }).first();
    }

    private <T, R> R findOneAggregated(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        MongoDatabase database = getDatabase(preparedQuery);
        Class<T> type = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();
        if (!resultType.isAssignableFrom(type)) {
            BsonDocument result = aggregate(clientSession, preparedQuery, BsonDocument.class).first();
            return convertResult(database.getCodecRegistry(), resultType, result, preparedQuery.isDtoProjection());
        }
        return aggregate(clientSession, preparedQuery).map(r -> {
            RuntimePersistentEntity<T> persistentEntity = preparedQuery.getPersistentEntity();
            if (type.isInstance(r)) {
                return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
            }
            return r;
        }).first();
    }

    private <T, R> Iterable<R> findAllAggregated(ClientSession clientSession,
                                                 MongoPreparedQuery<T, R> preparedQuery,
                                                 boolean stream) {
        Pageable pageable = preparedQuery.getPageable();
        int limit = pageable == Pageable.UNPAGED ? -1 : pageable.getSize();
        Class<T> type = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();
        MongoIterable<R> aggregate;
        if (!resultType.isAssignableFrom(type)) {
            MongoDatabase database = getDatabase(preparedQuery);
            aggregate = aggregate(clientSession, preparedQuery, BsonDocument.class)
                    .map(result -> convertResult(database.getCodecRegistry(), resultType, result, preparedQuery.isDtoProjection()));
        } else {
            aggregate = aggregate(clientSession, preparedQuery, resultType);
        }
        return stream ? aggregate : aggregate.into(new ArrayList<>(limit > 0 ? limit : 20));
    }

    private <T, R> Iterable<R> findAllFiltered(ClientSession clientSession,
                                               MongoPreparedQuery<T, R> preparedQuery,
                                               boolean stream) {
        Pageable pageable = preparedQuery.getPageable();
        int limit = pageable == Pageable.UNPAGED ? -1 : pageable.getSize();
        Class<T> type = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();
        MongoIterable<R> findIterable;
        if (!resultType.isAssignableFrom(type)) {
            MongoDatabase database = getDatabase(preparedQuery);
            findIterable = find(clientSession, preparedQuery, BsonDocument.class)
                    .map(result -> convertResult(database.getCodecRegistry(), resultType, result, preparedQuery.isDtoProjection()));
        } else {
            findIterable = find(clientSession, preparedQuery);
        }
        return stream ? findIterable : findIterable.into(new ArrayList<>(limit > 0 ? limit : 20));
    }

    private <T, R> FindIterable<R> find(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        return find(clientSession, preparedQuery, preparedQuery.getResultType());
    }

    private <T, R, MR> FindIterable<MR> find(ClientSession clientSession,
                                             MongoPreparedQuery<T, R> preparedQuery,
                                             Class<MR> resultType) {
        MongoFind find = preparedQuery.getFind();
        if (QUERY_LOG.isDebugEnabled()) {
            logFind(find);
        }
        MongoDatabase database = getDatabase(preparedQuery);
        MongoCollection<MR> collection = getCollection(database, preparedQuery.getPersistentEntity(), resultType);
        FindIterable<MR> findIterable = collection.find(clientSession, resultType);
        return applyFindOptions(find.getOptions(), findIterable);
    }

    private <MR> FindIterable<MR> applyFindOptions(@Nullable MongoFindOptions findOptions, FindIterable<MR> findIterable) {
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

    private <T, R, MR> AggregateIterable<MR> aggregate(ClientSession clientSession,
                                                       MongoPreparedQuery<T, R> preparedQuery,
                                                       Class<MR> resultType) {
        MongoDatabase database = getDatabase(preparedQuery);
        MongoCollection<MR> collection = getCollection(database, preparedQuery.getPersistentEntity(), resultType);
        MongoAggregation aggregation = preparedQuery.getAggregation();
        if (QUERY_LOG.isDebugEnabled()) {
            logAggregate(aggregation);
        }
        AggregateIterable<MR> aggregateIterable = collection.aggregate(clientSession, aggregation.getPipeline(), resultType);
        return applyAggregateOptions(aggregation.getOptions(), aggregateIterable);
    }

    private <T, R> AggregateIterable<R> aggregate(ClientSession clientSession, MongoPreparedQuery<T, R> preparedQuery) {
        return aggregate(clientSession, preparedQuery, preparedQuery.getResultType());
    }

    private <MR> AggregateIterable<MR> applyAggregateOptions(@Nullable MongoAggregationOptions aggregateOptions, AggregateIterable<MR> aggregateIterable) {
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
        if (maxAwaitTimeMS != null) {
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

    @Override
    public <T> T persist(InsertOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return persistOne(ctx, operation.getEntity(), runtimeEntityRegistry.getEntity(operation.getRootEntity()));
        });
    }

    @Override
    public <T> Iterable<T> persistAll(InsertBatchOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return persistBatch(ctx, operation, runtimeEntityRegistry.getEntity(operation.getRootEntity()), null);
        });
    }

    @Override
    public <T> T update(UpdateOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getAnnotationMetadata(), operation.getRepositoryType());
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            if (storedQuery != null) {
                MongoStoredQuery<T, ?> mongoStoredQuery = getMongoStoredQuery(storedQuery);
                MongoEntitiesOperation<T> op = createMongoUpdateOneInBulkOperation(ctx, mongoStoredQuery.getRuntimePersistentEntity(),
                        Collections.singletonList(operation.getEntity()), mongoStoredQuery);
                op.update();
                return op.getEntities().iterator().next();
            }
            return updateOne(ctx, operation.getEntity(), runtimeEntityRegistry.getEntity(operation.getRootEntity()));
        });
    }

    @Override
    public <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getAnnotationMetadata(), operation.getRepositoryType());
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            if (storedQuery != null) {
                MongoStoredQuery<T, ?> mongoStoredQuery = getMongoStoredQuery(storedQuery);
                MongoEntitiesOperation<T> op = createMongoUpdateOneInBulkOperation(ctx, mongoStoredQuery.getRuntimePersistentEntity(), operation, mongoStoredQuery);
                op.update();
                return op.getEntities();
            }
            MongoEntitiesOperation<T> op = createMongoReplaceOneInBulkOperation(ctx, runtimeEntityRegistry.getEntity(operation.getRootEntity()), operation);
            op.update();
            return op.getEntities();
        });
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getAnnotationMetadata(), operation.getRepositoryType());
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            if (storedQuery != null) {
                MongoStoredQuery<T, ?> mongoStoredQuery = getMongoStoredQuery(storedQuery);
                MongoEntitiesOperation<T> op = createMongoDeleteOneInBulkOperation(ctx, mongoStoredQuery.getRuntimePersistentEntity(), Collections.singletonList(operation.getEntity()), mongoStoredQuery);
                op.delete();
                return (int) op.modifiedCount;
            }
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            MongoEntityOperation<T> op = createMongoDeleteOneOperation(ctx, persistentEntity, operation.getEntity());
            op.delete();
            return (int) op.modifiedCount;
        });
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getAnnotationMetadata(), operation.getRepositoryType());
            StoredQuery<T, ?> storedQuery = operation.getStoredQuery();
            if (storedQuery != null) {
                MongoStoredQuery<T, ?> mongoStoredQuery = getMongoStoredQuery(storedQuery);
                MongoEntitiesOperation<T> op = createMongoDeleteOneInBulkOperation(ctx, mongoStoredQuery.getRuntimePersistentEntity(), operation, mongoStoredQuery);
                op.delete();
                return Optional.of(op.modifiedCount);
            }
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            if (operation.all()) {
                MongoDatabase mongoDatabase = getDatabase(persistentEntity, operation.getRepositoryType());
                long deletedCount = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType()).deleteMany(EMPTY).getDeletedCount();
                return Optional.of(deletedCount);
            }
            MongoEntitiesOperation<T> op = createMongoDeleteManyOperation(ctx, persistentEntity, operation);
            op.delete();
            return Optional.of(op.modifiedCount);
        });
    }

    @Override
    public Optional<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return withClientSession(clientSession -> {
            MongoPreparedQuery<?, Number> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
            MongoUpdate updateMany = mongoPreparedQuery.getUpdateMany();
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'updateMany' with filter: {} and update: {}", updateMany.getFilter().toBsonDocument().toJson(), updateMany.getUpdate().toBsonDocument().toJson());
            }
            UpdateResult updateResult = getCollection(mongoPreparedQuery)
                    .updateMany(clientSession, updateMany.getFilter(), updateMany.getUpdate(), updateMany.getOptions());
            if (preparedQuery.isOptimisticLock()) {
                checkOptimisticLocking(1, (int) updateResult.getModifiedCount());
            }
            return Optional.of(updateResult.getModifiedCount());
        });
    }

    @Override
    public Optional<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return withClientSession(clientSession -> {
            MongoPreparedQuery<?, Number> mongoPreparedQuery = getMongoPreparedQuery(preparedQuery);
            MongoDelete deleteMany = mongoPreparedQuery.getDeleteMany();
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'deleteMany' with filter: {}", deleteMany.getFilter().toBsonDocument().toJson());
            }
            DeleteResult deleteResult = getCollection(mongoPreparedQuery).
                    deleteMany(clientSession, deleteMany.getFilter(), deleteMany.getOptions());
            if (preparedQuery.isOptimisticLock()) {
                checkOptimisticLocking(1, (int) deleteResult.getDeletedCount());
            }
            return Optional.of(deleteResult.getDeletedCount());
        });
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

    private <T, R> MongoCollection<R> getCollection(MongoDatabase database, RuntimePersistentEntity<T> persistentEntity, Class<R> resultType) {
        return database.getCollection(collectionNameProvider.provide(persistentEntity), resultType);
    }

    private <T, R> MongoCollection<R> getCollection(RuntimePersistentEntity<T> persistentEntity, Class<?> repositoryClass, Class<R> resultType) {
        return getDatabase(persistentEntity, repositoryClass).getCollection(collectionNameProvider.provide(persistentEntity), resultType);
    }

    @Override
    protected MongoDatabase getDatabase(PersistentEntity persistentEntity, Class<?> repositoryClass) {
        return mongoClient.getDatabase(databaseNameProvider.provide(persistentEntity, repositoryClass));
    }

    @Override
    protected CodecRegistry getCodecRegistry(MongoDatabase mongoDatabase) {
        return mongoDatabase.getCodecRegistry();
    }

    @Override
    public <T> T persistOne(MongoOperationContext ctx, T value, RuntimePersistentEntity<T> persistentEntity) {
        MongoEntityOperation<T> op = createMongoInsertOneOperation(ctx, persistentEntity, value);
        op.persist();
        return op.getEntity();
    }

    @Override
    public <T> List<T> persistBatch(MongoOperationContext ctx, Iterable<T> values, RuntimePersistentEntity<T> persistentEntity, Predicate<T> predicate) {
        MongoEntitiesOperation<T> op = createMongoInsertManyOperation(ctx, persistentEntity, values);
        if (predicate != null) {
            op.veto(predicate);
        }
        op.persist();
        return op.getEntities();
    }

    @Override
    public <T> T updateOne(MongoOperationContext ctx, T value, RuntimePersistentEntity<T> persistentEntity) {
        MongoEntityOperation<T> op = createMongoReplaceOneOperation(ctx, persistentEntity, value);
        op.update();
        return op.getEntity();
    }

    @Override
    public void persistManyAssociation(MongoOperationContext ctx,
                                       RuntimeAssociation runtimeAssociation,
                                       Object value,
                                       RuntimePersistentEntity<Object> persistentEntity,
                                       Object child,
                                       RuntimePersistentEntity<Object> childPersistentEntity) {
        String joinCollectionName = runtimeAssociation.getOwner().getNamingStrategy().mappedName(runtimeAssociation);
        MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
        MongoCollection<BsonDocument> collection = mongoDatabase.getCollection(joinCollectionName, BsonDocument.class);
        BsonDocument association = association(collection.getCodecRegistry(), value, persistentEntity, child, childPersistentEntity);
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'insertOne' for collection: {} with document: {}", collection.getNamespace().getFullName(), association);
        }
        collection.insertOne(ctx.clientSession, association, getInsertOneOptions(ctx.annotationMetadata));
    }

    @Override
    public void persistManyAssociationBatch(MongoOperationContext ctx, RuntimeAssociation runtimeAssociation,
                                            Object value,
                                            RuntimePersistentEntity<Object> persistentEntity,
                                            Iterable<Object> child,
                                            RuntimePersistentEntity<Object> childPersistentEntity) {
        String joinCollectionName = runtimeAssociation.getOwner().getNamingStrategy().mappedName(runtimeAssociation);
        MongoCollection<BsonDocument> collection = getDatabase(persistentEntity, ctx.repositoryType).getCollection(joinCollectionName, BsonDocument.class);
        List<BsonDocument> associations = new ArrayList<>();
        for (Object c : child) {
            associations.add(association(collection.getCodecRegistry(), value, persistentEntity, c, childPersistentEntity));
        }
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'insertMany' for collection: {} with documents: {}", collection.getNamespace().getFullName(), associations);
        }
        collection.insertMany(ctx.clientSession, associations, getInsertManyOptions(ctx.annotationMetadata));
    }

    private <T> T withClientSession(Function<ClientSession, T> function) {
        ClientSession clientSession = transactionManager.findClientSession();
        if (clientSession != null) {
            return function.apply(clientSession);
        }
        try (ClientSession cs = mongoClient.startSession()) {
            return function.apply(cs);
        }
    }

    private <T> MongoEntityOperation<T> createMongoInsertOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoEntityOperation<T>(ctx, persistentEntity, entity, true) {

            @Override
            protected void execute() throws RuntimeException {
                MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
                MongoCollection<T> collection = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType());
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'insertOne' with entity: {}", entity);
                }
                InsertOneResult insertOneResult = collection.insertOne(ctx.clientSession, entity, getInsertOneOptions(ctx.annotationMetadata));
                BsonValue insertedId = insertOneResult.getInsertedId();
                BeanProperty<T, Object> property = (BeanProperty<T, Object>) persistentEntity.getIdentity().getProperty();
                if (property.get(entity) == null) {
                    entity = updateEntityId(property, entity, insertedId);
                }
            }
        };
    }

    private <T> MongoEntityOperation<T> createMongoReplaceOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoEntityOperation<T>(ctx, persistentEntity, entity, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
            final MongoCollection<BsonDocument> collection = getCollection(mongoDatabase, persistentEntity, BsonDocument.class);
            Bson filter;

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                filter = createFilterIdAndVersion(persistentEntity, entity, mongoDatabase.getCodecRegistry());
            }

            @Override
            protected void execute() throws RuntimeException {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'replaceOne' with filter: {}", filter.toBsonDocument().toJson());
                }
                BsonDocument bsonDocument = BsonDocumentWrapper.asBsonDocument(entity, mongoDatabase.getCodecRegistry());
                bsonDocument.remove("_id");
                UpdateResult updateResult = collection.replaceOne(ctx.clientSession, filter, bsonDocument, getReplaceOptions(ctx.annotationMetadata));
                modifiedCount = updateResult.getModifiedCount();
                if (persistentEntity.getVersion() != null) {
                    checkOptimisticLocking(1, (int) modifiedCount);
                }
            }

        };
    }

    private <T> MongoEntitiesOperation<T> createMongoUpdateOneInBulkOperation(MongoOperationContext ctx,
                                                                              RuntimePersistentEntity<T> persistentEntity,
                                                                              Iterable<T> entities,
                                                                              MongoStoredQuery<T, ?> storedQuery) {
        return new MongoEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            @Override
            protected void collectAutoPopulatedPreviousValues() {
            }

            @Override
            protected void execute() throws RuntimeException {
                List<UpdateOneModel<T>> updates = new ArrayList<>(entities.size());
                for (Data d : entities) {
                    if (d.vetoed) {
                        continue;
                    }
                    MongoUpdate updateOne = storedQuery.getUpdateOne(d.entity);
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'updateOne' with filter: {} and update: {}", updateOne.getFilter().toBsonDocument().toJson(), updateOne.getUpdate().toBsonDocument().toJson());
                    }
                    updates.add(new UpdateOneModel<>(updateOne.getFilter(), updateOne.getUpdate(), updateOne.getOptions()));
                }
                BulkWriteResult bulkWriteResult = getCollection(ctx, persistentEntity).bulkWrite(ctx.clientSession, updates);
                modifiedCount += bulkWriteResult.getModifiedCount();
                if (persistentEntity.getVersion() != null) {
                    checkOptimisticLocking(updates.size(), (int) modifiedCount);
                }
            }
        };
    }

    private <T> MongoEntitiesOperation<T> createMongoReplaceOneInBulkOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
            final MongoCollection<BsonDocument> collection = getCollection(mongoDatabase, persistentEntity, BsonDocument.class);
            Map<Data, Bson> filters;

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                filters = entities.stream()
                        .collect(Collectors.toMap(d -> d, d -> createFilterIdAndVersion(persistentEntity, d.entity, collection.getCodecRegistry())));
            }

            @Override
            protected void execute() throws RuntimeException {
                List<ReplaceOneModel<BsonDocument>> replaces = new ArrayList<>(entities.size());
                for (Data d : entities) {
                    if (d.vetoed) {
                        continue;
                    }
                    Bson filter = filters.get(d);
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'replaceOne' with filter: {}", filter.toBsonDocument().toJson());
                    }
                    BsonDocument bsonDocument = BsonDocumentWrapper.asBsonDocument(d.entity, mongoDatabase.getCodecRegistry());
                    bsonDocument.remove("_id");
                    replaces.add(new ReplaceOneModel<>(filter, bsonDocument, getReplaceOptions(ctx.annotationMetadata)));
                }
                BulkWriteResult bulkWriteResult = collection.bulkWrite(ctx.clientSession, replaces);
                modifiedCount = bulkWriteResult.getModifiedCount();
                if (persistentEntity.getVersion() != null) {
                    checkOptimisticLocking(replaces.size(), (int) modifiedCount);
                }
            }
        };
    }

    private <T> MongoEntityOperation<T> createMongoDeleteOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoEntityOperation<T>(ctx, persistentEntity, entity, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
            final MongoCollection<T> collection = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType());
            Bson filter;

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                filter = createFilterIdAndVersion(persistentEntity, entity, collection.getCodecRegistry());
            }

            @Override
            protected void execute() throws RuntimeException {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'deleteOne' with filter: {}", filter.toBsonDocument().toJson());
                }
                DeleteResult deleteResult = collection.deleteOne(ctx.clientSession, filter, getDeleteOptions(ctx.annotationMetadata));
                modifiedCount = deleteResult.getDeletedCount();
                if (persistentEntity.getVersion() != null) {
                    checkOptimisticLocking(1, (int) modifiedCount);
                }
            }
        };
    }

    private <T> MongoEntitiesOperation<T> createMongoDeleteManyOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
            final MongoCollection<T> collection = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType());
            Map<Data, Bson> filters;

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                filters = entities.stream().collect(Collectors.toMap(d -> d, d -> createFilterIdAndVersion(persistentEntity, d.entity, collection.getCodecRegistry())));
            }

            @Override
            protected void execute() throws RuntimeException {
                List<Bson> filters = entities.stream().filter(d -> !d.vetoed).map(d -> this.filters.get(d)).collect(Collectors.toList());
                if (!filters.isEmpty()) {
                    Bson filter = Filters.or(filters);
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'deleteMany' with filter: {}", filter.toBsonDocument().toJson());
                    }
                    DeleteResult deleteResult = collection.deleteMany(ctx.clientSession, filter, getDeleteOptions(ctx.annotationMetadata));
                    modifiedCount = deleteResult.getDeletedCount();
                }
                if (persistentEntity.getVersion() != null) {
                    int expected = (int) entities.stream().filter(d -> !d.vetoed).count();
                    checkOptimisticLocking(expected, (int) modifiedCount);
                }
            }
        };
    }

    private <T> MongoEntitiesOperation<T> createMongoDeleteOneInBulkOperation(MongoOperationContext ctx,
                                                                              RuntimePersistentEntity<T> persistentEntity,
                                                                              Iterable<T> entities,
                                                                              MongoStoredQuery<T, ?> storedQuery) {
        return new MongoEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            @Override
            protected void execute() throws RuntimeException {
                List<DeleteOneModel<T>> deletes = new ArrayList<>(entities.size());
                for (Data d : entities) {
                    if (d.vetoed) {
                        continue;
                    }
                    MongoDelete deleteOne = storedQuery.getDeleteOne(d.entity);
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'deleteOne' with filter: {} ", deleteOne.getFilter().toBsonDocument().toJson());
                    }
                    deletes.add(new DeleteOneModel<>(deleteOne.getFilter(), deleteOne.getOptions()));
                }
                BulkWriteResult bulkWriteResult = getCollection(ctx, persistentEntity).bulkWrite(ctx.clientSession, deletes);
                modifiedCount = bulkWriteResult.getDeletedCount();
                if (persistentEntity.getVersion() != null) {
                    checkOptimisticLocking(deletes.size(), (int) modifiedCount);
                }
            }
        };
    }

    private <T> MongoEntitiesOperation<T> createMongoInsertManyOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoEntitiesOperation<T>(ctx, persistentEntity, entities, true) {

            @Override
            protected void execute() throws RuntimeException {
                List<T> toInsert = entities.stream().filter(d -> !d.vetoed).map(d -> d.entity).collect(Collectors.toList());
                if (toInsert.isEmpty()) {
                    return;
                }
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'insertMany' with entities: {}", toInsert);
                }
                MongoDatabase mongoDatabase = getDatabase(persistentEntity, ctx.repositoryType);
                InsertManyResult insertManyResult = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType())
                        .insertMany(ctx.clientSession, toInsert, getInsertManyOptions(ctx.annotationMetadata));
                if (hasGeneratedId) {
                    Map<Integer, BsonValue> insertedIds = insertManyResult.getInsertedIds();
                    RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                    BeanProperty<T, Object> idProperty = (BeanProperty<T, Object>) identity.getProperty();
                    int index = 0;
                    for (Data d : entities) {
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
            }
        };
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
    private ExecutorService newLocalThreadPool() {
        this.executorService = Executors.newCachedThreadPool();
        return executorService;
    }

    @NonNull
    @Override
    public ReactiveRepositoryOperations reactive() {
        return new ExecutorReactiveOperations(async(), conversionService);
    }

    private abstract class MongoEntityOperation<T> extends AbstractSyncEntityOperations<MongoOperationContext, T, RuntimeException> {

        protected long modifiedCount;

        /**
         * Create a new instance.
         *
         * @param ctx              The context
         * @param persistentEntity The RuntimePersistentEntity
         * @param entity           The entity instance
         * @param insert           Is insert operation
         */
        protected MongoEntityOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity, boolean insert) {
            super(ctx, DefaultMongoRepositoryOperations.this.cascadeOperations, DefaultMongoRepositoryOperations.this.entityEventRegistry, persistentEntity, DefaultMongoRepositoryOperations.this.conversionService, entity, insert);
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
        }
    }

    private abstract class MongoEntitiesOperation<T> extends AbstractSyncEntitiesOperations<MongoOperationContext, T, RuntimeException> {

        protected long modifiedCount;

        protected MongoEntitiesOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, boolean insert) {
            super(ctx, DefaultMongoRepositoryOperations.this.cascadeOperations, DefaultMongoRepositoryOperations.this.conversionService, DefaultMongoRepositoryOperations.this.entityEventRegistry, persistentEntity, entities, insert);
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
        }

    }

    protected static class MongoOperationContext extends OperationContext {

        private final ClientSession clientSession;

        public MongoOperationContext(ClientSession clientSession, AnnotationMetadata annotationMetadata, Class<?> repositoryType) {
            super(annotationMetadata, repositoryType);
            this.clientSession = clientSession;
        }
    }
}
