/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.jdbc.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.jdbc.mapper.ColumnIndexResultSetReader;
import io.micronaut.data.jdbc.mapper.ColumnNameResultSetReader;
import io.micronaut.data.jdbc.mapper.JdbcQueryStatement;
import io.micronaut.data.jdbc.mapper.SqlResultConsumer;
import io.micronaut.data.jdbc.runtime.ConnectionCallback;
import io.micronaut.data.jdbc.runtime.PreparedStatementCallback;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
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
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.mapper.DTOMapper;
import io.micronaut.data.runtime.mapper.ResultConsumer;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.TypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlDTOMapper;
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.transaction.TransactionOperations;

import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implementation of {@link JdbcRepositoryOperations}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachBean(DataSource.class)
public class DefaultJdbcRepositoryOperations extends AbstractSqlRepositoryOperations<ResultSet, PreparedStatement> implements
        JdbcRepositoryOperations,
        AsyncCapableRepository,
        ReactiveCapableRepository,
        AutoCloseable {

    private final TransactionOperations<Connection> transactionOperations;
    private final DataSource dataSource;
    private ExecutorAsyncOperations asyncOperations;
    private ExecutorService executorService;

    /**
     * Default constructor.
     *
     * @param dataSourceName        The data source name
     * @param dataSource            The datasource
     * @param transactionOperations The JDBC operations for the data source
     * @param executorService       The executor service
     * @param beanContext           The bean context
     * @param codecs                The codecs
     * @param entityRegistry        The entity registry
     */
    @Internal
    protected DefaultJdbcRepositoryOperations(@Parameter String dataSourceName,
                                              DataSource dataSource,
                                              @Parameter TransactionOperations<Connection> transactionOperations,
                                              @Named("io") @Nullable ExecutorService executorService,
                                              BeanContext beanContext,
                                              List<MediaTypeCodec> codecs,
                                              RuntimeEntityRegistry entityRegistry) {
        super(
                dataSourceName,
                new ColumnNameResultSetReader(),
                new ColumnIndexResultSetReader(),
                new JdbcQueryStatement(),
                codecs,
                entityRegistry,
                beanContext
        );
        ArgumentUtils.requireNonNull("dataSource", dataSource);
        ArgumentUtils.requireNonNull("transactionOperations", transactionOperations);
        this.dataSource = dataSource;
        this.transactionOperations = transactionOperations;
        this.executorService = executorService;
    }

    @NonNull
    private ExecutorService newLocalThreadPool() {
        this.executorService = Executors.newCachedThreadPool();
        return executorService;
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
        return new ExecutorReactiveOperations(async());
    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return transactionOperations.executeRead(status -> {
            Connection connection = status.getConnection();
            RuntimePersistentEntity<T> persistentEntity = getEntity(preparedQuery.getRootEntity());
            try (PreparedStatement ps = prepareStatement(connection::prepareStatement, preparedQuery, false, true)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Class<R> resultType = preparedQuery.getResultType();
                        if (preparedQuery.getResultDataType() == DataType.ENTITY) {
                            RuntimePersistentEntity<R> resultPersistentEntity = getEntity(resultType);

                            final Set<JoinPath> joinFetchPaths = preparedQuery.getJoinFetchPaths();
                            SqlResultEntityTypeMapper<ResultSet, R> mapper = new SqlResultEntityTypeMapper<>(
                                    resultPersistentEntity,
                                    columnNameResultSetReader,
                                    joinFetchPaths,
                                    jsonCodec,
                                    (loadedEntity, o) -> {
                                        if (loadedEntity.hasPostLoadEventListeners()) {
                                            return triggerPostLoad(o, loadedEntity, preparedQuery.getAnnotationMetadata());
                                        } else {
                                            return o;
                                        }
                                    }
                            );
                            R result = mapper.readOneWithJoins(rs);
                            if (preparedQuery.hasResultConsumer()) {
                                preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class)
                                        .ifPresent(consumer -> consumer.accept(result, newMappingContext(rs)));
                            }
                            return result;
                        } else {
                            if (preparedQuery.isDtoProjection()) {
                                TypeMapper<ResultSet, R> introspectedDataMapper = new DTOMapper<>(
                                        persistentEntity,
                                        columnNameResultSetReader,
                                        jsonCodec
                                );

                                return introspectedDataMapper.map(rs, resultType);
                            } else {
                                Object v = columnIndexResultSetReader.readDynamic(rs, 1, preparedQuery.getResultDataType());
                                if (v == null) {
                                    return null;
                                } else if (resultType.isInstance(v)) {
                                    return (R) v;
                                } else {
                                    return columnIndexResultSetReader.convertRequired(v, resultType);
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL Query: " + e.getMessage(), e);
            }
            return null;
        });
    }

    @NonNull
    private ResultConsumer.Context<ResultSet> newMappingContext(ResultSet rs) {
        return new ResultConsumer.Context<ResultSet>() {
            @Override
            public ResultSet getResultSet() {
                return rs;
            }

            @Override
            public ResultReader<ResultSet, String> getResultReader() {
                return columnNameResultSetReader;
            }

            @NonNull
            @Override
            public <E> E readEntity(String prefix, Class<E> type) throws DataAccessException {
                RuntimePersistentEntity<E> entity = getEntity(type);
                SqlResultEntityTypeMapper<ResultSet, E> mapper = new SqlResultEntityTypeMapper<>(
                        prefix,
                        entity,
                        columnNameResultSetReader,
                        jsonCodec
                );
                return mapper.readOneWithJoins(rs);
            }

            @NonNull
            @Override
            public <E, D> D readDTO(@NonNull String prefix, @NonNull Class<E> rootEntity, @NonNull Class<D> dtoType) throws DataAccessException {
                RuntimePersistentEntity<E> entity = getEntity(rootEntity);
                TypeMapper<ResultSet, D> introspectedDataMapper = new DTOMapper<>(
                        entity,
                        columnNameResultSetReader,
                        jsonCodec
                );
                return introspectedDataMapper.map(rs, dtoType);
            }
        };
    }

    @Override
    public <T> boolean exists(@NonNull PreparedQuery<T, Boolean> preparedQuery) {
        return transactionOperations.executeRead(status -> {
            try {
                Connection connection = status.getConnection();
                try (PreparedStatement ps = prepareStatement(connection::prepareStatement, preparedQuery, false, true)) {
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL query: " + e.getMessage(), e);
            }
        });
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return transactionOperations.executeRead(status -> {
            Connection connection = status.getConnection();
            return findStream(preparedQuery, connection);
        });
    }

    private <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery, Connection connection) {
        Class<R> resultType = preparedQuery.getResultType();
        AtomicBoolean finished = new AtomicBoolean();

        PreparedStatement ps;
        try {
            ps = prepareStatement(connection::prepareStatement, preparedQuery, false, false);
        } catch (Exception e) {
            throw new DataAccessException("SQL Error preparing Query: " + e.getMessage(), e);
        }

        ResultSet openedRs = null;
        ResultSet rs;
        try {
            openedRs = ps.executeQuery();
            rs = openedRs;

            boolean dtoProjection = preparedQuery.isDtoProjection();
            boolean isEntity = preparedQuery.getResultDataType() == DataType.ENTITY;
            Spliterator<R> spliterator;

            if (isEntity || dtoProjection) {
                SqlResultConsumer sqlMappingConsumer = preparedQuery.hasResultConsumer() ? preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class).orElse(null) : null;
                SqlTypeMapper<ResultSet, R> mapper;
                final RuntimePersistentEntity<R> persistentEntity = getEntity(resultType);
                if (dtoProjection) {
                    mapper = new SqlDTOMapper<>(
                            persistentEntity,
                            columnNameResultSetReader,
                            jsonCodec
                    );
                } else {
                    Set<JoinPath> joinFetchPaths = preparedQuery.getJoinFetchPaths();
                    SqlResultEntityTypeMapper<ResultSet, R> entityTypeMapper = new SqlResultEntityTypeMapper<>(
                            persistentEntity,
                            columnNameResultSetReader,
                            joinFetchPaths,
                            jsonCodec,
                            (loadedEntity, o) -> {
                                if (loadedEntity.hasPostLoadEventListeners()) {
                                    return triggerPostLoad(o, loadedEntity, preparedQuery.getAnnotationMetadata());
                                } else {
                                    return o;
                                }
                            }
                    );
                    boolean onlySingleEndedJoins = joinFetchPaths.stream()
                            .flatMap(jp -> Arrays.stream(jp.getAssociationPath()))
                            .anyMatch(association -> association.getKind().isSingleEnded());
                    // Cannot stream ResultSet for "many" joined query
                    if (!onlySingleEndedJoins) {
                        try {
                            return entityTypeMapper.readAllWithJoins(rs).stream();
                        } finally {
                            closeResultSet(ps, rs, finished);
                        }
                    } else {
                        mapper = entityTypeMapper;
                    }
                }
                spliterator = new Spliterators.AbstractSpliterator<R>(Long.MAX_VALUE,
                        Spliterator.ORDERED | Spliterator.IMMUTABLE) {
                    @Override
                    public boolean tryAdvance(Consumer<? super R> action) {
                        if (finished.get()) {
                            return false;
                        }
                        boolean hasNext = mapper.hasNext(rs);
                        if (hasNext) {
                            R o = mapper.map(rs, resultType);
                            if (sqlMappingConsumer != null) {
                                sqlMappingConsumer.accept(rs, o);
                            }
                            action.accept(o);
                        } else {
                            closeResultSet(ps, rs, finished);
                        }
                        return hasNext;
                    }
                };
            } else {
                spliterator = new Spliterators.AbstractSpliterator<R>(Long.MAX_VALUE,
                        Spliterator.ORDERED | Spliterator.IMMUTABLE) {
                    @Override
                    public boolean tryAdvance(Consumer<? super R> action) {
                        if (finished.get()) {
                            return false;
                        }
                        try {
                            boolean hasNext = rs.next();
                            if (hasNext) {
                                Object v = columnIndexResultSetReader
                                        .readDynamic(rs, 1, preparedQuery.getResultDataType());
                                if (resultType.isInstance(v)) {
                                    //noinspection unchecked
                                    action.accept((R) v);
                                } else if (v != null) {
                                    Object r = columnIndexResultSetReader.convertRequired(v, resultType);
                                    if (r != null) {
                                        action.accept((R) r);
                                    }
                                }
                            } else {
                                closeResultSet(ps, rs, finished);
                            }
                            return hasNext;
                        } catch (SQLException e) {
                            throw new DataAccessException("Error retrieving next JDBC result: " + e.getMessage(), e);
                        }
                    }
                };
            }

            return StreamSupport.stream(spliterator, false).onClose(() -> {
                closeResultSet(ps, rs, finished);
            });
        } catch (Exception e) {
            closeResultSet(ps, openedRs, finished);
            throw new DataAccessException("SQL Error executing Query: " + e.getMessage(), e);
        }
    }

    private void closeResultSet(PreparedStatement ps, ResultSet rs, AtomicBoolean finished) {
        if (finished.compareAndSet(false, true)) {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (ps != null) {
                    ps.close();
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error closing JDBC result stream: " + e.getMessage(), e);
            }
        }
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return transactionOperations.executeRead(status -> {
            Connection connection = status.getConnection();
            return findStream(preparedQuery, connection).collect(Collectors.toList());
        });
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return transactionOperations.executeWrite(status -> {
            try {
                Connection connection = status.getConnection();
                try (PreparedStatement ps = prepareStatement(connection::prepareStatement, preparedQuery, true, false)) {
                    int result = ps.executeUpdate();
                    if (QUERY_LOG.isTraceEnabled()) {
                        QUERY_LOG.trace("Update operation updated {} records", result);
                    }
                    if (preparedQuery.isOptimisticLock()) {
                        checkOptimisticLocking(1, result);
                    }
                    return Optional.of(result);
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL UPDATE: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        Dialect dialect = queryBuilders.getOrDefault(operation.getRepositoryType(), DEFAULT_SQL_BUILDER).dialect();
        RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
        if (isSupportsBatchDelete(persistentEntity, dialect)) {
            return Optional.of(transactionOperations.executeWrite(status ->
                    deleteInBatch(status.getConnection(), annotationMetadata, new StoredSqlOperation(dialect, annotationMetadata), persistentEntity, operation)));
        }
        return Optional.of(
                operation.split().stream()
                        .mapToInt(op -> deleteOne(annotationMetadata, new StoredSqlOperation(dialect, annotationMetadata), persistentEntity, op.getEntity()))
                        .sum()
        );
    }

    @Override
    public <T> int delete(@NonNull DeleteOperation<T> operation) {
        AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        Dialect dialect = queryBuilders.getOrDefault(operation.getRepositoryType(), DEFAULT_SQL_BUILDER).dialect();
        return deleteOne(annotationMetadata, new StoredSqlOperation(dialect, annotationMetadata), getEntity(operation.getRootEntity()), operation.getEntity());
    }

    private <T> int deleteOne(AnnotationMetadata annotationMetadata, StoredSqlOperation sqlOperation,
                              RuntimePersistentEntity<T> persistentEntity,
                              T oneEntity) {
        Objects.requireNonNull(oneEntity, "Passed entity cannot be null");
        final T entity;
        Map<String, Object> previousValues = sqlOperation.collectAutoPopulatedPreviousValues(persistentEntity, oneEntity);
        if (persistentEntity.hasPreRemoveEventListeners()) {
            entity = triggerPreRemove(oneEntity, persistentEntity, annotationMetadata);
            if (entity == null) {
                // operation vetoed
                return 0;
            }
        } else {
            entity = oneEntity;
        }
        return transactionOperations.executeWrite(status -> {
            try {
                Connection connection = status.getConnection();
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing SQL DELETE: {}", sqlOperation.getQuery());
                }
                try (PreparedStatement ps = connection.prepareStatement(sqlOperation.getQuery())) {
                    sqlOperation.setParameters(ps, persistentEntity, entity, previousValues);
                    int deleted = ps.executeUpdate();
                    if (QUERY_LOG.isTraceEnabled()) {
                        QUERY_LOG.trace("Delete operation deleted {} records", deleted);
                    }
                    if (sqlOperation.isOptimisticLock()) {
                        checkOptimisticLocking(1, deleted);
                    }
                    if (deleted > 0 && persistentEntity.hasPostRemoveEventListeners()) {
                        triggerPostRemove(entity, persistentEntity, annotationMetadata);
                    }
                    return deleted;
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL DELETE: " + e.getMessage(), e);
            }
        });
    }

    private <T> int deleteInBatch(Connection connection, AnnotationMetadata annotationMetadata,
                                  StoredSqlOperation sqlOperation,
                                  RuntimePersistentEntity<T> persistentEntity,
                                  Iterable<T> entitiesIterable) {

        Objects.requireNonNull(entitiesIterable, "Entities cannot be ull");
        try {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Batch SQL DELETE: {}", sqlOperation.getQuery());
            }
            int expectedUpdates = 0;
            try (PreparedStatement ps = connection.prepareStatement(sqlOperation.getQuery())) {
                for (T entity : entitiesIterable) {
                    Map<String, Object> previousValues = sqlOperation.collectAutoPopulatedPreviousValues(persistentEntity, entity);
                    if (persistentEntity.hasPreRemoveEventListeners()) {
                        entity = triggerPreRemove(entity, persistentEntity, annotationMetadata);
                        if (entity == null) {
                            // operation vetoed
                            continue;
                        }
                    }
                    expectedUpdates++;
                    sqlOperation.setParameters(ps, persistentEntity, entity, previousValues);
                    ps.addBatch();
                    if (persistentEntity.hasPostRemoveEventListeners()) {
                        triggerPostRemove(entity, persistentEntity, annotationMetadata);
                    }
                }

                int deleted = Arrays.stream(ps.executeBatch()).sum();
                if (QUERY_LOG.isTraceEnabled()) {
                    QUERY_LOG.trace("Delete operation deleted {} records", deleted);
                }
                if (sqlOperation.isOptimisticLock()) {
                    checkOptimisticLocking(expectedUpdates, deleted);
                }
                return deleted;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error executing SQL DELETE: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        final Set<Object> persisted = new HashSet<>(10);
        final Class<?> repositoryType = operation.getRepositoryType();
        final Dialect dialect = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER).dialect();
        final RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
        StoredSqlOperation sqlOperation = new StoredSqlOperation(dialect, annotationMetadata);
        return transactionOperations.executeWrite(status ->
                updateOne(status.getConnection(), annotationMetadata, repositoryType, sqlOperation, Collections.emptyList(), persistentEntity, operation.getEntity(), persisted));
    }

    @NonNull
    @Override
    public <T> Iterable<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        final Set<Object> persisted = new HashSet<>(10);
        final Class<?> repositoryType = operation.getRepositoryType();
        final Dialect dialect = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER).dialect();
        final RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
        return transactionOperations.executeWrite(status -> {
            StoredSqlOperation sqlOperation = new StoredSqlOperation(dialect, annotationMetadata);
            if (!isSupportsBatchUpdate(persistentEntity, dialect)) {
                return operation.split().stream()
                        .map(op -> updateOne(status.getConnection(), annotationMetadata, repositoryType, sqlOperation, Collections.emptyList(), persistentEntity, op.getEntity(), persisted))
                        .collect(Collectors.toList());
            }
            return updateInBatch(sqlOperation, status.getConnection(), annotationMetadata, persistentEntity, operation, persisted);
        });
    }

    private <T> T updateOne(Connection connection,
                            AnnotationMetadata annotationMetadata,
                            Class<?> repositoryType,
                            SqlOperation sqlOperation,
                            List<Association> associations,
                            RuntimePersistentEntity<T> persistentEntity,
                            T entity, Set<Object> persisted) {
        Objects.requireNonNull(entity, "Passed entity cannot be null");
        if (persisted.contains(entity)) {
            return entity;
        }
        Map<String, Object> previousValues = sqlOperation.collectAutoPopulatedPreviousValues(persistentEntity, entity);
        if (persistentEntity.hasPreUpdateEventListeners()) {
            entity = triggerPreUpdate(entity, persistentEntity, annotationMetadata);
            if (entity == null) {
                return null;
            }
        }
        entity = cascadeUpdate(false, connection, sqlOperation.getDialect(), annotationMetadata, repositoryType, associations, persistentEntity, entity, persisted);
        try {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL UPDATE: {}", sqlOperation.getQuery());
            }
            try (PreparedStatement ps = connection.prepareStatement(sqlOperation.getQuery())) {
                sqlOperation.setParameters(ps, persistentEntity, entity, previousValues);
                int result = ps.executeUpdate();
                if (QUERY_LOG.isTraceEnabled()) {
                    QUERY_LOG.trace("Update operation updated {} records", result);
                }
                if (sqlOperation.isOptimisticLock()) {
                    checkOptimisticLocking(1, result);
                }
                if (persistentEntity.hasPostUpdateEventListeners()) {
                    entity = triggerPostUpdate(entity, persistentEntity, annotationMetadata);
                }
                return cascadeUpdate(true, connection, sqlOperation.getDialect(), annotationMetadata, repositoryType, associations, persistentEntity, entity, persisted);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error executing SQL UPDATE: " + e.getMessage(), e);
        }
    }

    private <T> Iterable<T> updateInBatch(StoredSqlOperation sqlOperation,
                                          Connection connection,
                                          AnnotationMetadata annotationMetadata,
                                          RuntimePersistentEntity<T> persistentEntity,
                                          Iterable<T> entitiesIterable,
                                          Set<Object> persisted) {
        Objects.requireNonNull(entitiesIterable, "Passed entities cannot be null");
        List<T> entities;
        if (entitiesIterable instanceof List) {
            entities = new ArrayList<>(((List<T>) entitiesIterable));
        } else {
            entities = new ArrayList<>(CollectionUtils.iterableToList(entitiesIterable));
        }
        List<T> toUpdate;
        int[] toUpdateEntitiesIndex;
        List<Map<String, Object>> previousValuesList = null;
        if (persistentEntity.hasPreUpdateEventListeners()) {
            toUpdate = new ArrayList<>(entities.size());
            toUpdateEntitiesIndex = new int[entities.size()];
            if (sqlOperation.isOptimisticLock()) {
                previousValuesList = new ArrayList<>(entities.size());
            }
            int i = 0;
            for (T child : entities) {
                Map<String, Object> previousValues = sqlOperation.collectAutoPopulatedPreviousValues(persistentEntity, child);
                child = triggerPreUpdate(child, persistentEntity, annotationMetadata);
                if (child == null || persisted.contains(child)) {
                    continue;
                }
                if (previousValuesList != null) {
                    previousValuesList.add(previousValues);
                }
                toUpdate.add(child);
                toUpdateEntitiesIndex[i] = i;
                i++;
            }
        } else {
            toUpdate = entities;
            toUpdateEntitiesIndex = null;
        }
        try {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Batch SQL Update: {}", sqlOperation.getQuery());
            }
            int i = 0;
            try (PreparedStatement ps = connection.prepareStatement(sqlOperation.getQuery())) {
                for (T entity : toUpdate) {
                    sqlOperation.setParameters(ps, persistentEntity, entity, previousValuesList == null ? null : previousValuesList.get(i++));
                    ps.addBatch();
                }
                int[] result = ps.executeBatch();
                if (QUERY_LOG.isTraceEnabled()) {
                    QUERY_LOG.trace("Update batch operation updated {} records", Arrays.toString(result));
                }
            }
            if (persistentEntity.hasPostUpdateEventListeners()) {
                for (T entity : toUpdate) {
                    entity = triggerPostUpdate(entity, persistentEntity, annotationMetadata);
                }
            }
            if (toUpdateEntitiesIndex != null) {
                int index = 0;
                for (T entity : toUpdate) {
                    entities.set(toUpdateEntitiesIndex[index++], entity);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error executing SQL UPDATE: " + e.getMessage(), e);
        }
        return entities;
    }

    @NonNull
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        T entity = operation.getEntity();
        final Class<?> repositoryType = operation.getRepositoryType();
        final Dialect dialect = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER).dialect();
        final SqlOperation sqlOperation = new StoredSqlOperation(dialect, annotationMetadata);
        final RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
        return transactionOperations.executeWrite((status) ->
                persistOne(status.getConnection(), annotationMetadata, repositoryType, sqlOperation, Collections.emptyList(), persistentEntity, entity, new HashSet<>(5)));
    }

    private <T> T persistOne(
            Connection connection,
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            SqlOperation sqlOperation,
            List<Association> associations,
            RuntimePersistentEntity<T> persistentEntity,
            T entity,
            Set<Object> persisted) {
        try {
            RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
            entity = cascadePersist(false, connection, sqlOperation.getDialect(), annotationMetadata, repositoryType, associations, persistentEntity, entity, persisted);
            final boolean hasGeneratedID = identity != null && identity.isGenerated();

            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL Insert: {}", sqlOperation.getQuery());
            }
            T resolvedEntity;
            try (PreparedStatement stmt = persistOnePreparedStatement(sqlOperation.getDialect(), identity, connection, hasGeneratedID, sqlOperation.getQuery())) {
                if (persistentEntity.hasPrePersistEventListeners()) {
                    final T newEntity = triggerPrePersist(entity, persistentEntity, annotationMetadata);
                    if (newEntity == null) {
                        // operation evicted
                        return entity;
                    } else {
                        resolvedEntity = newEntity;
                    }
                } else {
                    resolvedEntity = entity;
                }
                sqlOperation.setParameters(stmt, persistentEntity, entity, null);
                stmt.executeUpdate();

                persisted.add(resolvedEntity);
                if (hasGeneratedID) {
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        Object id = getEntityId(generatedKeys, identity.getDataType(), identity.getType());
                        resolvedEntity = updateEntityId((BeanProperty<T, Object>) identity.getProperty(), resolvedEntity, id);
                    } else {
                        throw new DataAccessException("ID failed to generate. No result returned.");
                    }
                }
                if (persistentEntity.hasPostPersistEventListeners()) {
                    resolvedEntity = triggerPostPersist(resolvedEntity, persistentEntity, annotationMetadata);
                }
            }
            if (identity == null) {
                return resolvedEntity;
            }
            return cascadePersist(true, connection, sqlOperation.getDialect(), annotationMetadata, repositoryType, associations, persistentEntity, resolvedEntity, persisted);
        } catch (SQLException e) {
            throw new DataAccessException("SQL Error executing INSERT: " + e.getMessage(), e);
        }
    }

    private <T> T cascadePersist(boolean isPostPersist, Connection connection,
                                 Dialect dialect,
                                 AnnotationMetadata annotationMetadata,
                                 Class<?> repositoryType,
                                 List<Association> associations,
                                 RuntimePersistentEntity<T> persistentEntity,
                                 T e,
                                 Set<Object> persisted) {
        return cascade(isPostPersist, Relation.Cascade.PERSIST, CascadeContext.of(associations, e), persistentEntity, e, new CascadeChild() {

            @Override
            public <C> boolean shouldCascade(RuntimePersistentEntity<C> childPersistentEntity, C entity) {
                return childPersistentEntity.getIdentity().getProperty().get(entity) == null;
            }

            @Override
            public <C> C acceptOne(CascadeContext ctx, RuntimePersistentEntity<C> childPersistentEntity, C child) {
                return persistOneCascaded(connection, repositoryType, dialect, annotationMetadata, ctx, childPersistentEntity, child, persisted);
            }

            @Override
            public <C> Iterable<C> acceptBatch(CascadeContext ctx, RuntimePersistentEntity<C> persistentEntity, List<C> entities) {
                SqlOperation childSqlPersistOperation = resolveEntityInsert(
                        annotationMetadata,
                        repositoryType,
                        persistentEntity.getIntrospection().getBeanType(),
                        persistentEntity
                );
                entities = persistInBatch(connection, annotationMetadata, repositoryType, childSqlPersistOperation, associations, persistentEntity, entities, persisted);
                return entities;
            }

            @Override
            public <C> void associate(CascadeContext ctx, RuntimePersistentEntity<C> persistentEntity, List<C> entities) {
                persistJoinTableAssociation(connection, repositoryType, dialect, ctx, persistentEntity, entities);
            }

            @Override
            public boolean isSupportsBatch(PersistentEntity persistentEntity) {
                return isSupportsBatchInsert(persistentEntity, dialect);
            }
        });
    }

    private <C> C persistOneCascaded(Connection connection,
                                           Class<?> repositoryType,
                                           Dialect dialect,
                                           AnnotationMetadata annotationMetadata,
                                           CascadeContext ctx,
                                           RuntimePersistentEntity<C> childPersistentEntity,
                                           C child,
                                           Set<Object> persisted) {
        RuntimePersistentProperty<C> associatedId = childPersistentEntity.getIdentity();
        BeanProperty associatedIdProperty = associatedId.getProperty();
        if (associatedId != null) {
            final Object id = associatedIdProperty.get(child);
            if (id != null) {
                return child;
            }
        }
        SqlOperation childSqlPersistOperation = resolveEntityInsert(
                annotationMetadata,
                repositoryType,
                child.getClass(),
                childPersistentEntity
        );
        child = persistOne(connection, annotationMetadata, repositoryType, childSqlPersistOperation, ctx.associations, childPersistentEntity, child, persisted);
        persistJoinTableAssociation(connection, repositoryType, dialect, ctx, childPersistentEntity, Collections.singletonList(child));
        return child;
    }

    private <T> T cascadeUpdate(boolean isPost, Connection connection,
                                 Dialect dialect,
                                 AnnotationMetadata annotationMetadata,
                                 Class<?> repositoryType,
                                 List<Association> associations,
                                 RuntimePersistentEntity<T> persistentEntity,
                                 T resolvedEntity,
                                 Set<Object> persisted) {
        return cascade(isPost, Relation.Cascade.PERSIST, CascadeContext.of(associations, resolvedEntity), persistentEntity, resolvedEntity, new CascadeChild() {

            @Override
            public <C> C acceptOne(CascadeContext ctx, RuntimePersistentEntity<C> childPersistentEntity, C child) {
                final RuntimePersistentProperty<C> idReader = childPersistentEntity.getIdentity();
                final BeanProperty<C, ?> idReaderProperty = idReader.getProperty();
                final Object id = idReaderProperty.get(child);
                if (id == null) {
                    return persistOneCascaded(connection, repositoryType, dialect, annotationMetadata, ctx, childPersistentEntity, child, persisted);
                }
                SqlOperation childSqlUpdateOperation = resolveEntityUpdate(
                        annotationMetadata,
                        repositoryType,
                        childPersistentEntity.getIntrospection().getBeanType(),
                        childPersistentEntity
                );
                return updateOne(connection, annotationMetadata, repositoryType, childSqlUpdateOperation, associations, childPersistentEntity, child, persisted);
            }

            @Override
            public <C> Iterable<C> acceptBatch(CascadeContext ctx, RuntimePersistentEntity<C> childPersistentEntity, List<C> entities) {
                // TODO: handle cascading updates to collections?
                return entities;
            }

            @Override
            public boolean isSupportsBatch(PersistentEntity persistentEntity) {
                return isSupportsBatchUpdate(persistentEntity, dialect);
            }

        });
    }

    private <T> T updateEntityId(BeanProperty<T, Object> identity, T resolvedEntity, Object id) {
        if (id == null) {
            return resolvedEntity;
        }
        if (identity.getType().isInstance(id)) {
            return setProperty(identity, resolvedEntity, id);
        }
        return convertAndSetWithValue(identity, resolvedEntity, id);
    }

    private <B, T> B convertAndSetWithValue(BeanProperty<B, T> beanProperty, B bean, T value) {
        if (beanProperty.isReadOnly()) {
            Argument<T> argument = beanProperty.asArgument();
            final ArgumentConversionContext<T> context = ConversionContext.of(argument);
            Object convertedValue = ConversionService.SHARED.convert(value, context).orElseThrow(() ->
                    new ConversionErrorException(argument, context.getLastError()
                            .orElse(() -> new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + beanProperty.getType())))
            );
            return beanProperty.withValue(bean, (T) convertedValue);
        }
        beanProperty.convertAndSet(bean, value);
        return bean;
    }

    private PreparedStatement persistOnePreparedStatement(Dialect dialect, PersistentProperty identity, Connection connection, boolean hasGeneratedID, String insertSql) throws SQLException {
        PreparedStatement stmt;
        if (hasGeneratedID && (dialect == Dialect.ORACLE || dialect == Dialect.SQL_SERVER)) {
            stmt = connection.prepareStatement(insertSql, new String[] { identity.getPersistedName() });
        } else {
            stmt = connection.prepareStatement(insertSql, hasGeneratedID ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
        }
        return stmt;
    }

    private <T> T cascade(
            boolean fkOnly,
            Relation.Cascade cascadeType,
            CascadeContext ctx,
            RuntimePersistentEntity<T> persistentEntity,
            T entity,
            CascadeChild cascadeChild) {
        for (RuntimeAssociation<T> association : persistentEntity.getAssociations()) {
            BeanProperty<T, Object> beanProperty = (BeanProperty<T, Object>) association.getProperty();
            Object child = beanProperty.get(entity);
            if (child == null) {
                break;
            }
            if (association instanceof Embedded) {
                Object newValue = cascade(fkOnly, cascadeType, ctx.embedded(association),
                        (RuntimePersistentEntity) association.getAssociatedEntity(),
                        child,
                        cascadeChild);
                if (child != newValue) {
                    entity = setProperty(beanProperty, entity, newValue);
                }
                continue;
            }
            if (association.doesCascade(cascadeType) && (fkOnly || !association.isForeignKey())) {
                if (association.getInverseSide().map(assoc -> ctx.rootAssociations.contains(assoc) || ctx.associations.contains(assoc)).orElse(false)) {
                    continue;
                }
                switch (association.getKind()) {
                    case ONE_TO_ONE:
                    case MANY_TO_ONE:
                        final RuntimePersistentEntity<Object> associatedEntity = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
                        Object newChild = cascadeChild.acceptOne(ctx.relation(association), associatedEntity, child);
                        if (child != newChild) {
                            entity = setProperty((BeanProperty<T, Object>) association.getProperty(), entity, newChild);
                        }
                        if (association.isForeignKey()) {
                            RuntimeAssociation<?> inverseAssociation = association.getInverseSide().orElse(null);
                            if (inverseAssociation != null) {
                                BeanProperty<T, Object> property = (BeanProperty<T, Object>) inverseAssociation.getProperty();
                                entity = setProperty(property, entity, child);
                            }
                        }
                        continue;
                    case ONE_TO_MANY:
                    case MANY_TO_MANY:
                        entity = cascadeChildren(ctx.relation(association), entity, association, cascadeChild);
                        continue;
                    default:
                        throw new IllegalArgumentException("Cannot cascade for relation: " + association.getKind());
                }
            }
        }
        return entity;
    }

    private <T> T cascadeChildren(CascadeContext ctx, T parent, RuntimeAssociation<T> association, CascadeChild cascadeChild) {
        final RuntimePersistentEntity<Object> associatedEntity = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
        final RuntimePersistentProperty<?> associatedId = associatedEntity.getIdentity();
        final BeanProperty associatedIdProperty = associatedId.getProperty();
        final BeanProperty associationProperty = association.getProperty();
        final Object children = associationProperty.get(parent);
        final RuntimeAssociation inverse = association.getInverseSide().orElse(null);

        if (children instanceof Iterable) {
            List<Object> entities;
            List<Object> toPersist;
            if (children instanceof List) {
                entities = new ArrayList<>(((List<?>) children));
            } else {
                entities = new ArrayList<>(CollectionUtils.iterableToList((Iterable) children));
            }
            toPersist = new ArrayList<>(15);
            boolean setChildren = false;
            int[] toPersistEntitiesIndex = new int[entities.size()];
            int i = 0;
            for (Object child : entities) {
                if (child == null) {
                    continue;
                }
                if (inverse != null) {
                    if (inverse.getKind() == Relation.Kind.MANY_TO_ONE) {
                        final BeanProperty property = inverse.getProperty();
                        child = setProperty(property, child, parent);
                        setChildren = true;
                    }
                }

                if (cascadeChild.shouldCascade(associatedEntity, child)) {
                    final BeanProperty bp = associatedIdProperty;
                    final Object id = bp.get(child);
                    if (id == null) {
                        toPersist.add(child);
                        toPersistEntitiesIndex[i] = i;
                    }
                }
                i++;
            }
            if (!toPersist.isEmpty()) {
                if (cascadeChild.isSupportsBatch(associatedEntity)) {
                    Iterable<Object> persistedChildren = cascadeChild.acceptBatch(ctx, associatedEntity, toPersist);
                    int k = 0;
                    for (Object child : persistedChildren) {
                        if (association.isForeignKey()) {
                            RuntimeAssociation inverseAssociation = association.getInverseSide().orElse(null);
                            if (inverseAssociation != null) {
                                BeanProperty property = inverseAssociation.getProperty();
                                child = setProperty(property, child, parent);
                            }
                        }
                        int idx = toPersistEntitiesIndex[k++];
                        if (entities.get(idx) != child) {
                            entities.set(idx, child);
                            setChildren = true;
                        }
                    }
                } else {
                    int k = 0;
                    for (Object child : toPersist) {
                        child = cascadeChild.acceptOne(ctx, associatedEntity, child);
                        if (association.isForeignKey()) {
                            RuntimeAssociation inverseAssociation = association.getInverseSide().orElse(null);
                            if (inverseAssociation != null) {
                                BeanProperty property = inverseAssociation.getProperty();
                                child = setProperty(property, child, parent);
                            }
                        }
                        int idx = toPersistEntitiesIndex[k++];
                        if (entities.get(idx) != child) {
                            entities.set(idx, child);
                            setChildren = true;
                        }
                    }
                }
            }
            if (SqlQueryBuilder.isForeignKeyWithJoinTable(association)) {
                cascadeChild.associate(ctx, associatedEntity, entities);
            }
            if (setChildren) {
                parent = convertAndSetWithValue((BeanProperty<T, Object>) associationProperty, parent, entities);
            }
        }
        return parent;
    }

    private <T, K> void persistJoinTableAssociation(Connection connection,
                                                    Class<?> repositoryType,
                                                    Dialect dialect,
                                                    CascadeContext ctx,
                                                    RuntimePersistentEntity<K> childPersistentEntity,
                                                    List<K> entities) {
        Association association = ctx.associations.get(ctx.associations.size() - 1);
        if (SqlQueryBuilder.isForeignKeyWithJoinTable(association) && !entities.isEmpty()) {
            RuntimePersistentEntity<?> entity = getEntity(ctx.parent.getClass());
            String sqlInsert = resolveAssociationInsert(repositoryType, entity, (RuntimeAssociation) association);
            insertJoinTableAssociations(
                    dialect,
                    connection,
                    sqlInsert,
                    entity.getIdentity(),
                    ctx.parent,
                    childPersistentEntity.getIdentity(),
                    entities);
        }
    }

    private <T, K> void insertJoinTableAssociations(Dialect dialect,
                                                 Connection connection,
                                                 String sqlInsert,
                                                 RuntimePersistentProperty<T> parentIdentity,
                                                 Object parent,
                                                 RuntimePersistentProperty<K> childIdentity,
                                                 List<K> children) {
        if (dialect.allowBatch() && children.size() > 1) {
            try (PreparedStatement ps = connection.prepareStatement(sqlInsert)) {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing SQL Insert: {}", sqlInsert);
                }
                for (Object child : children) {
                    int i = 0;
                    for (Map.Entry<PersistentProperty, Object> property : idPropertiesWithValues(parentIdentity, parent).collect(Collectors.toList())) {
                        setStatementParameter(
                                ps,
                                shiftIndex(i++),
                                property.getKey().getDataType(),
                                property.getValue(),
                                dialect);
                    }
                    for (Map.Entry<PersistentProperty, Object> property : idPropertiesWithValues(childIdentity, child).collect(Collectors.toList())) {
                        setStatementParameter(
                                ps,
                                shiftIndex(i++),
                                property.getKey().getDataType(),
                                property.getValue(),
                                dialect);
                    }
                    ps.addBatch();
                }
                int[] result = ps.executeBatch();
                if (QUERY_LOG.isTraceEnabled()) {
                    QUERY_LOG.trace("Insert batch operation inserted {} records", Arrays.stream(result).sequential());
                }
            } catch (SQLException e) {
                throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
            }
        } else {
            for (Object child : children) {
                try (PreparedStatement ps = connection.prepareStatement(sqlInsert)) {
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing SQL Insert: {}", sqlInsert);
                    }
                    int i = 0;
                    for (Map.Entry<PersistentProperty, Object> property : idPropertiesWithValues(parentIdentity, parent).collect(Collectors.toList())) {
                        setStatementParameter(
                                ps,
                                shiftIndex(i++),
                                property.getKey().getDataType(),
                                property.getValue(),
                                dialect);
                    }
                    for (Map.Entry<PersistentProperty, Object> property : idPropertiesWithValues(childIdentity, child).collect(Collectors.toList())) {
                        setStatementParameter(
                                ps,
                                shiftIndex(i++),
                                property.getKey().getDataType(),
                                property.getValue(),
                                dialect);
                    }
                    ps.execute();
                } catch (SQLException e) {
                    throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
                }
            }
        }
    }

    private Stream<Map.Entry<PersistentProperty, Object>> idPropertiesWithValues(PersistentProperty property, Object value) {
        Object propertyValue = ((RuntimePersistentProperty) property).getProperty().get(value);
        if (property instanceof Embedded) {
            Embedded embedded = (Embedded) property;
            PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
            return embeddedEntity.getPersistentProperties()
                    .stream()
                    .flatMap(prop -> idPropertiesWithValues(prop, propertyValue));
        } else if (property instanceof Association) {
            Association association = (Association) property;
            if (association.isForeignKey()) {
                return Stream.empty();
            }
            PersistentEntity associatedEntity = association.getAssociatedEntity();
            PersistentProperty identity = associatedEntity.getIdentity();
            if (identity == null) {
                throw new IllegalStateException("Identity cannot be missing for: " + associatedEntity);
            }
            return idPropertiesWithValues(identity, propertyValue);
        }
        return Stream.of(new AbstractMap.SimpleEntry<>(property, propertyValue));
    }

    @Nullable
    @Override
    public <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        throw new UnsupportedOperationException("The findOne method by ID is not supported. Execute the SQL query directly");
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
    public <T> Stream<T> findStream(@NonNull PagedQuery<T> query) {
        throw new UnsupportedOperationException("The findStream method without an explicit query is not supported. Use findStream(PreparedQuery) instead");
    }

    @Override
    public <R> Page<R> findPage(@NonNull PagedQuery<R> query) {
        throw new UnsupportedOperationException("The findPage method without an explicit query is not supported. Use findPage(PreparedQuery) instead");
    }

    @NonNull
    @Override
    public <T> Iterable<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        final Class<?> repositoryType = operation.getRepositoryType();
        final Dialect dialect = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER).dialect();
        final SqlOperation sqlOperation = new StoredSqlOperation(dialect, annotationMetadata);
        final RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
        final HashSet<Object> persisted = new HashSet<>(5);
        return transactionOperations.executeWrite((status) -> {
            if (!isSupportsBatchInsert(persistentEntity, dialect)) {
                return operation.split().stream()
                        .map(op -> persistOne(status.getConnection(), annotationMetadata, repositoryType, sqlOperation, Collections.emptyList(), persistentEntity, op.getEntity(), persisted))
                        .collect(Collectors.toList());
            } else {
                return persistInBatch(
                        status.getConnection(),
                        operation.getAnnotationMetadata(),
                        operation.getRepositoryType(),
                        sqlOperation,
                        Collections.emptyList(),
                        persistentEntity,
                        operation,
                        persisted
                );
            }
        });
    }

    private <T> List<T> persistInBatch(
            Connection connection,
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            SqlOperation sqlOperation,
            List<Association> associations,
            RuntimePersistentEntity<T> persistentEntity,
            @NonNull Iterable<T> entities,
            Set<Object> persisted) {
        List<T> results = new ArrayList<>(10);
        boolean hasGeneratedID = persistentEntity.getIdentity() != null && persistentEntity.getIdentity().isGenerated();
        try (PreparedStatement stmt = connection
                .prepareStatement(sqlOperation.getQuery(), hasGeneratedID ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {

            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Batch SQL Insert: {}", sqlOperation.getQuery());
            }
            final boolean hasPrePersistEventListeners = persistentEntity.hasPrePersistEventListeners();
            for (T entity : entities) {
                if (persisted.contains(entity)) {
                    continue;
                }
                if (hasPrePersistEventListeners) {
                    final T eventResult = triggerPrePersist(entity, persistentEntity, annotationMetadata);
                    if (eventResult == null) {
                        // operation evicted
                        results.add(entity);
                        continue;
                    } else {
                        entity = eventResult;
                    }
                }
                entity = cascadePersist(false, connection, sqlOperation.getDialect(), annotationMetadata, repositoryType, associations, persistentEntity, entity, persisted);
                sqlOperation.setParameters(stmt, persistentEntity, entity, null);
                stmt.addBatch();
                results.add(entity);
            }
            int[] result = stmt.executeBatch();
            if (QUERY_LOG.isTraceEnabled()) {
                QUERY_LOG.trace("Insert batch operation inserted {} records", Arrays.toString(result));
            }

            if (hasGeneratedID) {
                ListIterator<T> resultIterator = results.listIterator();
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                while (resultIterator.hasNext()) {
                    T entity = resultIterator.next();
                    if (!generatedKeys.next()) {
                        throw new DataAccessException("Failed to generate ID for entity: " + entity);
                    } else {
                        RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                        Object id = getEntityId(generatedKeys, identity.getDataType(), identity.getType());
                        final T resolvedEntity = updateEntityId((BeanProperty<T, Object>) identity.getProperty(), entity, id);
                        if (resolvedEntity != entity) {
                            resultIterator.set(resolvedEntity);
                        }
                    }
                }
            }
            final boolean hasPostPersistEventListeners = persistentEntity.hasPostPersistEventListeners();
            ListIterator<T> it = results.listIterator();
            while (it.hasNext()) {
                T entityBeforePostPersist = it.next();
                T entity = entityBeforePostPersist;
                if (hasPostPersistEventListeners) {
                    entity = triggerPostPersist(entity, persistentEntity, annotationMetadata);
                }
                entity = cascadePersist(true, connection, sqlOperation.getDialect(), annotationMetadata, repositoryType, associations, persistentEntity, entity, persisted);
                if (entity != entityBeforePostPersist) {
                    it.set(entity);
                }
            }
            return results;
        } catch (SQLException e) {
            throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
        }
    }

    private <T> T getEntityId(ResultSet generatedKeys, DataType dataType, Class<T> type) throws SQLException {
        Object id;
        switch (dataType) {
            case LONG:
                id = generatedKeys.getLong(1);
                break;
            case STRING:
                id = generatedKeys.getString(1);
                break;
            default:
                id = generatedKeys.getObject(1, type);
        }
        return (T) id;
    }

    @Override
    @PreDestroy
    public void close() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @NonNull
    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    @NonNull
    @Override
    public Connection getConnection() {
        return transactionOperations.getConnection();
    }

    @NonNull
    @Override
    public <R> R execute(@NonNull ConnectionCallback<R> callback) {
        try {
            return callback.call(transactionOperations.getConnection());
        } catch (SQLException e) {
            throw new DataAccessException("Error executing SQL Callback: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public <R> R prepareStatement(@NonNull String sql, @NonNull PreparedStatementCallback<R> callback) {
        ArgumentUtils.requireNonNull("sql", sql);
        ArgumentUtils.requireNonNull("callback", callback);
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Query: {}", sql);
        }
        try (PreparedStatement ps = transactionOperations.getConnection().prepareStatement(sql)) {
            return callback.call(ps);
        } catch (SQLException e) {
            throw new DataAccessException("Error preparing SQL statement: " + e.getMessage(), e);
        }
    }

    @NonNull
    @Override
    public <T> Stream<T> entityStream(@NonNull ResultSet resultSet, @NonNull Class<T> rootEntity) {
        return entityStream(resultSet, null, rootEntity);
    }

    @NonNull
    @Override
    public <E> E readEntity(@NonNull String prefix, @NonNull ResultSet resultSet, @NonNull Class<E> type) throws DataAccessException {
        return new SqlResultEntityTypeMapper<>(
                prefix,
                getEntity(type),
                columnNameResultSetReader,
                jsonCodec
        ).map(resultSet, type);
    }

    @NonNull
    @Override
    public <E, D> D readDTO(@NonNull String prefix, @NonNull ResultSet resultSet, @NonNull Class<E> rootEntity, @NonNull Class<D> dtoType) throws DataAccessException {
        return new DTOMapper<E, ResultSet, D>(
                getEntity(rootEntity),
                columnNameResultSetReader,
                jsonCodec
        ).map(resultSet, dtoType);
    }

    @NonNull
    @Override
    public <T> Stream<T> entityStream(@NonNull ResultSet resultSet, @Nullable String prefix, @NonNull Class<T> rootEntity) {
        ArgumentUtils.requireNonNull("resultSet", resultSet);
        ArgumentUtils.requireNonNull("rootEntity", rootEntity);
        TypeMapper<ResultSet, T> mapper = new SqlResultEntityTypeMapper<>(prefix, getEntity(rootEntity), columnNameResultSetReader, jsonCodec);
        Iterable<T> iterable = () -> new Iterator<T>() {
            boolean nextCalled = false;

            @Override
            public boolean hasNext() {
                try {
                    if (!nextCalled) {
                        nextCalled = true;
                        return resultSet.next();
                    } else {
                        return nextCalled;
                    }
                } catch (SQLException e) {
                    throw new DataAccessException("Error retrieving next JDBC result: " + e.getMessage(), e);
                }
            }

            @Override
            public T next() {
                nextCalled = false;
                return mapper.map(resultSet, rootEntity);
            }
        };
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static List<Association> associated(List<Association> associations, Association association) {
        if (associations == null) {
            return Collections.singletonList(association);
        }
        List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
        newAssociations.addAll(associations);
        newAssociations.add(association);
        return newAssociations;
    }

    private <X, Y> X setProperty(BeanProperty<X, Y> beanProperty, X x, Y y) {
        if (beanProperty.isReadOnly()) {
            return beanProperty.withValue(x, y);
        }
        beanProperty.set(x, y);
        return x;
    }

    private void checkOptimisticLocking(int expected, int received) {
        if (received != expected) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + expected + " got: " + received);
        }
    }

    interface CascadeChild {

        default <C> boolean shouldCascade(RuntimePersistentEntity<C> childPersistentEntity, C entity) {
            return true;
        }

        <C> C acceptOne(CascadeContext ctx, RuntimePersistentEntity<C> childPersistentEntity, C entity);

        <C> Iterable<C> acceptBatch(CascadeContext ctx, RuntimePersistentEntity<C> childPersistentEntity, List<C> entities);

        boolean isSupportsBatch(PersistentEntity persistentEntity);

        default <C> void associate(CascadeContext ctx, RuntimePersistentEntity<C> associatedEntity, List<C> entities) {
        }
    }

    static class CascadeContext {

        final List<Association> rootAssociations;
        final Object parent;
        final List<Association> associations;

        CascadeContext(List<Association> rootAssociations, Object parent, List<Association> associations) {
            this.rootAssociations = rootAssociations;
            this.parent = parent;
            this.associations = associations;
        }

        static CascadeContext of(List<Association> rootAssociations, Object parent) {
            return new CascadeContext(rootAssociations, parent, Collections.emptyList());
        }

        CascadeContext embedded(Association association) {
            return new CascadeContext(rootAssociations, parent, associated(associations, association));
        }

        CascadeContext relation(Association association) {
            return new CascadeContext(rootAssociations, parent, associated(associations, association));
        }

    }
}
