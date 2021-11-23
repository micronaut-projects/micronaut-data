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
package io.micronaut.data.mongo.operations;

import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.mongo.database.MongoDatabaseFactory;
import io.micronaut.data.mongo.transaction.MongoSynchronousTransactionManager;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntitiesOperations;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Named;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
@EachBean(MongoClient.class)
@Internal
public final class DefaultMongoRepositoryOperations extends AbstractRepositoryOperations<ClientSession, Object> implements
        MongoRepositoryOperations,
        AsyncCapableRepository,
        ReactiveCapableRepository,
        SyncCascadeOperations.SyncCascadeOperationsHelper<DefaultMongoRepositoryOperations.MongoOperationContext> {
    private static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    private static final BsonDocument EMPTY = new BsonDocument();
    private final MongoClient mongoClient;
    private final SyncCascadeOperations<MongoOperationContext> cascadeOperations;
    private final MongoSynchronousTransactionManager transactionManager;
    private final MongoDatabaseFactory mongoDatabaseFactory;
    private ExecutorAsyncOperations asyncOperations;
    private ExecutorService executorService;

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
     * @param mongoClient                The Mongo client
     * @param executorService            The executor service
     */
    protected DefaultMongoRepositoryOperations(@Parameter String serverName,
                                               BeanContext beanContext,
                                               List<MediaTypeCodec> codecs,
                                               DateTimeProvider<Object> dateTimeProvider,
                                               RuntimeEntityRegistry runtimeEntityRegistry,
                                               DataConversionService<?> conversionService,
                                               AttributeConverterRegistry attributeConverterRegistry,
                                               MongoClient mongoClient,
                                               @Named("io") @Nullable ExecutorService executorService) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.mongoClient = mongoClient;
        this.cascadeOperations = new SyncCascadeOperations<>(conversionService, this);
        boolean isPrimary = "Primary".equals(serverName);
        this.transactionManager = beanContext.getBean(MongoSynchronousTransactionManager.class, isPrimary ? null : Qualifiers.byName(serverName));
        this.mongoDatabaseFactory = beanContext.getBean(MongoDatabaseFactory.class, isPrimary ? null : Qualifiers.byName(serverName));
        this.executorService = executorService;
    }

    @Override
    public <T> T findOne(Class<T> type, Serializable id) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
            MongoDatabase database = getDatabase(persistentEntity);
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
            Class<T> type = preparedQuery.getRootEntity();
            Class<R> resultType = preparedQuery.getResultType();
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
            MongoDatabase database = getDatabase(persistentEntity);
            String query = preparedQuery.getQuery();
            Bson filter;
            List<BsonDocument> pipeline;
            if (query.startsWith("[")) {
                pipeline = getPipeline(database.getCodecRegistry(), preparedQuery, persistentEntity);
                filter = EMPTY;
            } else {
                pipeline = null;
                filter = getFilter(database.getCodecRegistry(), preparedQuery, persistentEntity);
            }
            if (preparedQuery.isCount() || query.contains("$count")) {
                return getCount(clientSession, database, type, resultType, persistentEntity, filter, pipeline);
            }
            if (pipeline == null) {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'find' with filter: {}", filter.toBsonDocument().toJson());
                }
                return getCollection(database, persistentEntity, resultType).find(clientSession, filter, resultType).limit(1).map(r -> {
                    if (type.isInstance(r)) {
                        return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
                    }
                    return r;
                }).first();
            } else {
                return findOneAggregated(clientSession, preparedQuery, type, resultType, persistentEntity, database, pipeline);
            }
        });
    }

    private <R> R convertResult(MongoDatabase mongoDatabase,
                                Class<R> resultType,
                                BsonDocument result,
                                boolean isDtoProjection) {
        BsonValue value;
        if (result == null) {
            value = BsonNull.VALUE;
        } else if (result.size() == 1) {
            value = result.values().iterator().next().asNumber();
        } else if (result.size() == 2) {
            value = result.entrySet().stream().filter(f -> !f.getKey().equals("_id")).findFirst().get().getValue();
        } else if (isDtoProjection) {
            Object dtoResult = MongoUtils.toValue(result.asDocument(), resultType, mongoDatabase.getCodecRegistry());
            if (resultType.isInstance(dtoResult)) {
                return (R) dtoResult;
            }
            return conversionService.convertRequired(dtoResult, resultType);
        } else {
            throw new IllegalStateException("Unrecognized result: " + result);
        }
        return conversionService.convertRequired(MongoUtils.toValue(value), resultType);
    }

    @Override
    public <T> boolean exists(PreparedQuery<T, Boolean> preparedQuery) {
        return withClientSession(clientSession -> {
            Class<T> type = preparedQuery.getRootEntity();
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
            MongoDatabase database = getDatabase(persistentEntity);
            String query = preparedQuery.getQuery();
            if (query.startsWith("[")) {
                List<BsonDocument> pipeline = getPipeline(database.getCodecRegistry(), preparedQuery, persistentEntity);
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", pipeline.stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
                }
                return getCollection(database, persistentEntity, persistentEntity.getIntrospection().getBeanType())
                        .aggregate(clientSession, pipeline)
                        .iterator().hasNext();
            } else {
                Bson filter = getFilter(database.getCodecRegistry(), preparedQuery, persistentEntity);
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing exists Mongo 'find' with filter: {}", filter.toBsonDocument().toJson());
                }
                return getCollection(database, persistentEntity, persistentEntity.getIntrospection().getBeanType())
                        .find(clientSession, type)
                        .limit(1).filter(filter).iterator().hasNext();
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
        return withClientSession(clientSession -> findAll(clientSession, preparedQuery, false));
    }

    private <T, R> Iterable<R> findAll(ClientSession clientSession, PreparedQuery<T, R> preparedQuery, boolean stream) {
        Pageable pageable = preparedQuery.getPageable();

        Class<T> type = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();
        RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(type);
        MongoDatabase database = getDatabase(persistentEntity);

        String query = preparedQuery.getQuery();
        Bson filter;
        List<BsonDocument> pipeline;
        if (query.startsWith("[")) {
            pipeline = getPipeline(database.getCodecRegistry(), preparedQuery, persistentEntity);
            filter = EMPTY;
        } else {
            pipeline = null;
            filter = getFilter(database.getCodecRegistry(), preparedQuery, persistentEntity);
        }

        if (preparedQuery.isCount() || query.contains("$count")) {
            return Collections.singletonList(getCount(clientSession, database, type, resultType, persistentEntity, filter, pipeline));
        }
        if (pipeline == null) {
            return findAll(clientSession, database, pageable, resultType, persistentEntity, filter, stream);
        }
        return findAllAggregated(clientSession, database, pageable, resultType, preparedQuery.isDtoProjection(), persistentEntity, pipeline, stream);
    }

    @Override
    public <T, R> Stream<R> findStream(PreparedQuery<T, R> preparedQuery) {
        return withClientSession(clientSession -> {
            MongoIterable<R> iterable = (MongoIterable<R>) findAll(clientSession, preparedQuery, true);
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

    private <T, R> R findOneAggregated(ClientSession clientSession, PreparedQuery<T, R> preparedQuery, Class<T> type, Class<R> resultType, RuntimePersistentEntity<T> persistentEntity, MongoDatabase database, List<BsonDocument> pipeline) {
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", pipeline.stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
        }
        boolean isProjection = pipeline.stream().anyMatch(stage -> stage.containsKey("$group") || stage.containsKey("$project"));
        if (isProjection) {
            BsonDocument result = getCollection(database, persistentEntity, BsonDocument.class).aggregate(clientSession, pipeline, BsonDocument.class).first();
            return convertResult(database, resultType, result, preparedQuery.isDtoProjection());
        }
        return getCollection(database, persistentEntity, resultType).aggregate(clientSession, pipeline).map(r -> {
            if (type.isInstance(r)) {
                return (R) triggerPostLoad(preparedQuery.getAnnotationMetadata(), persistentEntity, type.cast(r));
            }
            return r;
        }).first();
    }

    private <T, R> Iterable<R> findAllAggregated(ClientSession clientSession,
                                                 MongoDatabase database,
                                                 Pageable pageable,
                                                 Class<R> resultType,
                                                 boolean isDtoProjection,
                                                 RuntimePersistentEntity<T> persistentEntity,
                                                 List<BsonDocument> pipeline,
                                                 boolean stream) {
        int limit = 0;
        if (pageable != Pageable.UNPAGED) {
            int skip = (int) pageable.getOffset();
            limit = pageable.getSize();
            Sort pageableSort = pageable.getSort();
            if (pageableSort.isSorted()) {
                Bson sort = pageableSort.getOrderBy().stream().map(order -> order.isAscending() ? Sorts.ascending(order.getProperty()) : Sorts.descending(order.getProperty())).collect(Collectors.collectingAndThen(Collectors.toList(), Sorts::orderBy));
                BsonDocument sortStage = new BsonDocument().append("$sort", sort.toBsonDocument());
                addStageToPipelineBefore(pipeline, sortStage, "$limit", "$skip");
            }
            if (skip > 0) {
                pipeline.add(new BsonDocument().append("$skip", new BsonInt32(skip)));
            }
            if (limit > 0) {
                pipeline.add(new BsonDocument().append("$limit", new BsonInt32(limit)));
            }
        }
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", pipeline.stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
        }
        boolean isProjection = pipeline.stream().anyMatch(stage -> stage.containsKey("$group") || stage.containsKey("$project"));
        MongoIterable<R> aggregate;
        if (isProjection) {
            aggregate = getCollection(database, persistentEntity, BsonDocument.class)
                    .aggregate(clientSession, pipeline, BsonDocument.class)
                    .map(result -> convertResult(database, resultType, result, isDtoProjection));
        } else {
            aggregate = getCollection(database, persistentEntity, resultType).aggregate(clientSession, pipeline, resultType);
        }
        return stream ? aggregate : aggregate.into(new ArrayList<>(limit > 0 ? limit : 20));
    }

    private <T, R> Iterable<R> findAll(ClientSession clientSession,
                                       MongoDatabase database,
                                       Pageable pageable,
                                       Class<R> resultType,
                                       RuntimePersistentEntity<T> persistentEntity,
                                       Bson filter,
                                       boolean stream) {
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
        FindIterable<R> findIterable = getCollection(database, persistentEntity, resultType)
                .find(clientSession, filter, resultType)
                .skip(skip)
                .limit(Math.max(limit, 0))
                .sort(sort);
        return stream ? findIterable : findIterable.into(new ArrayList<>(limit > 0 ? limit : 20));
    }

    private <T, R> R getCount(ClientSession clientSession, MongoDatabase mongoDatabase, Class<T> type, Class<R> resultType, RuntimePersistentEntity<T> persistentEntity, Bson filter, List<BsonDocument> pipeline) {
        if (pipeline == null) {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'countDocuments' with filter: {}", filter.toBsonDocument().toJson());
            }
            long count = getCollection(mongoDatabase, persistentEntity, BsonDocument.class)
                    .countDocuments(clientSession, filter);
            return conversionService.convertRequired(count, resultType);
        } else {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'aggregate' with pipeline: {}", pipeline.stream().map(e -> e.toBsonDocument().toJson()).collect(Collectors.toList()));
            }
            R result = getCollection(mongoDatabase, persistentEntity, type)
                    .aggregate(clientSession, pipeline, BsonDocument.class)
                    .map(bsonDocument -> convertResult(mongoDatabase, resultType, bsonDocument, false))
                    .first();
            if (result == null) {
                result = conversionService.convertRequired(0, resultType);
            }
            return result;
        }
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
            return updateOne(ctx, operation.getEntity(), runtimeEntityRegistry.getEntity(operation.getRootEntity()));
        });
    }

    @Override
    public <T> Iterable<T> updateAll(UpdateBatchOperation<T> operation) {
        return withClientSession(clientSession -> {
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return updateBatch(ctx, operation, runtimeEntityRegistry.getEntity(operation.getRootEntity()));
        });
    }

    @Override
    public <T> int delete(DeleteOperation<T> operation) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getAnnotationMetadata(), operation.getRepositoryType());
            MongoEntityOperation<T> op = createMongoDeleteOneOperation(ctx, persistentEntity, operation.getEntity());
            op.delete();
            return (int) op.modifiedCount;
        });
    }

    @Override
    public <T> Optional<Number> deleteAll(DeleteBatchOperation<T> operation) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<T> persistentEntity = runtimeEntityRegistry.getEntity(operation.getRootEntity());
            if (operation.all()) {
                MongoDatabase mongoDatabase = getDatabase(persistentEntity);
                long deletedCount = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType()).deleteMany(EMPTY).getDeletedCount();
                return Optional.of(deletedCount);
            }
            MongoOperationContext ctx = new MongoOperationContext(clientSession, operation.getAnnotationMetadata(), operation.getRepositoryType());
            MongoEntitiesOperation<T> op = createMongoDeleteManyOperation(ctx, persistentEntity, operation);
            op.delete();
            return Optional.of(op.modifiedCount);
        });
    }

    @Override
    public Optional<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
            MongoDatabase database = getDatabase(persistentEntity);
            Bson update = getUpdate(database.getCodecRegistry(), preparedQuery, persistentEntity);
            Bson filter = getFilter(database.getCodecRegistry(), preparedQuery, persistentEntity);
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'updateMany' with filter: {} and update: {}", filter.toBsonDocument().toJson(), update.toBsonDocument().toJson());
            }
            UpdateResult updateResult = getCollection(database, persistentEntity, persistentEntity.getIntrospection().getBeanType()).updateMany(clientSession, filter, update);
            if (preparedQuery.isOptimisticLock()) {
                checkOptimisticLocking(1, (int) updateResult.getModifiedCount());
            }
            return Optional.of(updateResult.getModifiedCount());
        });
    }

    @Override
    public Optional<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return withClientSession(clientSession -> {
            RuntimePersistentEntity<?> persistentEntity = runtimeEntityRegistry.getEntity(preparedQuery.getRootEntity());
            MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            Bson filter = getFilter(mongoDatabase.getCodecRegistry(), preparedQuery, persistentEntity);
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Mongo 'deleteMany' with filter: {}", filter.toBsonDocument().toJson());
            }
            DeleteResult deleteResult = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType()).deleteMany(clientSession, filter);
            if (preparedQuery.isOptimisticLock()) {
                checkOptimisticLocking(1, (int) deleteResult.getDeletedCount());
            }
            return Optional.of(deleteResult.getDeletedCount());
        });
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

    private void addStageToPipelineBefore(List<BsonDocument> pipeline, BsonDocument stageToAdd, String... beforeStages) {
        int lastFoundIndex = -1;
        int index = 0;
        for (BsonDocument stage : pipeline) {
            for (String beforeStageName : beforeStages) {
                if (stage.containsKey(beforeStageName)) {
                    lastFoundIndex = index;
                    break;
                }
            }
            index++;
        }
        if (lastFoundIndex > -1) {
            pipeline.add(lastFoundIndex, stageToAdd);
        } else {
            pipeline.add(stageToAdd);
        }
    }

    private <T> Bson getUpdate(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<T> persistentEntity) {
        String query = preparedQuery.getUpdate();
        if (query == null) {
            throw new IllegalArgumentException("Update query is not provided!");
        }
        return getQuery(codecRegistry, preparedQuery, persistentEntity, query);
    }

    private <T> Bson getFilter(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<T> persistentEntity) {
        String query = preparedQuery.getQuery();
        return getQuery(codecRegistry, preparedQuery, persistentEntity, query);
    }

    private <T> List<BsonDocument> getPipeline(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<T> persistentEntity) {
        String query = preparedQuery.getQuery();
        BsonArray bsonArray = BsonArray.parse(query);
        bsonArray = (BsonArray) replaceQueryParameters(codecRegistry, preparedQuery, persistentEntity, bsonArray);
        return bsonArray.stream().map(BsonValue::asDocument).collect(Collectors.toList());
    }

    private <T> BsonDocument getQuery(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<T> persistentEntity, String query) {
        if (StringUtils.isEmpty(query)) {
            return EMPTY;
        }
        BsonDocument bsonDocument = BsonDocument.parse(query);
        bsonDocument = (BsonDocument) replaceQueryParameters(codecRegistry, preparedQuery, persistentEntity, bsonDocument);
        return bsonDocument;
    }

    private <T> BsonValue replaceQueryParameters(CodecRegistry codecRegistry, PreparedQuery<?, ?> preparedQuery, RuntimePersistentEntity<T> persistentEntity, BsonValue value) {
        if (value instanceof BsonDocument) {
            BsonDocument bsonDocument = (BsonDocument) value;
            BsonInt32 queryParameterIndex = bsonDocument.getInt32("$qpidx", null);
            if (queryParameterIndex != null) {
                int index = queryParameterIndex.getValue();
                return getValue(index, preparedQuery.getQueryBindings().get(index), preparedQuery, persistentEntity, codecRegistry);
            }

            for (Map.Entry<String, BsonValue> entry : bsonDocument.entrySet()) {
                BsonValue bsonValue = entry.getValue();
                BsonValue newValue = replaceQueryParameters(codecRegistry, preparedQuery, persistentEntity, bsonValue);
                if (bsonValue != newValue) {
                    entry.setValue(newValue);
                }
            }
            return bsonDocument;
        } else if (value instanceof BsonArray) {
            BsonArray bsonArray = (BsonArray) value;
            for (int i = 0; i < bsonArray.size(); i++) {
                BsonValue bsonValue = bsonArray.get(i);
                BsonValue newValue = replaceQueryParameters(codecRegistry, preparedQuery, persistentEntity, bsonValue);
                if (bsonValue != newValue) {
                    if (newValue.isNull()) {
                        bsonArray.remove(i);
                        i -= 1;
                    } else if (newValue.isArray()) {
                        bsonArray.remove(i);
                        List<BsonValue> values = newValue.asArray().getValues();
                        bsonArray.addAll(i, values);
                        i += values.size() - 1;
                    } else {
                        bsonArray.set(i, newValue);
                    }
                }
            }
        }
        return value;
    }

    private <T> BsonValue getValue(int index,
                                   QueryParameterBinding queryParameterBinding,
                                   PreparedQuery<?, ?> preparedQuery,
                                   RuntimePersistentEntity<T> persistentEntity,
                                   CodecRegistry codecRegistry) {
        Class<?> parameterConverter = queryParameterBinding.getParameterConverterClass();
        Object value;
        if (queryParameterBinding.getParameterIndex() != -1) {
            value = resolveParameterValue(queryParameterBinding, preparedQuery.getParameterArray());
        } else if (queryParameterBinding.isAutoPopulated()) {
            PersistentPropertyPath pp = getRequiredPropertyPath(queryParameterBinding, persistentEntity);
            RuntimePersistentProperty<?> persistentProperty = (RuntimePersistentProperty) pp.getProperty();
            Object previousValue = null;
            QueryParameterBinding previousPopulatedValueParameter = queryParameterBinding.getPreviousPopulatedValueParameter();
            if (previousPopulatedValueParameter != null) {
                if (previousPopulatedValueParameter.getParameterIndex() == -1) {
                    throw new IllegalStateException("Previous value parameter cannot be bind!");
                }
                previousValue = resolveParameterValue(previousPopulatedValueParameter, preparedQuery.getParameterArray());
            }
            value = runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
            value = convert(value, persistentProperty);
            parameterConverter = null;
        } else {
            throw new IllegalStateException("Invalid query [" + "]. Unable to establish parameter value for parameter at position: " + (index + 1));
        }

        DataType dataType = queryParameterBinding.getDataType();
        List<Object> values = expandValue(value, dataType);
        if (values != null && values.isEmpty()) {
            // Empty collections / array should always set at least one value
            value = null;
            values = null;
        }
        if (values == null) {
            if (parameterConverter != null) {
                int parameterIndex = queryParameterBinding.getParameterIndex();
                Argument<?> argument = parameterIndex > -1 ? preparedQuery.getArguments()[parameterIndex] : null;
                value = convert(parameterConverter, value, argument);
            }
            if (value instanceof String) {
                PersistentPropertyPath pp = getRequiredPropertyPath(queryParameterBinding, persistentEntity);
                RuntimePersistentProperty<?> persistentProperty = (RuntimePersistentProperty) pp.getProperty();
                if (persistentProperty instanceof RuntimeAssociation) {
                    RuntimeAssociation runtimeAssociation = (RuntimeAssociation) persistentProperty;
                    RuntimePersistentProperty identity = runtimeAssociation.getAssociatedEntity().getIdentity();
                    if (identity != null && identity.getType() == String.class && identity.isGenerated()) {
                        return new BsonObjectId(new ObjectId((String) value));
                    }
                }
                if (persistentProperty.getOwner().getIdentity() == persistentProperty && persistentProperty.getType() == String.class && persistentProperty.isGenerated()) {
                    return new BsonObjectId(new ObjectId((String) value));
                }
            }
            return MongoUtils.toBsonValue(conversionService, value, codecRegistry);
        } else {
            Class<?> finalParameterConverter = parameterConverter;
            return new BsonArray(values.stream().map(val -> {
                if (finalParameterConverter != null) {
                    int parameterIndex = queryParameterBinding.getParameterIndex();
                    Argument<?> argument = parameterIndex > -1 ? preparedQuery.getArguments()[parameterIndex] : null;
                    val = convert(finalParameterConverter, val, argument);
                }
                return MongoUtils.toBsonValue(conversionService, val, codecRegistry);
            }).collect(Collectors.toList()));
        }
    }

    private Object convert(Class<?> converterClass, Object value, @Nullable Argument<?> argument) {
        if (converterClass == null) {
            return value;
        }
        AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
        ConversionContext conversionContext = createTypeConversionContext(null, null, argument);
        return converter.convertToPersistedValue(value, conversionContext);
    }

    private Object convert(Object value, RuntimePersistentProperty<?> property) {
        AttributeConverter<Object, Object> converter = property.getConverter();
        if (converter != null) {
            return converter.convertToPersistedValue(value, createTypeConversionContext(null, property, property.getArgument()));
        }
        return value;
    }

    private <T> PersistentPropertyPath getRequiredPropertyPath(QueryParameterBinding queryParameterBinding, RuntimePersistentEntity<T> persistentEntity) {
        String[] propertyPath = queryParameterBinding.getRequiredPropertyPath();
        PersistentPropertyPath pp = persistentEntity.getPropertyPath(propertyPath);
        if (pp == null) {
            throw new IllegalStateException("Cannot find auto populated property: " + String.join(".", propertyPath));
        }
        return pp;
    }

    private List<Object> expandValue(Object value, DataType dataType) {
        // Special case for byte array, we want to support a list of byte[] convertible values
        if (value == null || dataType != null && dataType.isArray() && dataType != DataType.BYTE_ARRAY || value instanceof byte[]) {
            // not expanded
            return null;
        } else if (value instanceof Iterable) {
            return (List<Object>) CollectionUtils.iterableToList((Iterable<?>) value);
        } else if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len == 0) {
                return Collections.emptyList();
            } else {
                List<Object> list = new ArrayList<>(len);
                for (int j = 0; j < len; j++) {
                    Object o = Array.get(value, j);
                    list.add(o);
                }
                return list;
            }
        } else {
            // not expanded
            return null;
        }
    }

    private Object resolveParameterValue(QueryParameterBinding queryParameterBinding, Object[] parameterArray) {
        Object value;
        value = parameterArray[queryParameterBinding.getParameterIndex()];
        String[] parameterBindingPath = queryParameterBinding.getParameterBindingPath();
        if (parameterBindingPath != null) {
            for (String prop : parameterBindingPath) {
                if (value == null) {
                    return null;
                }
                Object finalValue = value;
                BeanProperty beanProperty = BeanIntrospection.getIntrospection(value.getClass())
                        .getProperty(prop).orElseThrow(() -> new IntrospectionException("Cannot find a property: '" + prop + "' on bean: " + finalValue));
                value = beanProperty.get(value);
            }
        }
        return value;
    }

    @Override
    protected ConversionContext createTypeConversionContext(ClientSession connection, RuntimePersistentProperty<?> property, Argument<?> argument) {
        if (argument != null) {
            return ConversionContext.of(argument);
        }
        return ConversionContext.DEFAULT;
    }

    @Override
    public void setStatementParameter(Object preparedStatement, int index, DataType dataType, Object value, Dialect dialect) {

    }

    private <T, R> MongoCollection<R> getCollection(MongoDatabase database, RuntimePersistentEntity<T> persistentEntity, Class<R> resultType) {
        return database.getCollection(persistentEntity.getPersistedName(), resultType);
    }

    private MongoDatabase getDatabase(PersistentEntity persistentEntity) {
        return mongoDatabaseFactory.getDatabase(persistentEntity);
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

    private <T> List<T> updateBatch(MongoOperationContext ctx, Iterable<T> values, RuntimePersistentEntity<T> persistentEntity) {
        MongoEntitiesOperation<T> op = createMongoReplaceManyOperation(ctx, persistentEntity, values);
        op.update();
        return op.getEntities();
    }

    @Override
    public void persistManyAssociation(MongoOperationContext ctx,
                                       RuntimeAssociation runtimeAssociation,
                                       Object value,
                                       RuntimePersistentEntity<Object> persistentEntity,
                                       Object child,
                                       RuntimePersistentEntity<Object> childPersistentEntity) {
        String joinCollectionName = runtimeAssociation.getOwner().getNamingStrategy().mappedName(runtimeAssociation);
        MongoDatabase mongoDatabase = getDatabase(persistentEntity);
        MongoCollection<BsonDocument> collection = mongoDatabase.getCollection(joinCollectionName, BsonDocument.class);
        BsonDocument association = association(collection, value, persistentEntity, child, childPersistentEntity);
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'insertOne' for collection: {} with document: {}", collection.getNamespace().getFullName(), association);
        }
        collection.insertOne(ctx.clientSession, association);
    }

    @Override
    public void persistManyAssociationBatch(MongoOperationContext ctx, RuntimeAssociation runtimeAssociation,
                                            Object value,
                                            RuntimePersistentEntity<Object> persistentEntity,
                                            Iterable<Object> child,
                                            RuntimePersistentEntity<Object> childPersistentEntity) {
        String joinCollectionName = runtimeAssociation.getOwner().getNamingStrategy().mappedName(runtimeAssociation);
        MongoCollection<BsonDocument> collection = getDatabase(persistentEntity).getCollection(joinCollectionName, BsonDocument.class);
        List<BsonDocument> associations = new ArrayList<>();
        for (Object c : child) {
            associations.add(association(collection, value, persistentEntity, c, childPersistentEntity));
        }
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Mongo 'insertMany' for collection: {} with documents: {}", collection.getNamespace().getFullName(), associations);
        }
        collection.insertMany(ctx.clientSession, associations);
    }

    private BsonDocument association(MongoCollection<BsonDocument> collection,
                                     Object value, RuntimePersistentEntity<Object> persistentEntity,
                                     Object child, RuntimePersistentEntity<Object> childPersistentEntity) {
        BsonDocument document = new BsonDocument();
        document.put(persistentEntity.getPersistedName(), MongoUtils.entityIdValue(conversionService, persistentEntity, value, collection.getCodecRegistry()));
        document.put(childPersistentEntity.getPersistedName(), MongoUtils.entityIdValue(conversionService, childPersistentEntity, child, collection.getCodecRegistry()));
        return document;
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
                MongoDatabase mongoDatabase = getDatabase(persistentEntity);
                MongoCollection<T> collection = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType());
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'insertOne' with entity: {}", entity);
                }
                InsertOneResult insertOneResult = collection.insertOne(ctx.clientSession, entity);
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

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            final MongoCollection<BsonDocument> collection = getCollection(mongoDatabase, persistentEntity, BsonDocument.class);
            Bson filter;

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                filter = MongoUtils.filterByIdAndVersion(conversionService, persistentEntity, entity, collection.getCodecRegistry());
            }

            @Override
            protected void execute() throws RuntimeException {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'replaceOne' with filter: {}", filter.toBsonDocument().toJson());
                }
                BsonDocument bsonDocument = BsonDocumentWrapper.asBsonDocument(entity, mongoDatabase.getCodecRegistry());
                bsonDocument.remove("_id");
                UpdateResult updateResult = collection.replaceOne(ctx.clientSession, filter, bsonDocument);
                modifiedCount = updateResult.getModifiedCount();
                if (persistentEntity.getVersion() != null) {
                    checkOptimisticLocking(1, (int) modifiedCount);
                }
            }
        };
    }

    private <T> MongoEntitiesOperation<T> createMongoReplaceManyOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            final MongoCollection<BsonDocument> collection = getCollection(mongoDatabase, persistentEntity, BsonDocument.class);
            Map<Data, Bson> filters;

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                filters = entities.stream()
                        .collect(Collectors.toMap(d -> d, d -> MongoUtils.filterByIdAndVersion(conversionService, persistentEntity, d.entity, collection.getCodecRegistry())));
            }

            @Override
            protected void execute() throws RuntimeException {
                int expectedToBeUpdated = 0;
                for (Data d : entities) {
                    if (d.vetoed) {
                        continue;
                    }
                    expectedToBeUpdated++;
                    Bson filter = filters.get(d);
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'replaceOne' with filter: {}", filter.toBsonDocument().toJson());
                    }
                    BsonDocument bsonDocument = BsonDocumentWrapper.asBsonDocument(d.entity, mongoDatabase.getCodecRegistry());
                    bsonDocument.remove("_id");
                    UpdateResult updateResult = collection.replaceOne(ctx.clientSession, filter, bsonDocument);
                    modifiedCount += updateResult.getModifiedCount();
                }
                if (persistentEntity.getVersion() != null) {
                    checkOptimisticLocking(expectedToBeUpdated, (int) modifiedCount);
                }
            }
        };
    }

    private <T> MongoEntityOperation<T> createMongoDeleteOneOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity) {
        return new MongoEntityOperation<T>(ctx, persistentEntity, entity, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            final MongoCollection<T> collection = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType());
            Bson filter;

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                filter = MongoUtils.filterByIdAndVersion(conversionService, persistentEntity, entity, collection.getCodecRegistry());
            }

            @Override
            protected void execute() throws RuntimeException {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Mongo 'deleteOne' with filter: {}", filter.toBsonDocument().toJson());
                }
                DeleteResult deleteResult = collection.deleteOne(ctx.clientSession, filter);
                modifiedCount = deleteResult.getDeletedCount();
                if (persistentEntity.getVersion() != null) {
                    checkOptimisticLocking(1, (int) modifiedCount);
                }
            }
        };
    }

    private <T> MongoEntitiesOperation<T> createMongoDeleteManyOperation(MongoOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities) {
        return new MongoEntitiesOperation<T>(ctx, persistentEntity, entities, false) {

            final MongoDatabase mongoDatabase = getDatabase(persistentEntity);
            final MongoCollection<T> collection = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType());
            Map<Data, Bson> filters;

            @Override
            protected void collectAutoPopulatedPreviousValues() {
                filters = entities.stream().collect(Collectors.toMap(d -> d, d -> MongoUtils.filterByIdAndVersion(conversionService, persistentEntity, d.entity, collection.getCodecRegistry())));
            }

            @Override
            protected void execute() throws RuntimeException {
                List<Bson> filters = entities.stream().filter(d -> !d.vetoed).map(d -> this.filters.get(d)).collect(Collectors.toList());
                if (!filters.isEmpty()) {
                    Bson filter = Filters.or(filters);
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Mongo 'deleteMany' with filter: {}", filter.toBsonDocument().toJson());
                    }
                    DeleteResult deleteResult = collection.deleteMany(ctx.clientSession, filter);
                    modifiedCount = deleteResult.getDeletedCount();
                }
                if (persistentEntity.getVersion() != null) {
                    int expected = (int) entities.stream().filter(d -> !d.vetoed).count();
                    checkOptimisticLocking(expected, (int) modifiedCount);
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
                MongoDatabase mongoDatabase = getDatabase(persistentEntity);
                InsertManyResult insertManyResult = getCollection(mongoDatabase, persistentEntity, persistentEntity.getIntrospection().getBeanType())
                        .insertMany(ctx.clientSession, toInsert);
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
