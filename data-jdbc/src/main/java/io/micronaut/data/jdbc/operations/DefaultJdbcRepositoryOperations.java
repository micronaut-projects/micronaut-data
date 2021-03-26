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
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.DataMethod;
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
import io.micronaut.data.model.PersistentPropertyPath;
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
import io.micronaut.data.runtime.date.DateTimeProvider;
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
     * @param dateTimeProvider      The dateTimeProvider
     * @param entityRegistry        The entity registry
     */
    @Internal
    protected DefaultJdbcRepositoryOperations(@Parameter String dataSourceName,
                                              DataSource dataSource,
                                              @Parameter TransactionOperations<Connection> transactionOperations,
                                              @Named("io") @Nullable ExecutorService executorService,
                                              BeanContext beanContext,
                                              List<MediaTypeCodec> codecs,
                                              @NonNull DateTimeProvider dateTimeProvider,
                                              RuntimeEntityRegistry entityRegistry) {
        super(
                dataSourceName,
                new ColumnNameResultSetReader(),
                new ColumnIndexResultSetReader(),
                new JdbcQueryStatement(),
                codecs,
                dateTimeProvider,
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
            try (PreparedStatement ps = prepareStatement(connection::prepareStatement, preparedQuery, false, true)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Class<R> resultType = preparedQuery.getResultType();
                        if (preparedQuery.getResultDataType() == DataType.ENTITY) {
                            RuntimePersistentEntity<R> persistentEntity = getEntity(resultType);

                            final Set<JoinPath> joinFetchPaths = preparedQuery.getJoinFetchPaths();
                            SqlResultEntityTypeMapper<ResultSet, R> mapper = new SqlResultEntityTypeMapper<>(
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
                            R result = mapper.readOneWithJoins(rs);
                            if (preparedQuery.hasResultConsumer()) {
                                preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class)
                                        .ifPresent(consumer -> consumer.accept(result, newMappingContext(rs)));
                            }
                            return result;
                        } else {
                            if (preparedQuery.isDtoProjection()) {
                                RuntimePersistentEntity<T> persistentEntity = getEntity(preparedQuery.getRootEntity());
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
        String[] params = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
        String query = annotationMetadata.stringValue(Query.class, "rawQuery")
                .orElseGet(() -> annotationMetadata.stringValue(Query.class).orElse(null));
        Dialect dialect = queryBuilders.getOrDefault(operation.getRepositoryType(), DEFAULT_SQL_BUILDER).dialect();
        RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());

        if (isSupportsBatchUpdate(persistentEntity, dialect)) {
            return Optional.of(transactionOperations.executeWrite(status ->
                    deleteInBatch(status.getConnection(), dialect, annotationMetadata, persistentEntity, query, params, operation)));
        }
        return Optional.of(
                operation.split().stream()
                        .mapToInt(this::delete)
                        .sum()
        );
    }

    @Override
    public <T> int delete(@NonNull DeleteOperation<T> operation) {
        AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        String[] params = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
        String query = annotationMetadata.stringValue(Query.class, "rawQuery")
                .orElseGet(() -> annotationMetadata.stringValue(Query.class).orElse(null));
        Dialect dialect = queryBuilders.getOrDefault(operation.getRepositoryType(), DEFAULT_SQL_BUILDER).dialect();
        return deleteOne(annotationMetadata, params, query, dialect, getEntity(operation.getRootEntity()), operation.getEntity());
    }

    private <T> int deleteOne(AnnotationMetadata annotationMetadata, String[] params, String query, Dialect dialect, RuntimePersistentEntity<T> persistentEntity, T oneEntity) {
        Objects.requireNonNull(query, "Query cannot be null");
        Objects.requireNonNull(oneEntity, "Passed entity cannot be null");

        final T entity;
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
                    QUERY_LOG.debug("Executing SQL DELETE: {}", query);
                }
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    for (int i = 0; i < params.length; i++) {
                        setDeleteStatementParameter(dialect, persistentEntity, params[i], ps, entity, i);
                    }
                    int deleted = ps.executeUpdate();
                    if (QUERY_LOG.isTraceEnabled()) {
                        QUERY_LOG.trace("Delete operation deleted {} records", deleted);
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

    private <T> int deleteInBatch(Connection connection,
                                  Dialect dialect,
                                  AnnotationMetadata annotationMetadata,
                                  RuntimePersistentEntity<T> persistentEntity,
                                  String query,
                                  String[] params,
                                  Iterable<T> entitiesIterable) {

        Objects.requireNonNull(query, "Query cannot be null");
        Objects.requireNonNull(entitiesIterable, "Entities cannot be ull");
        try {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Batch SQL DELETE: {}", query);
            }
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                for (T entity : entitiesIterable) {
                    if (persistentEntity.hasPreRemoveEventListeners()) {
                        entity = triggerPreRemove(entity, persistentEntity, annotationMetadata);
                        if (entity == null) {
                            // operation vetoed
                            continue;
                        }
                    }
                    for (int i = 0; i < params.length; i++) {
                        setDeleteStatementParameter(dialect, persistentEntity, params[i], ps, entity, i);
                    }
                    ps.addBatch();
                    if (persistentEntity.hasPostRemoveEventListeners()) {
                        triggerPostRemove(entity, persistentEntity, annotationMetadata);
                    }
                }

                int[] deleted = ps.executeBatch();
                if (QUERY_LOG.isTraceEnabled()) {
                    QUERY_LOG.trace("Delete operation deleted {} records", deleted);
                }
                return Arrays.stream(deleted).sum();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error executing SQL DELETE: " + e.getMessage(), e);
        }
    }

    private <T> void setDeleteStatementParameter(Dialect dialect, RuntimePersistentEntity<T> persistentEntity, String param, PreparedStatement ps, Object value, int i) {
        String propertyName = param;
        if (propertyName.isEmpty()) {
            setStatementParameter(ps, i + 1, DataType.ENTITY, value, dialect);
            return;
        }
        if (propertyName.startsWith("0.")) {
            propertyName = propertyName.replace("0.", "");
        }
        PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(propertyName);
        if (propertyPath == null) {
            throw new IllegalStateException("Cannot perform delete for non-existent property: " + persistentEntity.getSimpleName() + "." + propertyName);
        }
        for (Association association : propertyPath.getAssociations()) {
            RuntimePersistentProperty<Object> p = (RuntimePersistentProperty<Object>) association;
            value = p.getProperty().get(value);
            if (value == null) {
                break;
            }
        }
        RuntimePersistentProperty<Object> property = (RuntimePersistentProperty<Object>) propertyPath.getProperty();
        if (value != null) {
            value = property.getProperty().get(value);
        }
        setStatementParameter(ps, i + 1, property.getDataType(), value, dialect);
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        final String[] params = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
        final String query = annotationMetadata.stringValue(Query.class).orElse(null);
        final T entity = operation.getEntity();
        final Set<Object> persisted = new HashSet<>(10);
        final Class<?> repositoryType = operation.getRepositoryType();
        final Dialect dialect = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER).dialect();
        final RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
        return transactionOperations.executeWrite(status ->
                updateOne(status.getConnection(), repositoryType, dialect, annotationMetadata, persistentEntity, query, params, entity, persisted));
    }

    @NonNull
    @Override
    public <T> Iterable<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        final String[] params = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
        final String query = annotationMetadata.stringValue(Query.class, "rawQuery")
                .orElseGet(() -> annotationMetadata.stringValue(Query.class).orElse(null));
        final Set<Object> persisted = new HashSet<>(10);
        final Class<?> repositoryType = operation.getRepositoryType();
        final Dialect dialect = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER).dialect();
        final RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
        if (!isSupportsBatchUpdate(persistentEntity, dialect)) {
            return transactionOperations.executeWrite(status -> operation.split().stream()
                    .map(op -> updateOne(status.getConnection(), repositoryType, dialect, annotationMetadata, persistentEntity, query, params, op.getEntity(), persisted))
                    .collect(Collectors.toList()));
        } else {
            return transactionOperations.executeWrite(status -> updateInBatch(
                    status.getConnection(), repositoryType, dialect, annotationMetadata, persistentEntity, query, params, operation, persisted
            ));
        }
    }

    private <T> T updateOne(Connection connection,
                            Class<?> repositoryType,
                            Dialect dialect,
                            AnnotationMetadata annotationMetadata,
                            RuntimePersistentEntity<T> persistentEntity,
                            String query, String[] params, T entity, Set persisted) {
        Objects.requireNonNull(entity, "Passed entity cannot be null");
        if (StringUtils.isNotEmpty(query) && ArrayUtils.isNotEmpty(params)) {
            final T resolvedEntity;
            if (persistentEntity.hasPreUpdateEventListeners()) {
                resolvedEntity = triggerPreUpdate(entity, persistentEntity, annotationMetadata);
                if (resolvedEntity == null) {
                    return entity;
                }
            } else {
                resolvedEntity = entity;
            }
            try {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing SQL UPDATE: {}", query);
                }
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    updateEntitySetStatement(ps, connection, repositoryType, dialect, annotationMetadata, persistentEntity, params, persisted, resolvedEntity);
                    int result = ps.executeUpdate();
                    if (QUERY_LOG.isTraceEnabled()) {
                        QUERY_LOG.trace("Update operation updated {} records", result);
                    }
                    if (persistentEntity.hasPostUpdateEventListeners()) {
                        triggerPostUpdate(resolvedEntity, persistentEntity, annotationMetadata);
                    }
                    return resolvedEntity;
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL UPDATE: " + e.getMessage(), e);
            }
        }
        return entity;
    }

    private <T> Iterable<T> updateInBatch(Connection connection,
                                          Class<?> repositoryType,
                                          Dialect dialect,
                                          AnnotationMetadata annotationMetadata,
                                          RuntimePersistentEntity<T> persistentEntity,
                                          String query,
                                          String[] params,
                                          Iterable<T> entitiesIterable,
                                          Set persisted) {
        Objects.requireNonNull(entitiesIterable, "Passed entities cannot be null");
        if (StringUtils.isEmpty(query) || ArrayUtils.isEmpty(params)) {
            return Collections.emptyList();
        }
        List<T> entities;
        if (entitiesIterable instanceof List) {
            entities = new ArrayList<>(((List<T>) entitiesIterable));
        } else {
            entities = new ArrayList<>(CollectionUtils.iterableToList(entitiesIterable));
        }
        List<T> toUpdate;
        int[] toUpdateEntitiesIndex;
        if (persistentEntity.hasPreUpdateEventListeners()) {
            toUpdate = new ArrayList<>(entities.size());
            toUpdateEntitiesIndex = new int[entities.size()];
            int i = 0;
            for (T child : entities) {
                if (child == null || persisted.contains(child)) {
                    continue;
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
                QUERY_LOG.debug("Executing Batch SQL Update: {}", query);
            }
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                for (T entity : toUpdate) {
                    updateEntitySetStatement(ps, connection, repositoryType, dialect, annotationMetadata, persistentEntity, params, persisted, entity);
                    // TODO: update the entity
                    ps.addBatch();
                }
                int[] result = ps.executeBatch();
                if (QUERY_LOG.isTraceEnabled()) {
                    QUERY_LOG.trace("Update batch operation updated {} records", Arrays.toString(result));
                }
            }
            if (persistentEntity.hasPostUpdateEventListeners()) {
                for (T entity : toUpdate) {
                    triggerPostUpdate(entity, persistentEntity, annotationMetadata);
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

    private <T> void updateEntitySetStatement(PreparedStatement ps,
                                              Connection connection,
                                              Class<?> repositoryType,
                                              Dialect dialect,
                                              AnnotationMetadata annotationMetadata,
                                              RuntimePersistentEntity<T> persistentEntity,
                                              String[] params,
                                              Set persisted,
                                              T entity) {
        for (int i = 0; i < params.length; i++) {
            Object value = entity;
            String propertyName = params[i];
            RuntimePersistentProperty<Object> property = (RuntimePersistentProperty<Object>) persistentEntity.getPropertyByName(propertyName);
            if (property == null) {
                PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(propertyName);
                if (propertyPath == null) {
                    throw new IllegalStateException("Cannot perform update for non-existent property: " + persistentEntity.getSimpleName() + "." + propertyName + " " + Arrays.toString(params));
                }
                property = (RuntimePersistentProperty<Object>) propertyPath.getProperty();
                List<Association> embeddedAssociations = new ArrayList<>(propertyPath.getAssociations().size());
                for (Association association : propertyPath.getAssociations()) {
                    RuntimePersistentProperty<Object> p = (RuntimePersistentProperty<Object>) association;
                    value = p.getProperty().get(value);
                    if (value == null) {
                        break;
                    }
                    if (association instanceof Embedded) {
                        embeddedAssociations.add(association);
                    } else {
                        cascadeUpdate(connection, repositoryType, annotationMetadata, embeddedAssociations, association, value, persisted);
                    }
                }
            }
            DataType dataType = property.getDataType();
            BeanProperty beanProperty = property.getProperty();
            if (dataType == DataType.ENTITY) {
                property = (RuntimePersistentProperty) getEntity(property.getType()).getIdentity();
                dataType = property.getDataType();
                beanProperty = property.getProperty();
                if (value != null) {
                    value = beanProperty.get(value);
                }
            } else {
                if (beanProperty.hasAnnotation(DateUpdated.class)) {
                    Object newValue = dateTimeProvider.getNow();
                    beanProperty.convertAndSet(value, newValue);
                    value = newValue;
                } else if (value != null) {
                    value = beanProperty.get(value);
                }
            }
            setStatementParameter(ps, i + 1, dataType, value, dialect);
        }
    }

    private void cascadeUpdate(Connection connection, Class<?> repositoryType,
                               AnnotationMetadata annotationMetadata,
                               List<Association> associations,
                               Association association,
                               Object value,
                               Set<Object> persisted) {
        if (value == null || persisted.contains(value)) {
            return;
        }
        final RuntimePersistentEntity<Object> associatedEntity = (RuntimePersistentEntity) association.getAssociatedEntity();
        final RuntimePersistentProperty<Object> idReader = getIdReader(value);
        final BeanProperty<Object, ?> idReaderProperty = idReader.getProperty();
        final Object id = idReaderProperty.get(value);
        if (id != null) {
            if (association.doesCascade(Relation.Cascade.PERSIST)) {
                final Relation.Kind kind = association.getKind();
                switch (kind) {
                    case ONE_TO_ONE:
                    case MANY_TO_ONE:
                        // TODO: support batch update
                        persisted.add(value);
                        final StoredInsert<Object> updateStatement = resolveEntityUpdate(
                                annotationMetadata,
                                repositoryType,
                                associatedEntity.getIntrospection().getBeanType(),
                                associatedEntity
                        );
                        updateOne(
                                connection,
                                repositoryType,
                                updateStatement.getDialect(),
                                annotationMetadata,
                                updateStatement.getPersistentEntity(),
                                updateStatement.getSql(),
                                updateStatement.getParameterBinding(),
                                value,
                                persisted
                        );
                        break;
                    case MANY_TO_MANY:
                    case ONE_TO_MANY:
                        // TODO: handle cascading updates to collections?
                    default:
                }
            }
        } else {
            if (association.doesCascade(Relation.Cascade.PERSIST)) {
                StoredInsert<Object> associatedInsert = resolveEntityInsert(
                        annotationMetadata,
                        repositoryType,
                        associatedEntity.getIntrospection().getBeanType(),
                        associatedEntity
                );
                persistOne(
                        connection,
                        annotationMetadata,
                        repositoryType,
                        associatedInsert,
                        associations,
                        value,
                        persisted
                );
            }
        }
    }

    @NonNull
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        StoredInsert<T> insert = resolveInsert(operation);
        final Class<?> repositoryType = operation.getRepositoryType();
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        T entity = operation.getEntity();
        return transactionOperations.executeWrite((status) ->
                persistOne(status.getConnection(), annotationMetadata, repositoryType, insert, Collections.emptyList(), entity, new HashSet<>(5)));
    }

    private <T> T persistOne(
            Connection connection,
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            StoredInsert<T> insert,
            List<Association> associations,
            T entity,
            Set<Object> persisted) {
        try {
            entity = cascadePrePersist(
                    connection,
                    annotationMetadata,
                    repositoryType,
                    insert.getPersistentEntity(),
                    associations,
                    entity,
                    persisted,
                    (RuntimePersistentProperty<T>) insert.getIdentity()
            );

            boolean generateId = insert.isGenerateId();
            String insertSql = insert.getSql();
            BeanProperty<T, Object> identity = insert.getIdentityProperty();
            final boolean hasGeneratedID = generateId && identity != null;

            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL Insert: {}", insertSql);
            }
            final RuntimePersistentEntity<T> persistentEntity = insert.getPersistentEntity();
            T resolvedEntity;
            try (PreparedStatement stmt = persistOnePreparedStatement(insert, connection, generateId, insertSql, hasGeneratedID)) {
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
                setInsertParameters(insert, resolvedEntity, stmt);
                stmt.executeUpdate();

                persisted.add(resolvedEntity);
                if (hasGeneratedID) {
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        Object id = getEntityId(generatedKeys, insert.getIdentity().getDataType(), identity.getType());
                        resolvedEntity = updateEntityId(identity, resolvedEntity, id);
                    } else {
                        throw new DataAccessException("ID failed to generate. No result returned.");
                    }
                }
                if (persistentEntity.hasPostPersistEventListeners()) {
                    resolvedEntity = triggerPostPersist(resolvedEntity, insert.getPersistentEntity(), annotationMetadata);
                }
            }
            if (identity == null) {
                return resolvedEntity;
            }
            return cascadePostPersist(
                    connection,
                    annotationMetadata,
                    repositoryType,
                    insert.getDialect(),
                    persistentEntity,
                    associations,
                    resolvedEntity,
                    identity.get(resolvedEntity),
                    persisted);
        } catch (SQLException e) {
            throw new DataAccessException("SQL Error executing INSERT: " + e.getMessage(), e);
        }
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

    private <T> PreparedStatement persistOnePreparedStatement(StoredInsert<T> insert, Connection connection, boolean generateId, String insertSql, boolean hasGeneratedID) throws SQLException {
        PreparedStatement stmt;
        if (hasGeneratedID && (insert.getDialect() == Dialect.ORACLE || insert.getDialect() == Dialect.SQL_SERVER)) {
            stmt = connection
                    .prepareStatement(insertSql, new String[] { insert.getIdentity().getPersistedName() });
        } else {
            stmt = connection
                    .prepareStatement(insertSql, generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
        }
        return stmt;
    }

    private <T> T cascadePrePersist(
            Connection connection,
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            RuntimePersistentEntity<T> persistentEntity,
            List<Association> associations,
            T parent,
            Set<Object> persisted,
            RuntimePersistentProperty<T> identityProperty) throws SQLException {
        if (identityProperty == null) {
            return parent;
        }
        for (RuntimeAssociation<T> childAssociation : persistentEntity.getAssociations()) {
            BeanProperty beanProperty = childAssociation.getProperty();
            Object value = beanProperty.get(parent);
            if (value == null) {
                break;
            }
            if (childAssociation instanceof Embedded) {
                Object newValue = cascadePrePersist(connection,
                        annotationMetadata,
                        repositoryType,
                        (RuntimePersistentEntity<T>) childAssociation.getAssociatedEntity(),
                        associated(associations, childAssociation),
                        (T) value,
                        persisted,
                        identityProperty);
                if (value != newValue) {
                    parent = (T) setProperty(beanProperty, parent, newValue);
                }
                continue;
            }
            // PrePersist insert is only allowed for non foreign keys
            if (childAssociation.doesCascade(Relation.Cascade.PERSIST) && !childAssociation.isForeignKey()) {
                if (childAssociation.getInverseSide().map(associations::contains).orElse(false)) {
                    continue;
                }
                switch (childAssociation.getKind()) {
                    case ONE_TO_ONE:
                    case MANY_TO_ONE:
                        parent = cascadePersistForChild(connection, annotationMetadata, repositoryType, associated(associations, childAssociation), parent, childAssociation, value, persisted);
                        break;
                    default:
                        throw new IllegalArgumentException("Cannot cascade PERSIST for relation: " + childAssociation.getKind());
                }
            }
        }
        return parent;
    }

    private <T> T cascadePostPersist(
            Connection connection,
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            Dialect dialect,
            RuntimePersistentEntity<T> persistentEntity,
            List<Association> associations,
            T parent,
            Object identityValue,
            Set<Object> persisted) throws SQLException {
        RuntimePersistentProperty<T> identityProperty = persistentEntity.getIdentity();
        if (identityValue == null) {
            return parent;
        }
        for (RuntimeAssociation<T> association : persistentEntity.getAssociations()) {
            BeanProperty beanProperty = association.getProperty();
            Object value = beanProperty.get(parent);
            if (value == null) {
                break;
            }
            if (association instanceof Embedded) {
                Object newValue = cascadePostPersist(connection,
                        annotationMetadata,
                        repositoryType,
                        dialect,
                        (RuntimePersistentEntity) association.getAssociatedEntity(),
                        associated(associations, association),
                        value,
                        identityValue,
                        persisted);
                if (value != newValue) {
                    parent = (T) setProperty(beanProperty, parent, newValue);
                }
                continue;
            }
            // PostPersist insert is only allowed for foreign keys
            if (association.doesCascade(Relation.Cascade.PERSIST) && association.isForeignKey()) {
                if (association.getInverseSide().map(associations::contains).orElse(false)) {
                    continue;
                }
                switch (association.getKind()) {
                    case ONE_TO_ONE:
                    case MANY_TO_ONE:
                        parent = cascadePersistForChild(connection, annotationMetadata, repositoryType, associated(associations, association), parent, association, value, persisted);
                        break;
                    case ONE_TO_MANY:
                    case MANY_TO_MANY:
                        parent = cascadePersistForChildren(connection,
                                annotationMetadata,
                                repositoryType,
                                associations,
                                parent,
                                persistentEntity,
                                identityProperty,
                                identityValue,
                                dialect,
                                association,
                                persisted);
                        break;
                    default:
                        throw new IllegalArgumentException("Cannot cascade PERSIST for relation: " + association.getKind());
                }
            }
        }
        return parent;
    }

    private <T> T cascadePersistForChild(Connection connection,
                                         AnnotationMetadata annotationMetadata,
                                         Class<?> repositoryType,
                                         List<Association> associations,
                                         T parent,
                                         RuntimeAssociation<T> childAssociation,
                                         Object child,
                                         Set<Object> persisted) {
        RuntimePersistentProperty<?> associatedId = childAssociation.getAssociatedEntity().getIdentity();
        BeanProperty associatedIdProperty = associatedId.getProperty();
        if (child == null || persisted.contains(child)) {
            return parent;
        }
        if (associatedId != null) {
            final Object id = associatedIdProperty.get(child);
            if (id != null) {
                return parent;
            }
        }
        final RuntimePersistentEntity<?> associatedEntity = childAssociation.getAssociatedEntity();
        final Class<?> associationType = associatedEntity.getIntrospection().getBeanType();
        StoredInsert<Object>  associatedInsert = resolveEntityInsert(
                annotationMetadata,
                repositoryType,
                associationType,
                associatedEntity
        );
        child = persistOne(
                connection,
                annotationMetadata,
                repositoryType,
                associatedInsert,
                associations,
                child,
                persisted
        );
        if (associatedIdProperty.isReadOnly()) {
            BeanProperty associationBeanProperty = childAssociation.getProperty();
            return (T) setProperty(associationBeanProperty, parent, child);
        }
        if (childAssociation.isForeignKey()) {
            RuntimeAssociation<?> inverseAssociation = childAssociation.getInverseSide().orElse(null);
            if (inverseAssociation != null) {
                BeanProperty property = inverseAssociation.getProperty();
                return (T) setProperty(property, parent, child);
            }
        }
        return parent;
    }

    private <T> T cascadePersistForChildren(Connection connection,
                                            AnnotationMetadata annotationMetadata,
                                            Class<?> repositoryType,
                                            List<Association> associations,
                                            T parent,
                                            RuntimePersistentEntity<T> persistentEntity,
                                            RuntimePersistentProperty<T> identityProperty,
                                            Object identityValue,
                                            Dialect dialect,
                                            RuntimeAssociation<T> association,
                                            Set<Object> persisted) throws SQLException {
        final RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
        final Class<?> associationType = associatedEntity.getIntrospection().getBeanType();
        final RuntimePersistentProperty<?> associatedId = associatedEntity.getIdentity();
        final BeanProperty associatedIdProperty = associatedId.getProperty();
        final BeanProperty associationProperty = association.getProperty();
        final Object children = associationProperty.get(parent);
        final RuntimeAssociation<?> inverse = association.getInverseSide().orElse(null);

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
                if (child == null || persisted.contains(child)) {
                    continue;
                }
                if (inverse != null) {
                    if (inverse.getKind() == Relation.Kind.MANY_TO_ONE) {
                        final BeanProperty property = inverse.getProperty();
                        child = setProperty(property, child, parent);
                        setChildren = true;
                    }
                }
                if (associatedId != null) {
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
                List<Association> newAssociations = associated(associations, association);
                StoredInsert<Object> insertQuery = resolveEntityInsert(
                        annotationMetadata,
                        repositoryType,
                        associationType,
                        associatedEntity
                );
                if (insertQuery.doesSupportBatch()) {
                    List<Object> persistedChildren = persistInBatch(
                            annotationMetadata,
                            repositoryType,
                            newAssociations,
                            toPersist,
                            insertQuery,
                            persisted
                    );
                    int k = 0;
                    for (Object child : persistedChildren) {
                        if (association.isForeignKey()) {
                            RuntimeAssociation<?> inverseAssociation = association.getInverseSide().orElse(null);
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
                        child = persistOne(
                                connection,
                                annotationMetadata,
                                repositoryType,
                                insertQuery,
                                newAssociations,
                                child,
                                persisted
                        );
                        if (association.isForeignKey()) {
                            RuntimeAssociation<?> inverseAssociation = association.getInverseSide().orElse(null);
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
            if (setChildren) {
                parent = convertAndSetWithValue((BeanProperty<T, Object>) associationProperty, parent, entities);
            }
            if (SqlQueryBuilder.isForeignKeyWithJoinTable(association)) {
                RuntimePersistentEntity<T> pe;
                if (associations.isEmpty()) {
                    pe = persistentEntity;
                } else {
                    Association assoc = associations.get(0);
                    if (assoc instanceof Embedded) {
                        pe = (RuntimePersistentEntity<T>) assoc.getOwner();
                    } else {
                        pe = (RuntimePersistentEntity<T>) assoc.getAssociatedEntity();
                    }
                }
                String sqlInsert = resolveAssociationInsert(repositoryType, pe, association);
                insertJoinTableAssociations(
                        dialect,
                        connection,
                        sqlInsert,
                        pe.getIdentity(),
                        identityValue,
                        associatedId,
                        entities);
            }
        }
        return parent;
    }

    private <T> void insertJoinTableAssociations(Dialect dialect,
                                                 Connection connection,
                                                 String sqlInsert,
                                                 RuntimePersistentProperty<T> parentIdentityProperty,
                                                 Object parentId,
                                                 RuntimePersistentProperty<?> childIdentity,
                                                 Iterable<Object> children) throws SQLException {
        if (dialect.allowBatch()) {
            try (PreparedStatement ps = connection.prepareStatement(sqlInsert)) {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing SQL Insert: {}", sqlInsert);
                }
                for (Object child : children) {
                    int i = 1;
                    setStatementParameter(
                            ps,
                            i++,
                            parentIdentityProperty.getDataType(),
                            parentId,
                            dialect);
                    List<Map.Entry<PersistentProperty, Object>> propsWithValues = idPropertiesWithValues(childIdentity, child).collect(Collectors.toList());
                    for (Map.Entry<PersistentProperty, Object> property : propsWithValues) {
                        setStatementParameter(
                                ps,
                                i++,
                                property.getKey().getDataType(),
                                property.getValue(),
                                dialect);
                    }
                    ps.addBatch();
                }
                int[] result = ps.executeBatch();
                if (QUERY_LOG.isTraceEnabled()) {
                    QUERY_LOG.trace("Insert batch operation inserted {} records", Arrays.toString(result));
                }
            }
        } else {
            for (Object child : children) {
                try (PreparedStatement ps = connection.prepareStatement(sqlInsert)) {
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing SQL Insert: {}", sqlInsert);
                    }
                    int i = 1;
                    setStatementParameter(
                            ps,
                            i++,
                            parentIdentityProperty.getDataType(),
                            parentId,
                            dialect);
                    List<Map.Entry<PersistentProperty, Object>> propsWithValues = idPropertiesWithValues(childIdentity, child).collect(Collectors.toList());
                    for (Map.Entry<PersistentProperty, Object> property : propsWithValues) {
                        setStatementParameter(
                                ps,
                                i++,
                                property.getKey().getDataType(),
                                property.getValue(),
                                dialect);
                    }
                    ps.execute();
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
        StoredInsert<T> insert = resolveInsert(operation);
        if (!insert.doesSupportBatch()) {
            return operation.split().stream()
                    .map(this::persist)
                    .collect(Collectors.toList());
        } else {
            //noinspection ConstantConditions
            return persistInBatch(
                    operation.getAnnotationMetadata(),
                    operation.getRepositoryType(),
                    Collections.emptyList(),
                    operation,
                    insert,
                    new HashSet(10)
            );
        }
    }

    private <T> List<T> persistInBatch(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            List<Association> associations,
            @NonNull Iterable<T> entities,
            StoredInsert<T> insert,
            Set<Object> persisted) {
        return transactionOperations.executeWrite((status) -> {
            Connection connection = status.getConnection();
            List<T> results = new ArrayList<>(10);
            boolean generateId = insert.isGenerateId();
            String insertSql = insert.getSql();
            BeanProperty<T, Object> identity = insert.getIdentityProperty();
            final boolean hasGeneratedID = generateId && identity != null;
            final RuntimePersistentEntity<T> persistentEntity = insert.getPersistentEntity();

            try (PreparedStatement stmt = connection
                    .prepareStatement(insertSql, generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {

                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Batch SQL Insert: {}", insertSql);
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
                    setInsertParameters(insert, entity, stmt);
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
                            Object id = getEntityId(generatedKeys, insert.getIdentity().getDataType(), identity.getType());
                            final T resolvedEntity = updateEntityId(identity, entity, id);
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
                    entity = cascadePostPersist(
                            connection,
                            annotationMetadata,
                            repositoryType,
                            insert.getDialect(),
                            persistentEntity,
                            associations,
                            entity,
                            persistentEntity.getIdentity().getProperty().get(entityBeforePostPersist),
                            persisted
                    );
                    if (entity != entityBeforePostPersist) {
                        it.set(entity);
                    }
                }
                return results;
            } catch (SQLException e) {
                throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
            }
        });
    }

    private Object getEntityId(ResultSet generatedKeys, DataType dataType, Class<Object> type) throws SQLException {
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
        return id;
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

    private List<Association> associated(List<Association> associations, Association association) {
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
}
