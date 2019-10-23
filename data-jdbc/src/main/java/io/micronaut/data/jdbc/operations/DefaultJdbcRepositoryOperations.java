/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.mapper.ColumnIndexResultSetReader;
import io.micronaut.data.jdbc.mapper.ColumnNameResultSetReader;
import io.micronaut.data.jdbc.mapper.JdbcQueryStatement;
import io.micronaut.data.jdbc.mapper.SqlResultConsumer;
import io.micronaut.data.jdbc.runtime.ConnectionCallback;
import io.micronaut.data.jdbc.runtime.PreparedStatementCallback;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.repository.GenericRepository;
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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
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

    private static final Object IGNORED_PARAMETER = new Object();
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
     */
    protected DefaultJdbcRepositoryOperations(@Parameter String dataSourceName,
                                              DataSource dataSource,
                                              @Parameter TransactionOperations<Connection> transactionOperations,
                                              @Named("io") @Nullable ExecutorService executorService,
                                              BeanContext beanContext,
                                              List<MediaTypeCodec> codecs) {
        super(new ColumnNameResultSetReader(), new ColumnIndexResultSetReader(), new JdbcQueryStatement(), codecs);
        ArgumentUtils.requireNonNull("dataSource", dataSource);
        ArgumentUtils.requireNonNull("transactionOperations", transactionOperations);
        this.dataSource = dataSource;
        this.transactionOperations = transactionOperations;
        this.executorService = executorService;
        Collection<BeanDefinition<GenericRepository>> beanDefinitions = beanContext.getBeanDefinitions(GenericRepository.class, Qualifiers.byStereotype(Repository.class));
        for (BeanDefinition<GenericRepository> beanDefinition : beanDefinitions) {
            String targetDs = beanDefinition.stringValue(Repository.class).orElse("default");
            if (targetDs.equalsIgnoreCase(dataSourceName)) {
                Dialect dialect = beanDefinition.findAnnotation(JdbcRepository.class).flatMap(av -> av.enumValue("dialect", Dialect.class)).orElse(Dialect.ANSI);
                dialects.put(beanDefinition.getBeanType(), dialect);
                QueryBuilder qb = queryBuilders.get(dialect);
                if (qb == null) {
                    queryBuilders.put(dialect, new SqlQueryBuilder(dialect));
                }
            }
        }
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
            try (PreparedStatement ps = prepareStatement(connection, preparedQuery, false, true)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Class<T> rootEntity = preparedQuery.getRootEntity();
                        Class<R> resultType = preparedQuery.getResultType();
                        if (resultType == rootEntity) {
                            @SuppressWarnings("unchecked")
                            RuntimePersistentEntity<R> persistentEntity = getEntity((Class<R>) rootEntity);
                            TypeMapper<ResultSet, R> mapper = new SqlResultEntityTypeMapper<>(
                                    persistentEntity,
                                    columnNameResultSetReader,
                                    preparedQuery.getJoinFetchPaths(),
                                    jsonCodec
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
                                        columnNameResultSetReader
                                );

                                return introspectedDataMapper.map(rs, resultType);
                            } else {
                                Object v = columnIndexResultSetReader.readDynamic(rs, 1, preparedQuery.getResultDataType());
                                if (resultType.isInstance(v)) {
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
                        columnNameResultSetReader
                );
                return introspectedDataMapper.map(rs, dtoType);
            }
        };
    }

    @Override
    public <T, R> boolean exists(@NonNull PreparedQuery<T, R> preparedQuery) {
        //noinspection ConstantConditions
        return transactionOperations.executeRead(status -> {
            try {
                Connection connection = status.getConnection();
                PreparedStatement ps = prepareStatement(connection, preparedQuery, false, true);
                ResultSet rs = ps.executeQuery();
                return rs.next();
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL query: " + e.getMessage(), e);
            }
        });
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {

        //noinspection ConstantConditions
        return transactionOperations.executeRead(status -> {
            Connection connection = status.getConnection();
            return findStream(preparedQuery, connection);
        });
    }

    private <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery, Connection connection) {
        Class<T> rootEntity = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();

        PreparedStatement ps;
        try {
            ps = prepareStatement(connection, preparedQuery, false, false);
        } catch (SQLException e) {
            throw new DataAccessException("SQL Error preparing Query: " + e.getMessage(), e);
        }

        ResultSet rs;
        try {
            rs = ps.executeQuery();
        } catch (SQLException e) {
            try {
                ps.close();
            } catch (SQLException e2) {
                // ignore
            }
            throw new DataAccessException("SQL Error executing Query: " + e.getMessage(), e);
        }
        boolean dtoProjection = preparedQuery.isDtoProjection();
        boolean isRootResult = resultType == rootEntity;
        Spliterator<R> spliterator;
        AtomicBoolean finished = new AtomicBoolean();
        if (isRootResult || dtoProjection) {
            SqlResultConsumer sqlMappingConsumer = preparedQuery.hasResultConsumer() ? preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class).orElse(null) : null;
            SqlTypeMapper<ResultSet, R> mapper;
            if (dtoProjection) {
                mapper = new SqlDTOMapper<>(
                        getEntity(rootEntity),
                        columnNameResultSetReader
                );
            } else {
                mapper = new SqlResultEntityTypeMapper<>(
                        getEntity(resultType),
                        columnNameResultSetReader,
                        preparedQuery.getJoinFetchPaths(),
                        jsonCodec
                );
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
                            Object v = columnIndexResultSetReader.readDynamic(rs, 1, preparedQuery.getResultDataType());
                            if (resultType.isInstance(v)) {
                                //noinspection unchecked
                                action.accept((R) v);
                            } else {
                                Object r = columnIndexResultSetReader.convertRequired(v, resultType);
                                action.accept((R) r);
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
    }

    private void closeResultSet(PreparedStatement ps, ResultSet rs, AtomicBoolean finished) {
        if (finished.compareAndSet(false, true)) {
            try {
                rs.close();
                ps.close();
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
        //noinspection ConstantConditions
        return transactionOperations.executeWrite(status -> {
            try {
                Connection connection = status.getConnection();
                try (PreparedStatement ps = prepareStatement(connection, preparedQuery, true, false)) {
                    return Optional.of(ps.executeUpdate());
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL UPDATE: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull BatchOperation<T> operation) {
        throw new UnsupportedOperationException("The deleteAll method via batch is unsupported. Execute the SQL update directly");
    }

    @NonNull
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        @SuppressWarnings("unchecked") StoredInsert<T> insert = resolveInsert(operation);
        //noinspection ConstantConditions
        return transactionOperations.executeWrite((status) -> {
            try {
                Connection connection = status.getConnection();
                T entity = operation.getEntity();
                boolean generateId = insert.isGenerateId();
                String insertSql = insert.getSql();
                BeanProperty<T, Object> identity = insert.getIdentityProperty();
                final boolean hasGeneratedID = generateId && identity != null;

                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing SQL Insert: {}", insertSql);
                }

                PreparedStatement stmt;
                if (hasGeneratedID && (insert.getDialect() == Dialect.ORACLE || insert.getDialect() == Dialect.SQL_SERVER)) {
                    stmt = connection
                            .prepareStatement(insertSql, new String[] { insert.getIdentity().getPersistedName() });
                } else {
                    stmt = connection
                            .prepareStatement(insertSql, generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
                }

                setInsertParameters(insert, entity, stmt);
                stmt.executeUpdate();
                if (hasGeneratedID) {
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        long id = generatedKeys.getLong(1);
                        if (identity.getType().isInstance(id)) {
                            identity.set(entity, id);
                        } else {
                            identity.convertAndSet(entity, id);
                        }
                    } else {
                        throw new DataAccessException("ID failed to generate. No result returned.");
                    }
                }
                return entity;
            } catch (SQLException e) {
                throw new DataAccessException("SQL Error executing INSERT: " + e.getMessage(), e);
            }
        });

    }

    private <T, R> PreparedStatement prepareStatement(
            Connection connection,
            @NonNull PreparedQuery<T, R> preparedQuery,
            boolean isUpdate,
            boolean isSingleResult) throws SQLException {
        Object[] queryParameters = preparedQuery.getParameterArray();
        int[] parameterBinding = preparedQuery.getIndexedParameterBinding();
        DataType[] parameterTypes = preparedQuery.getIndexedParameterTypes();
        String query = preparedQuery.getQuery();

        final boolean hasIn = preparedQuery.hasInExpression();
        if (hasIn) {
            Matcher matcher = IN_EXPRESSION_PATTERN.matcher(query);
            // this has to be done is two passes, one to remove and establish new indexes
            // and again to expand existing indexes
            while (matcher.find()) {
                int inIndex = Integer.valueOf(matcher.group(1));
                int queryParameterIndex = parameterBinding[inIndex - 1];
                Object value = queryParameters[queryParameterIndex];

                if (value == null) {
                    query = matcher.replaceFirst(NOT_TRUE_EXPRESSION);
                    queryParameters[queryParameterIndex] = IGNORED_PARAMETER;
                } else {
                    int size = sizeOf(value);
                    if (size == 0) {
                        queryParameters[queryParameterIndex] = IGNORED_PARAMETER;
                        query = matcher.replaceFirst(NOT_TRUE_EXPRESSION);
                    } else {
                        String replacement = " IN(" + String.join(",", Collections.nCopies(size, "?")) + ")";
                        query = matcher.replaceFirst(replacement);
                    }
                }
                matcher = IN_EXPRESSION_PATTERN.matcher(query);
            }
        }

        if (!isUpdate) {
            Pageable pageable = preparedQuery.getPageable();
            if (pageable != Pageable.UNPAGED) {
                Class<T> rootEntity = preparedQuery.getRootEntity();
                Sort sort = pageable.getSort();
                Dialect dialect = dialects.getOrDefault(preparedQuery.getRepositoryType(), Dialect.ANSI);
                QueryBuilder queryBuilder = queryBuilders.getOrDefault(dialect, DEFAULT_SQL_BUILDER);
                if (sort.isSorted()) {
                    query += queryBuilder.buildOrderBy(getEntity(rootEntity), sort).getQuery();
                } else if (isSqlServerWithoutOrderBy(query, dialect)) {
                    // SQL server requires order by
                    RuntimePersistentEntity<T> persistentEntity = getEntity(rootEntity);
                    sort = sortById(persistentEntity);
                    query += queryBuilder.buildOrderBy(persistentEntity, sort).getQuery();
                }
                if (isSingleResult && pageable.getOffset() > 0) {
                    pageable = Pageable.from(pageable.getNumber(), 1);
                }
                query += queryBuilder.buildPagination(pageable).getQuery();
            }
        }

        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Query: {}", query);
        }
        final PreparedStatement ps = connection.prepareStatement(query);
        int index = 1;
        for (int i = 0; i < parameterBinding.length; i++) {
            int parameterIndex = parameterBinding[i];
            DataType dataType = parameterTypes[i];
            Object value;
            if (parameterIndex > -1) {
                value = queryParameters[parameterIndex];
            } else {
                String[] indexedParameterPaths = preparedQuery.getIndexedParameterPaths();
                String propertyPath = indexedParameterPaths[i];
                if (propertyPath != null) {

                    String lastUpdatedProperty = preparedQuery.getLastUpdatedProperty();
                    if (lastUpdatedProperty != null && lastUpdatedProperty.equals(propertyPath)) {
                        Class<?> lastUpdatedType = preparedQuery.getLastUpdatedType();
                        if (lastUpdatedType == null) {
                            throw new IllegalStateException("Could not establish last updated time for entity: " + preparedQuery.getRootEntity());
                        }
                        Object timestamp = ConversionService.SHARED.convert(OffsetDateTime.now(), lastUpdatedType).orElse(null);
                        if (timestamp == null) {
                            throw new IllegalStateException("Unsupported date type: " + lastUpdatedType);
                        }
                        value = timestamp;
                    } else {
                        int j = propertyPath.indexOf('.');
                        if (j > -1) {
                            String subProp = propertyPath.substring(j + 1);
                            value = queryParameters[Integer.valueOf(propertyPath.substring(0, j))];
                            value = BeanWrapper.getWrapper(value).getRequiredProperty(subProp, Object.class);
                        } else {
                            throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
                        }
                    }
                } else {
                    throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
                }
            }

            if (QUERY_LOG.isTraceEnabled()) {
                QUERY_LOG.trace("Binding parameter at position {} to value {}", index, value);
            }
            if (value == null) {
                setStatementParameter(ps, index++, dataType, null);
            } else if (value != IGNORED_PARAMETER) {
                if (value instanceof Iterable) {
                    Iterable iter = (Iterable) value;
                    for (Object o : iter) {
                        setStatementParameter(ps, index++, dataType, o);
                    }
                } else if (value.getClass().isArray()) {
                    int len = Array.getLength(value);
                    for (int j = 0; j < len; j++) {
                        Object o = Array.get(value, j);
                        setStatementParameter(ps, index++, dataType, o);
                    }
                } else {
                    setStatementParameter(ps, index++, dataType, value);
                }
            }
        }
        return ps;
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
    public <T> Iterable<T> persistAll(@NonNull BatchOperation<T> operation) {
        @SuppressWarnings("unchecked") StoredInsert<T> insert = resolveInsert(operation);
        if (!insert.doesSupportBatch()) {
            List<T> results = new ArrayList<>();
            for (T entity : operation) {
                results.add(persist(new InsertOperation<T>() {
                    @NonNull
                    @Override
                    public T getEntity() {
                        return entity;
                    }

                    @NonNull
                    @Override
                    public Class<T> getRootEntity() {
                        return operation.getRootEntity();
                    }

                    @Override
                    public String getName() {
                        return operation.getName();
                    }

                    @Override
                    public AnnotationMetadata getAnnotationMetadata() {
                        return operation.getAnnotationMetadata();
                    }
                }));
            }
            return results;
        } else {
            //noinspection ConstantConditions
            return transactionOperations.executeWrite((status) -> {
                Connection connection = status.getConnection();
                List<T> results = new ArrayList<>(10);
                boolean generateId = insert.isGenerateId();
                String insertSql = insert.getSql();
                BeanProperty<T, Object> identity = insert.getIdentityProperty();
                final boolean hasGeneratedID = generateId && identity != null;

                try {
                    PreparedStatement stmt;
                    if (hasGeneratedID && insert.getDialect() == Dialect.ORACLE) {
                        stmt = connection
                                .prepareStatement(insertSql, new String[] { identity.getName() });
                    } else {
                        stmt = connection
                                .prepareStatement(insertSql, generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
                    }
                    if (QUERY_LOG.isDebugEnabled()) {
                        QUERY_LOG.debug("Executing Batch SQL Insert: {}", insertSql);
                    }
                    for (T entity : operation) {
                        setInsertParameters(insert, entity, stmt);
                        stmt.addBatch();
                        results.add(entity);
                    }
                    stmt.executeBatch();


                    if (hasGeneratedID) {
                        Iterator<T> resultIterator = results.iterator();
                        ResultSet generatedKeys = stmt.getGeneratedKeys();
                        while (resultIterator.hasNext()) {
                            T entity = resultIterator.next();
                            if (!generatedKeys.next()) {
                                throw new DataAccessException("Failed to generate ID for entity: " + entity);
                            } else {
                                long id = generatedKeys.getLong(1);
                                if (identity.getType().isInstance(id)) {
                                    identity.set(entity, id);
                                } else {
                                    identity.convertAndSet(entity, id);
                                }
                            }
                        }
                    }
                    return results;
                } catch (SQLException e) {
                    throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
                }
            });
        }
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
        try {
            return callback.call(transactionOperations.getConnection().prepareStatement(sql));
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
                columnNameResultSetReader
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
