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
import io.micronaut.core.convert.ConversionService;
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
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.*;
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
import java.sql.*;
import java.util.*;
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
                            TypeMapper<ResultSet, R> mapper = new SqlResultEntityTypeMapper<>(
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
                            R result = mapper.map(rs, resultType);
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
                TypeMapper<ResultSet, E> mapper = new SqlResultEntityTypeMapper<>(
                        prefix,
                        entity,
                        columnNameResultSetReader,
                        jsonCodec
                );
                return mapper.map(rs, type);
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
                    // Cannot stream ResultSet for joined query
                    if (!joinFetchPaths.isEmpty()) {
                        Map<Object, Object> processedEntities = new HashMap<>();
                        List<R> values = new ArrayList<>();
                        try {
                            while (entityTypeMapper.hasNext(rs)) {
                                Object id = entityTypeMapper.readId(rs);
                                if (id != null) {
                                    Object processedEntity = processedEntities.get(id);
                                    if (processedEntity != null) {
                                        entityTypeMapper.readChildren(rs, (R) processedEntity);
                                        continue;
                                    }
                                }
                                R o = entityTypeMapper.map(rs, resultType);
                                if (sqlMappingConsumer != null) {
                                    sqlMappingConsumer.accept(rs, o);
                                }
                                values.add(o);
                                if (id != null) {
                                    processedEntities.put(id, o);
                                }
                            }
                            return values.stream();
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
        if (operation.all()) {
            final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
            final String query = annotationMetadata.stringValue(Query.class).orElse(null);
            if (query == null) {
                throw new UnsupportedOperationException("The deleteAll method via batch is unsupported. Execute the SQL update directly");
            }
            final String[] params = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
            if (params.length != 0) {
                throw new IllegalStateException("Unexpected parameters: " + Arrays.toString(params));
            }

            return transactionOperations.executeWrite(status -> {
                try {
                    Connection connection = status.getConnection();
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing SQL DELETE ALL: {}", query);
                    }
                    try (PreparedStatement ps = connection.prepareStatement(query)) {
                        return Optional.of(ps.executeUpdate());
                    }
                } catch (SQLException e) {
                    throw new DataAccessException("Error executing SQL DELETE: " + e.getMessage(), e);
                }
            });

        }
        return Optional.of(
                operation.split().stream()
                        .mapToInt(this::delete)
                        .sum()
        );
    }

    @Override
    public <T> int delete(@NonNull DeleteOperation<T> operation) {
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        final String[] params = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
        final String query = annotationMetadata.stringValue(Query.class).orElse(null);
        final Dialect dialect = queryBuilders.getOrDefault(operation.getRepositoryType(), DEFAULT_SQL_BUILDER).dialect();

        Objects.requireNonNull(query, "Query cannot be null");

        final Object opEntity = operation.getEntity();
        Objects.requireNonNull(opEntity, "Passed entity cannot be null");

        final RuntimePersistentEntity<Object> finalPersistentEntity = (RuntimePersistentEntity<Object>) getEntity(opEntity.getClass());
        final Object finalEntity;
        if (finalPersistentEntity.hasPreRemoveEventListeners()) {
            finalEntity = triggerPreRemove(opEntity, finalPersistentEntity, annotationMetadata);
            if (finalEntity == null) {
                // operation vetoed
                return 0;
            }
        } else {
            finalEntity = opEntity;
        }
        return transactionOperations.executeWrite(status -> {
            try {
                Connection connection = status.getConnection();
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing SQL DELETE: {}", query);
                }
                Object entity = finalEntity;
                RuntimePersistentEntity<Object> persistentEntity = finalPersistentEntity;
                final RuntimePersistentProperty<Object> identity = persistentEntity.getIdentity();
                if (identity != null) {
                    if (identity instanceof Embedded) {
                        final BeanProperty idProp = identity.getProperty();
                        final Object idEntity = idProp.get(entity);
                        if (idEntity == null) {
                            throw new IllegalStateException("Cannot delete an entity with null ID: " + entity);
                        }
                        entity = idEntity;
                        persistentEntity = (RuntimePersistentEntity<Object>) getEntity(idEntity.getClass());
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    for (int i = 0; i < params.length; i++) {
                        String propertyName = params[i];
                        if (propertyName.isEmpty()) {
                            setStatementParameter(ps, i + 1, DataType.ENTITY, entity, dialect);
                        } else {
                            if (propertyName.startsWith("0.")) {
                                propertyName = propertyName.replace("0.", "");
                            }
                            RuntimePersistentProperty<Object> pp = persistentEntity.getPropertyByName(propertyName);
                            if (pp == null) {
                                throw new IllegalStateException("Cannot perform delete for non-existent property: " + persistentEntity.getSimpleName() + "." + propertyName);
                            }
                            setStatementParameter(ps, i + 1, pp.getDataType(), pp.getProperty().get(entity), dialect);
                        }
                    }
                    int updated = ps.executeUpdate();
                    if (updated > 0 && persistentEntity.hasPostRemoveEventListeners()) {
                        triggerPostRemove(finalEntity, persistentEntity, annotationMetadata);
                    }
                    return updated;
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL DELETE: " + e.getMessage(), e);
            }
        });
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        final AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
        final String[] params = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
        final String query = annotationMetadata.stringValue(Query.class).orElse(null);
        final T entity = operation.getEntity();
        final Set persisted = new HashSet(10);
        final Class<?> repositoryType = operation.getRepositoryType();
        return updateOne(repositoryType, annotationMetadata, query, params, entity, persisted);
    }

    private <T> T updateOne(Class<?> repositoryType, AnnotationMetadata annotationMetadata, String query, String[] params, T entity, Set persisted) {
        Objects.requireNonNull(entity, "Passed entity cannot be null");
        if (StringUtils.isNotEmpty(query) && ArrayUtils.isNotEmpty(params)) {
            final RuntimePersistentEntity<T> persistentEntity =
                    (RuntimePersistentEntity<T>) getEntity(entity.getClass());
            final Dialect dialect = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER).dialect();
            final T resolvedEntity;
            if (persistentEntity.hasPreUpdateEventListeners()) {
                resolvedEntity = triggerPreUpdate(entity, persistentEntity, annotationMetadata);
                if (resolvedEntity == null) {
                    return entity;
                }
            } else {
                resolvedEntity = entity;
            }
            return transactionOperations.executeWrite(status -> {
                try {
                    Connection connection = status.getConnection();
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing SQL UPDATE: {}", query);
                    }
                    try (PreparedStatement ps = connection.prepareStatement(query)) {
                        for (int i = 0; i < params.length; i++) {
                            String propertyName = params[i];
                            Object value = resolvedEntity;
                            RuntimePersistentProperty<Object> property = (RuntimePersistentProperty<Object>) persistentEntity.getPropertyByName(propertyName);
                            if (property == null) {
                                List<PersistentProperty> embeddedProperties = persistentEntity.getPropertiesInPath(propertyName);
                                if (embeddedProperties.isEmpty()) {
                                    throw new IllegalStateException("Cannot perform update for non-existent property: " + persistentEntity.getSimpleName() + "." + propertyName);
                                }
                                property = (RuntimePersistentProperty) CollectionUtils.last(embeddedProperties);
                                for (PersistentProperty embeddedProperty : embeddedProperties) {
                                    if (embeddedProperty instanceof Association) {
                                        Association association = (Association) embeddedProperty;
                                        if (association.getKind() != Relation.Kind.EMBEDDED) {
                                            RuntimePersistentProperty<Object> p = (RuntimePersistentProperty<Object>) embeddedProperty;
                                            if (value != null) {
                                                value = p.getProperty().get(value);
                                            }
                                            cascadeUpdate(repositoryType, annotationMetadata, association, value, persisted);
                                            continue;
                                        }
                                    }
                                    if (value == null || property == embeddedProperty) {
                                        break;
                                    }
                                    RuntimePersistentProperty<Object> p = (RuntimePersistentProperty<Object>) embeddedProperty;
                                    value = p.getProperty().get(value);
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
                            setStatementParameter(
                                    ps,
                                    i + 1,
                                    dataType,
                                    value,
                                    dialect
                            );
                        }
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
            });
        }
        return entity;
    }

    private void cascadeUpdate(Class<?> repositoryType, AnnotationMetadata annotationMetadata, Association association, Object value, Set<Object> persisted) {
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
                        persisted.add(value);
                        final StoredInsert<Object> updateStatement = resolveEntityUpdate(
                                annotationMetadata,
                                repositoryType,
                                associatedEntity.getIntrospection().getBeanType(),
                                associatedEntity
                        );
                        updateOne(
                                repositoryType,
                                annotationMetadata,
                                updateStatement.getSql(),
                                updateStatement.getParameterBinding(),
                                value,
                                persisted
                        );
                        break;
                    case MANY_TO_MANY:
                    case ONE_TO_MANY:
                        // TODO: handle cascading updates to collections?

                    case EMBEDDED:
                    default:
                        // TODO: embedded type updates
                }
            }
        } else {
            if (association.doesCascade(Relation.Cascade.PERSIST)) {
                StoredInsert associatedInsert = resolveEntityInsert(
                        annotationMetadata,
                        repositoryType,
                        associatedEntity.getIntrospection().getBeanType(),
                        associatedEntity
                );
                persistOne(
                        annotationMetadata,
                        repositoryType,
                        associatedInsert,
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
        return persistOne(annotationMetadata, repositoryType, insert, entity, new HashSet(5));
    }

    private <T> T persistOne(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            StoredInsert<T> insert,
            T entity,
            Set persisted) {
        return transactionOperations.executeWrite((status) -> {
            try {
                Connection connection = status.getConnection();
                boolean generateId = insert.isGenerateId();
                String insertSql = insert.getSql();
                BeanProperty<T, Object> identity = insert.getIdentityProperty();
                final boolean hasGeneratedID = generateId && identity != null;

                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing SQL Insert: {}", insertSql);
                }

                try (PreparedStatement stmt = persistOnePreparedStatement(insert, connection, generateId, insertSql, hasGeneratedID)) {

                    final RuntimePersistentEntity<T> pe = insert.getPersistentEntity();
                    T resolvedEntity;
                    if (pe.hasPrePersistEventListeners()) {
                        final T newEntity = triggerPrePersist(entity, pe, annotationMetadata);
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
                    if (pe.hasPostPersistEventListeners()) {
                        resolvedEntity = triggerPostPersist(resolvedEntity, insert.getPersistentEntity(), annotationMetadata);
                    }
                    cascadeInserts(
                            annotationMetadata,
                            repositoryType,
                            insert,
                            resolvedEntity,
                            persisted,
                            connection,
                            insert.getIdentity()
                    );
                    return resolvedEntity;
                }
            } catch (SQLException e) {
                throw new DataAccessException("SQL Error executing INSERT: " + e.getMessage(), e);
            }
        });
    }

    private <T> T updateEntityId(BeanProperty<T, Object> identity, T resolvedEntity, Object id) {
        if (id == null) {
            return resolvedEntity;
        }
        if (identity.getType().isInstance(id)) {
            if (identity.isReadOnly()) {
                if (identity.hasSetterOrConstructorArgument()) {
                    resolvedEntity = identity.withValue(resolvedEntity, id);
                } else {
                    return resolvedEntity;
                }
            } else {
                identity.set(resolvedEntity, id);
            }
        } else {
            if (identity.isReadOnly()) {
                if (identity.hasSetterOrConstructorArgument()) {
                    final Object converted = ConversionService.SHARED.convert(id, identity.asArgument()).orElse(null);
                    if (converted != null) {
                        resolvedEntity = identity.withValue(resolvedEntity, converted);
                    }
                } else {
                    return resolvedEntity;
                }
            } else {
                identity.convertAndSet(resolvedEntity, id);
            }
        }
        return resolvedEntity;
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

    private <T> void cascadeInserts(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            StoredInsert<T> insert,
            T entity,
            Set<Object> persisted,
            Connection connection,
            RuntimePersistentProperty<T> identityProperty) throws SQLException {
        if (identityProperty == null) {
            return;
        }
        BeanProperty<T, ?> identity = identityProperty.getProperty();
        Dialect dialect = insert.getDialect();
        final RuntimePersistentEntity<T> persistentEntity = (RuntimePersistentEntity<T>) getEntity(entity.getClass());
        for (RuntimeAssociation<T> association : persistentEntity.getAssociations()) {
            if (association.doesCascade(Relation.Cascade.PERSIST)) {
                final Relation.Kind kind = association.getKind();
                final RuntimePersistentEntity<?> associatedEntity =
                        association.getAssociatedEntity();
                final Class<?> associationType = associatedEntity.getIntrospection().getBeanType();
                final RuntimePersistentProperty<?> associatedId = associatedEntity.getIdentity();
                final BeanProperty associatedIdProperty = associatedId.getProperty();
                switch (kind) {
                    case ONE_TO_ONE:
                    case MANY_TO_ONE:
                        // in the case of an one-to-one we ensure the inverse side set
                        final Object associated = association.getProperty().get(entity);
                        if (associated == null || persisted.contains(associated)) {
                            continue;
                        }
                        if (association.isForeignKey()) {
                            association.getInverseSide().ifPresent(inverse -> {
                                final BeanProperty property = inverse.getProperty();
                                property.set(associated, entity);
                            });
                        }

                        // get the insert operation
                        StoredInsert<Object>  associatedInsert = resolveEntityInsert(
                                annotationMetadata,
                                repositoryType,
                                associationType,
                                associatedEntity
                        );

                        if (associatedId != null) {
                            final Object id = associatedIdProperty.get(associated);
                            if (id != null) {
                                continue;
                            }
                        }

                        persistOne(
                                annotationMetadata,
                                repositoryType,
                                associatedInsert,
                                associated,
                                persisted
                        );

                        break;
                    case ONE_TO_MANY:
                    case MANY_TO_MANY:
                    default:
                        final Object many = association.getProperty().get(entity);
                        final RuntimeAssociation<?> inverse
                                = association.getInverseSide().orElse(null);
                        if (many instanceof Iterable) {
                            Iterable<Object> entities = (Iterable) many;
                            if (!entities.iterator().hasNext()) {
                                return;
                            }
                            List<Object> toPersist;
                            if (many instanceof Collection) {
                                toPersist = new ArrayList<>(((Collection<?>) many).size());
                            } else {
                                toPersist = new ArrayList<>(15);
                            }
                            for (Object o : entities) {
                                if (o == null || persisted.contains(o)) {
                                    continue;
                                }
                                if (inverse != null) {
                                    if (inverse.getKind() == Relation.Kind.MANY_TO_ONE) {
                                        final BeanProperty property = inverse.getProperty();
                                        property.set(o, entity);
                                    }
                                }
                                if (associatedId != null) {
                                    final BeanProperty bp = associatedIdProperty;
                                    final Object id = bp.get(o);
                                    if (id == null) {
                                        toPersist.add(o);
                                    }
                                }
                            }
                            if (!toPersist.isEmpty()) {
                                StoredInsert<Object> insertQuery = resolveEntityInsert(
                                        annotationMetadata,
                                        repositoryType,
                                        associationType,
                                        associatedEntity
                                );
                                if (insertQuery.doesSupportBatch()) {
                                    persistInBatch(
                                            annotationMetadata,
                                            repositoryType,
                                            toPersist,
                                            insertQuery,
                                            persisted
                                    );
                                } else {
                                    for (Object o : toPersist) {
                                        persistOne(
                                                annotationMetadata,
                                                repositoryType,
                                                insertQuery,
                                                o,
                                                persisted
                                        );
                                    }
                                }
                            }
                            if (SqlQueryBuilder.isForeignKeyWithJoinTable(association)) {
                                String sqlInsert = resolveAssociationInsert(repositoryType, persistentEntity, association);
                                insertJoinTableAssociations(
                                        dialect,
                                        connection,
                                        sqlInsert,
                                        insert.doesSupportBatch(),
                                        identityProperty, identity.get(entity),
                                        associatedId,
                                        entities);
                            }
                        }
                }
            }
        }
    }

    private <T> void insertJoinTableAssociations(Dialect dialect,
                                                 Connection connection,
                                                 String sqlInsert,
                                                 boolean supportBatch,
                                                 RuntimePersistentProperty<T> parentIdentityProperty,
                                                 Object parentId,
                                                 RuntimePersistentProperty<?> childIdentity,
                                                 Iterable<Object> children) throws SQLException {
        if (supportBatch) {
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
                ps.executeBatch();
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
                throw new IllegalStateException("Identity cannot be missing for: " + association);
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
                    operation,
                    insert,
                    new HashSet(10)
            );
        }
    }

    private <T> Iterable<T> persistInBatch(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            @NonNull Iterable<T> entities,
            StoredInsert<T> insert,
            Set persisted) {
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
                stmt.executeBatch();


                if (hasGeneratedID && !identity.isReadOnly()) {
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
                for (T result : results) {
                    if (hasPostPersistEventListeners) {
                        result = triggerPostPersist(result, persistentEntity, annotationMetadata);
                    }
                    cascadeInserts(
                            annotationMetadata,
                            repositoryType,
                            insert,
                            result,
                            persisted,
                            connection,
                            insert.getIdentity()
                    );
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
}
