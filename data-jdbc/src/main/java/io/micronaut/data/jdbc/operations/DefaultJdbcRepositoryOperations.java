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

import io.micronaut.aop.InvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.connection.annotation.Connectable;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.jdbc.convert.JdbcConversionContext;
import io.micronaut.data.jdbc.mapper.ColumnIndexCallableResultReader;
import io.micronaut.data.jdbc.mapper.ColumnIndexResultSetReader;
import io.micronaut.data.jdbc.mapper.ColumnNameCallableResultReader;
import io.micronaut.data.jdbc.mapper.ColumnNameResultSetReader;
import io.micronaut.data.jdbc.mapper.JdbcQueryStatement;
import io.micronaut.data.jdbc.mapper.SqlResultConsumer;
import io.micronaut.data.jdbc.runtime.ConnectionCallback;
import io.micronaut.data.jdbc.runtime.PreparedStatementCallback;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.DeleteReturningBatchOperation;
import io.micronaut.data.model.runtime.DeleteReturningOperation;
import io.micronaut.data.model.runtime.EntityOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.operations.DeleteReturningRepositoryOperations;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.convert.RuntimePersistentPropertyConversionContext;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.mapper.DTOMapper;
import io.micronaut.data.runtime.mapper.ResultConsumer;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.TypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntitiesOperations;
import io.micronaut.data.runtime.operations.internal.AbstractSyncEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery;
import io.micronaut.data.runtime.operations.internal.sql.*;
import io.micronaut.data.runtime.support.AbstractConversionContext;
import io.micronaut.json.JsonMapper;
import io.micronaut.transaction.TransactionOperations;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Implementation of {@link JdbcRepositoryOperations}.
 *
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@EachBean(DataSource.class)
@Internal
public final class DefaultJdbcRepositoryOperations extends AbstractSqlRepositoryOperations<ResultSet, PreparedStatement, SQLException> implements
    JdbcRepositoryOperations,
    DeleteReturningRepositoryOperations,
    AsyncCapableRepository,
    ReactiveCapableRepository,
    AutoCloseable,
    SyncCascadeOperations.SyncCascadeOperationsHelper<DefaultJdbcRepositoryOperations.JdbcOperationContext> {

    private final ConnectionOperations<Connection> connectionOperations;
    private final TransactionOperations<Connection> transactionOperations;
    private final DataSource dataSource;
    private final DataSource unwrapedDataSource;
    private ExecutorAsyncOperations asyncOperations;
    private ExecutorService executorService;
    private final SyncCascadeOperations<JdbcOperationContext> cascadeOperations;
    private final DataJdbcConfiguration jdbcConfiguration;
    @Nullable
    private final SchemaTenantResolver schemaTenantResolver;
    private final JdbcSchemaHandler schemaHandler;

    private final ColumnNameCallableResultReader columnNameCallableResultReader;
    private final ColumnIndexCallableResultReader columnIndexCallableResultReader;
    private final Map<Dialect, List<SqlExceptionMapper>> sqlExceptionMappers = new EnumMap<>(Dialect.class);

    /**
     * Default constructor.
     *
     * @param dataSourceName              The data source name
     * @param jdbcConfiguration           The jdbcConfiguration
     * @param dataSource                  The datasource
     * @param connectionOperations        The connection operations
     * @param transactionOperations       The JDBC operations for the data source
     * @param executorService             The executor service
     * @param beanContext                 The bean context
     * @param dateTimeProvider            The dateTimeProvider
     * @param entityRegistry              The entity registry
     * @param conversionService           The conversion service
     * @param attributeConverterRegistry  The attribute converter registry
     * @param schemaTenantResolver        The schema tenant resolver
     * @param schemaHandler               The schema handler
     * @param jsonMapper                  The JSON mapper
     * @param sqlJsonColumnMapperProvider The SQL JSON column mapper provider
     * @param sqlExceptionMapperList The SQL exception mapper list
     */
    @Internal
    @SuppressWarnings("ParameterNumber")
    DefaultJdbcRepositoryOperations(@Parameter String dataSourceName,
                                    @Parameter DataJdbcConfiguration jdbcConfiguration,
                                    DataSource dataSource,
                                    @Parameter ConnectionOperations<Connection> connectionOperations,
                                    @Parameter TransactionOperations<Connection> transactionOperations,
                                    @Named("io") @Nullable ExecutorService executorService,
                                    BeanContext beanContext,
                                    @NonNull DateTimeProvider dateTimeProvider,
                                    RuntimeEntityRegistry entityRegistry,
                                    DataConversionService conversionService,
                                    AttributeConverterRegistry attributeConverterRegistry,
                                    @Nullable SchemaTenantResolver schemaTenantResolver,
                                    JdbcSchemaHandler schemaHandler,
                                    @Nullable JsonMapper jsonMapper,
                                    SqlJsonColumnMapperProvider<ResultSet> sqlJsonColumnMapperProvider,
                                    List<SqlExceptionMapper> sqlExceptionMapperList,
                                    List<SqlExecutionObserver> observers) {
        super(
            dataSourceName,
            new ColumnNameResultSetReader(conversionService),
            new ColumnIndexResultSetReader(conversionService),
            new JdbcQueryStatement(conversionService, jdbcConfiguration),
            dateTimeProvider,
            entityRegistry,
            beanContext,
            conversionService,
            attributeConverterRegistry,
            jsonMapper,
            sqlJsonColumnMapperProvider,
            observers);
        this.schemaTenantResolver = schemaTenantResolver;
        this.schemaHandler = schemaHandler;
        this.connectionOperations = connectionOperations;
        ArgumentUtils.requireNonNull("dataSource", dataSource);
        ArgumentUtils.requireNonNull("transactionOperations", transactionOperations);
        this.dataSource = dataSource;
        this.unwrapedDataSource = DelegatingDataSource.unwrapDataSource(dataSource);
        this.transactionOperations = transactionOperations;
        this.executorService = executorService;
        this.cascadeOperations = new SyncCascadeOperations<>(conversionService, this);
        this.jdbcConfiguration = jdbcConfiguration;
        this.columnNameCallableResultReader = new ColumnNameCallableResultReader(conversionService);
        this.columnIndexCallableResultReader = new ColumnIndexCallableResultReader(conversionService);
        if (CollectionUtils.isNotEmpty(sqlExceptionMapperList)) {
            for (SqlExceptionMapper sqlExceptionMapper : sqlExceptionMapperList) {
                Dialect dialect = sqlExceptionMapper.getDialect();
                List<SqlExceptionMapper> dialectSqlExceptionMapperList = sqlExceptionMappers.get(dialect);
                if (dialectSqlExceptionMapperList == null) {
                    dialectSqlExceptionMapperList = new ArrayList<>();
                }
                dialectSqlExceptionMapperList.add(sqlExceptionMapper);
                sqlExceptionMappers.put(dialect, dialectSqlExceptionMapperList);
            }
        }
    }

    @NonNull
    private ExecutorService newLocalThreadPool() {
        this.executorService = Executors.newCachedThreadPool();
        return executorService;
    }

    @Override
    protected Integer getFirstResultSetIndex() {
        return 1;
    }

    @Override
    public <T> T persistOne(JdbcOperationContext ctx, T value, RuntimePersistentEntity<T> persistentEntity) {
        SqlStoredQuery<T, ?> storedQuery = resolveEntityInsert(ctx.annotationMetadata, ctx.repositoryType, (Class<T>) value.getClass(), persistentEntity);
        JdbcEntityOperations<T> persistOneOp = new JdbcEntityOperations<>(ctx, storedQuery, persistentEntity, value, true);
        persistOneOp.persist();
        return persistOneOp.getEntity();
    }

    @Override
    public <T> List<T> persistBatch(JdbcOperationContext ctx, Iterable<T> values,
                                    RuntimePersistentEntity<T> childPersistentEntity,
                                    Predicate<T> predicate) {
        SqlStoredQuery<T, T> storedQuery = resolveEntityInsert(
            ctx.annotationMetadata,
            ctx.repositoryType,
            childPersistentEntity.getIntrospection().getBeanType(),
            childPersistentEntity
        );
        JdbcEntitiesOperations<T> persistBatchOp = new JdbcEntitiesOperations<>(ctx, childPersistentEntity, values, storedQuery, true);
        persistBatchOp.veto(predicate);
        persistBatchOp.persist();
        return persistBatchOp.getEntities();
    }

    @Override
    public <T> T updateOne(JdbcOperationContext ctx, T value, RuntimePersistentEntity<T> persistentEntity) {
        SqlStoredQuery<T, T> storedQuery = resolveEntityUpdate(ctx.annotationMetadata, ctx.repositoryType, (Class<T>) value.getClass(), persistentEntity);
        JdbcEntityOperations<T> op = new JdbcEntityOperations<>(ctx, persistentEntity, value, storedQuery);
        op.update();
        return op.getEntity();
    }

    @Override
    public void persistManyAssociation(JdbcOperationContext ctx,
                                       RuntimeAssociation runtimeAssociation,
                                       Object value, RuntimePersistentEntity<Object> persistentEntity,
                                       Object child, RuntimePersistentEntity<Object> childPersistentEntity) {
        SqlStoredQuery<Object, ?> storedQuery = resolveSqlInsertAssociation(ctx.repositoryType, runtimeAssociation, persistentEntity, value);
        try {
            new JdbcEntityOperations<>(ctx, childPersistentEntity, child, storedQuery).execute();
        } catch (Exception e) {
            throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
        }
    }

    @Override
    public void persistManyAssociationBatch(JdbcOperationContext ctx,
                                            RuntimeAssociation runtimeAssociation,
                                            Object value, RuntimePersistentEntity<Object> persistentEntity,
                                            Iterable<Object> child, RuntimePersistentEntity<Object> childPersistentEntity) {
        SqlStoredQuery<Object, ?> storedQuery = resolveSqlInsertAssociation(ctx.repositoryType, runtimeAssociation, persistentEntity, value);
        try {
            JdbcEntitiesOperations<Object> assocOp = new JdbcEntitiesOperations<>(ctx, childPersistentEntity, child, storedQuery);
            assocOp.veto(ctx.persisted::contains);
            assocOp.execute();
        } catch (Exception e) {
            throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
        }
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
        return new ExecutorReactiveOperations(async(), conversionService);
    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> pq) {
        return executeRead(connection -> findOne(connection, getSqlPreparedQuery(pq)));
    }

    private <T, R> R findOne(Connection connection, SqlPreparedQuery<T, R> preparedQuery) {
        try (PreparedStatement ps = prepareStatement(connection::prepareStatement, preparedQuery, false, true)) {
            preparedQuery.bindParameters(new JdbcParameterBinder(connection, ps, preparedQuery));
            try (ResultSet rs = ps.executeQuery()) {
                SqlTypeMapper<ResultSet, R> mapper = createMapper(preparedQuery, ResultSet.class);
                R result;
                if (mapper instanceof SqlResultEntityTypeMapper<ResultSet, R> entityTypeMapper) {
                    final boolean hasJoins = !preparedQuery.getJoinPaths().isEmpty();

                    SqlResultEntityTypeMapper.PushingMapper<ResultSet, R> oneMapper = entityTypeMapper.readOneMapper();
                    if (rs.next()) {
                        oneMapper.processRow(rs);
                    }
                    while (hasJoins && rs.next()) {
                        oneMapper.processRow(rs);
                    }
                    result = oneMapper.getResult();
                } else if (rs.next()) {
                    result = mapper.map(rs, preparedQuery.getResultType());
                } else {
                    result = null;
                }
                if (result != null && preparedQuery.hasResultConsumer()) {
                    preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class)
                        .ifPresent(consumer -> consumer.accept(result, newMappingContext(rs)));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error executing SQL Query: " + e.getMessage(), e);
        }
    }

    private <T, R> List<R> findAll(Connection connection, SqlPreparedQuery<T, R> preparedQuery, boolean applyPageable) {
        try (PreparedStatement ps = prepareStatement(connection::prepareStatement, preparedQuery, !applyPageable, false)) {
            preparedQuery.bindParameters(new JdbcParameterBinder(connection, ps, preparedQuery));
            return findAll(preparedQuery, ps);
        } catch (SQLException e) {
            throw new DataAccessException("Error executing SQL Query: " + e.getMessage(), e);
        }
    }

    private <T, R> List<R> findAll(SqlStoredQuery<T, R> sqlStoredQuery, PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return findAll(sqlStoredQuery, rs);
        }
    }

    @NonNull
    private <T, R> List<R> findAll(SqlStoredQuery<T, R> sqlStoredQuery, ResultSet rs) throws SQLException {
        SqlTypeMapper<ResultSet, R> mapper = createMapper(sqlStoredQuery, ResultSet.class);
        List<R> result;
        if (mapper instanceof SqlResultEntityTypeMapper<ResultSet, R> entityTypeMapper) {
            SqlResultEntityTypeMapper.PushingMapper<ResultSet, List<R>> manyMapper = entityTypeMapper.readManyMapper();
            while (rs.next()) {
                manyMapper.processRow(rs);
            }
            result = manyMapper.getResult();
            if (result == null) {
                result = List.of();
            }
        } else {
            result = new ArrayList<>();
            while (rs.next()) {
                result.add(
                    mapper.map(rs, sqlStoredQuery.getResultType())
                );
            }
        }
        if (sqlStoredQuery.hasResultConsumer() && sqlStoredQuery instanceof PreparedQuery<?, ?> preparedQuery) {
            List<R> finalResult = result;
            preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class)
                .ifPresent(consumer -> consumer.accept(finalResult, newMappingContext(rs)));
        }
        return result;
    }

    @Override
    public <T> boolean exists(@NonNull PreparedQuery<T, Boolean> pq) {
        return executeRead(connection -> {
            try {
                SqlPreparedQuery<T, Boolean> preparedQuery = getSqlPreparedQuery(pq);
                try (PreparedStatement ps = prepareStatement(connection::prepareStatement, preparedQuery, false, true)) {
                    preparedQuery.bindParameters(new JdbcParameterBinder(connection, ps, preparedQuery));
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
        ConnectionContext connectionContext = getConnectionCtx();
        return findStream(preparedQuery, connectionContext.connection, connectionContext.needsToBeClosed);
    }

    private <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> pq, Connection connection, boolean closeConnection) {
        SqlPreparedQuery<T, R> preparedQuery = getSqlPreparedQuery(pq);
        RuntimePersistentEntity<T> persistentEntity = preparedQuery.getPersistentEntity();
        Class<R> resultType = preparedQuery.getResultType();
        AtomicBoolean finished = new AtomicBoolean();

        PreparedStatement ps;
        try {
            ps = prepareStatement(connection::prepareStatement, preparedQuery, false, false);
            preparedQuery.bindParameters(new JdbcParameterBinder(connection, ps, preparedQuery));
        } catch (Exception e) {
            throw new DataAccessException("SQL Error preparing Query: " + e.getMessage(), e);
        }

        ResultSet openedRs = null;
        ResultSet rs;
        try {
            openedRs = ps.executeQuery();
            rs = openedRs;
            SqlResultConsumer<R> sqlMappingConsumer = preparedQuery.hasResultConsumer() ? preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class).orElse(null) : null;
            SqlTypeMapper<ResultSet, R> resultMapper = createMapper(preparedQuery, ResultSet.class);
            if (resultMapper instanceof SqlResultEntityTypeMapper<ResultSet, R> entityTypeMapper) {
                Set<JoinPath> joinFetchPaths = preparedQuery.getJoinPaths();
                boolean onlySingleEndedJoins = isOnlySingleEndedJoins(persistentEntity, joinFetchPaths);
                // Cannot stream ResultSet for "many" joined query
                if (!onlySingleEndedJoins) {
                    try {
                        SqlResultEntityTypeMapper.PushingMapper<ResultSet, List<R>> manyMapper = entityTypeMapper.readManyMapper();
                        while (rs.next()) {
                            manyMapper.processRow(rs);
                        }
                        return manyMapper.getResult().stream();
                    } finally {
                        closeResultSet(connection, ps, rs, finished, closeConnection);
                    }
                }
            }

            Spliterator<R> spliterator = new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE,
                    Spliterator.ORDERED | Spliterator.IMMUTABLE) {
                @Override
                public boolean tryAdvance(Consumer<? super R> action) {
                    if (finished.get()) {
                        return false;
                    }
                    boolean hasNext = resultMapper.hasNext(rs);
                    if (hasNext) {
                        R o = resultMapper.map(rs, resultType);
                        if (sqlMappingConsumer != null) {
                            sqlMappingConsumer.accept(o, newMappingContext(rs));
                        }
                        action.accept(o);
                    } else {
                        closeResultSet(connection, ps, rs, finished, closeConnection);
                    }
                    return hasNext;
                }
            };
            return StreamSupport.stream(spliterator, false)
                .onClose(() -> closeResultSet(connection, ps, rs, finished, closeConnection));
        } catch (Exception e) {
            closeResultSet(connection, ps, openedRs, finished, closeConnection);
            throw new DataAccessException("SQL Error executing Query: " + e.getMessage(), e);
        }
    }

    private void closeResultSet(Connection connection, PreparedStatement ps, ResultSet rs, AtomicBoolean finished, boolean closeConnection) {
        if (finished.compareAndSet(false, true)) {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (ps != null) {
                    ps.close();
                }
                if (closeConnection) {
                    connection.close();
                }
            } catch (SQLException e) {
                throw new DataAccessException("Error closing JDBC result stream: " + e.getMessage(), e);
            }
        }
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return executeRead(connection -> findAll(connection, getSqlPreparedQuery(preparedQuery), true));
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> pq) {
        return executeWrite(connection -> {
            SqlPreparedQuery<?, Number> preparedQuery = getSqlPreparedQuery(pq);
            try (PreparedStatement ps = prepareStatement(connection::prepareStatement, preparedQuery, true, false)) {
                preparedQuery.bindParameters(new JdbcParameterBinder(connection, ps, preparedQuery));
                int result = ps.executeUpdate();
                for (SqlExecutionObserver observer : observers) {
                    observer.updatedRecords(result);
                }
                if (preparedQuery.isOptimisticLock()) {
                    checkOptimisticLocking(1, result);
                }
                return Optional.of(result);
            } catch (SQLException e) {
                throw sqlExceptionToDataAccessException(e, preparedQuery.getDialect(), sqlException -> new DataAccessException("Error executing SQL UPDATE: " + sqlException.getMessage(), sqlException));
            }
        });
    }

    @Override
    public <R> List<R> execute(PreparedQuery<?, R> pq) {
        return executeWrite(connection -> {
            SqlPreparedQuery<?, R> preparedQuery = getSqlPreparedQuery(pq);
            try {
                if (preparedQuery.isProcedure()) {
                    return callProcedure(connection, preparedQuery);
                } else {
                    return findAll(connection, preparedQuery, false);
                }
            } catch (SQLException e) {
                throw sqlExceptionToDataAccessException(e, preparedQuery.getDialect(), sqlException -> new DataAccessException("Error executing SQL UPDATE: " + sqlException.getMessage(), sqlException));
            }
        });
    }

    private <R> List<R> callProcedure(Connection connection, SqlPreparedQuery<?, R> preparedQuery) throws SQLException {
        try (CallableStatement callableStatement = connection.prepareCall(preparedQuery.getQuery())) {
            preparedQuery.bindParameters(new JdbcParameterBinder(connection, callableStatement, preparedQuery));
            if (!preparedQuery.getResultArgument().isVoid()) {
                DataType resultDataType = preparedQuery.getResultDataType();
                int sqlType = JdbcQueryStatement.findSqlType(resultDataType, jdbcConfiguration.getDialect());
                int outIndex = preparedQuery.getQueryBindings().size() + 1;
                callableStatement.registerOutParameter(outIndex, sqlType);
            }
            callableStatement.execute();
            if (preparedQuery.getResultArgument().isVoid()) {
                return List.of();
            }
            int outIndex = preparedQuery.getQueryBindings().size() + 1;
            List<R> result = new ArrayList<>();
            result.add(
                (R) columnIndexCallableResultReader.readDynamic(
                    callableStatement,
                    outIndex,
                    preparedQuery.getResultDataType()
                )
            );
            return result;
        }
    }

    private Integer sum(Stream<Integer> stream) {
        return stream.mapToInt(i -> i).sum();
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull DeleteBatchOperation<T> operation) {
        return Optional.ofNullable(executeWrite(connection -> {
            SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
            JdbcOperationContext ctx = createContext(operation, connection, storedQuery);
            RuntimePersistentEntity<T> persistentEntity = storedQuery.getPersistentEntity();
            if (isSupportsBatchDelete(persistentEntity, storedQuery.getDialect())) {
                JdbcEntitiesOperations<T> op = new JdbcEntitiesOperations<>(ctx, persistentEntity, operation, storedQuery);
                op.delete();
                return op.rowsUpdated;
            }
            return sum(
                operation.split().stream()
                    .map(deleteOp -> {
                        JdbcEntityOperations<T> op = new JdbcEntityOperations<>(ctx, persistentEntity, deleteOp.getEntity(), storedQuery);
                        op.delete();
                        return op.rowsUpdated;
                    })
            );
        }));
    }

    @Override
    public <T> int delete(@NonNull DeleteOperation<T> operation) {
        return executeWrite(connection -> {
            SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
            JdbcOperationContext ctx = createContext(operation, connection, storedQuery);
            JdbcEntityOperations<T> op = new JdbcEntityOperations<>(ctx, storedQuery.getPersistentEntity(), operation.getEntity(), storedQuery);
            op.delete();
            return op;
        }).rowsUpdated;
    }

    @Override
    public <E, R> R deleteReturning(DeleteReturningOperation<E, R> operation) {
        return executeWrite(connection -> {
            SqlStoredQuery<E, R> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
            JdbcOperationContext ctx = createContext(operation, connection, storedQuery);
            JdbcEntityOperations<E> op = new JdbcEntityOperations<>(ctx, storedQuery.getPersistentEntity(), operation.getEntity(), storedQuery);
            op.delete();
            return (R) op.getEntity();
        });
    }

    @Override
    public <E, R> List<R> deleteAllReturning(DeleteReturningBatchOperation<E, R> operation) {
        return executeWrite(connection -> {
            SqlStoredQuery<E, R> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
            JdbcOperationContext ctx = createContext(operation, connection, storedQuery);
            RuntimePersistentEntity<E> persistentEntity = storedQuery.getPersistentEntity();
            if (isSupportsBatchDelete(persistentEntity, storedQuery.getDialect())) {
                JdbcEntitiesOperations<E> op = new JdbcEntitiesOperations<>(ctx, persistentEntity, operation, storedQuery);
                op.delete();
                return (List<R>) op.getEntities();
            }
            return (List<R>) operation.split().stream()
                    .map(deleteOp -> {
                        JdbcEntityOperations<E> op = new JdbcEntityOperations<>(ctx, persistentEntity, deleteOp.getEntity(), storedQuery);
                        op.delete();
                        return op.getEntity();
                    }).toList();
        });
    }

    @NonNull
    @Override
    public <T> T update(@NonNull UpdateOperation<T> operation) {
        return executeWrite(connection -> {
            SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
            JdbcOperationContext ctx = createContext(operation, connection, storedQuery);
            JdbcEntityOperations<T> op = new JdbcEntityOperations<>(ctx, storedQuery.getPersistentEntity(), operation.getEntity(), storedQuery);
            op.update();
            return op.getEntity();
        });
    }

    @NonNull
    @Override
    public <T> Iterable<T> updateAll(@NonNull UpdateBatchOperation<T> operation) {
        return executeWrite(connection -> {
            final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
            final RuntimePersistentEntity<T> persistentEntity = storedQuery.getPersistentEntity();
            JdbcOperationContext ctx = createContext(operation, connection, storedQuery);
            if (!isSupportsBatchUpdate(persistentEntity, storedQuery)) {
                return operation.split()
                    .stream()
                    .map(updateOp -> {
                        JdbcEntityOperations<T> op = new JdbcEntityOperations<>(ctx, persistentEntity, updateOp.getEntity(), storedQuery);
                        op.update();
                        return op.getEntity();
                    })
                    .toList();
            }
            JdbcEntitiesOperations<T> op = new JdbcEntitiesOperations<>(ctx, persistentEntity, operation, storedQuery);
            op.update();
            return op.getEntities();
        });
    }

    @NonNull
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        return executeWrite(connection -> {
            final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
            JdbcOperationContext ctx = createContext(operation, connection, storedQuery);
            JdbcEntityOperations<T> op = new JdbcEntityOperations<>(ctx, storedQuery, storedQuery.getPersistentEntity(), operation.getEntity(), true);
            op.persist();
            return op;
        }).getEntity();
    }

    @Nullable
    @Override
    public <T> T findOne(@NonNull Class<T> type, @NonNull Object id) {
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
        throw new UnsupportedOperationException("The findPage method without an explicit query is not supported. Use findAll(PreparedQuery) instead");
    }

    @Override
    @NonNull
    public <T> Iterable<T> persistAll(@NonNull InsertBatchOperation<T> operation) {
        return executeWrite(connection -> {
            final SqlStoredQuery<T, ?> storedQuery = getSqlStoredQuery(operation.getStoredQuery());
            final RuntimePersistentEntity<T> persistentEntity = storedQuery.getPersistentEntity();
            JdbcOperationContext ctx = createContext(operation, connection, storedQuery);
            if (!isSupportsBatchInsert(persistentEntity, storedQuery)) {
                return operation.split().stream()
                    .map(persistOp -> {
                        JdbcEntityOperations<T> op = new JdbcEntityOperations<>(ctx, storedQuery, persistentEntity, persistOp.getEntity(), true);
                        op.persist();
                        return op.getEntity();
                    })
                    .toList();
            } else {
                JdbcEntitiesOperations<T> op = new JdbcEntitiesOperations<>(ctx, persistentEntity, operation, storedQuery, true);
                op.persist();
                return op.getEntities();
            }

        });
    }

    private <I> I executeRead(Function<Connection, I> fn) {
        if (!jdbcConfiguration.isAllowConnectionPerOperation() && connectionOperations.findConnectionStatus().isEmpty()) {
            throw connectionNotFoundAndNewNotAllowed();
        }
        return connectionOperations.executeRead(status -> {
            Connection connection = status.getConnection();
            applySchema(connection);
            return fn.apply(connection);
        });
    }

    private <I> I executeWrite(Function<Connection, I> fn) {
        if (!jdbcConfiguration.isAllowConnectionPerOperation() && connectionOperations.findConnectionStatus().isEmpty()) {
            throw connectionNotFoundAndNewNotAllowed();
        }
        return connectionOperations.executeWrite(status -> {
            Connection connection = status.getConnection();
            applySchema(connection);
            return fn.apply(connection);
        });
    }

    private DataAccessException connectionNotFoundAndNewNotAllowed() {
        return new DataAccessException("Connection is required for this operation. Annotate with @" + Connectable.class + ", @Transactional or enable `isAllowConnectionPerOperation`.");
    }

    @Override
    @PreDestroy
    public void close() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void applySchema(Connection connection) {
        if (schemaTenantResolver != null) {
            String schema = schemaTenantResolver.resolveTenantSchemaName();
            schemaHandler.useSchema(connection, jdbcConfiguration.getDialect(), schema);
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
        Connection connection = connectionOperations.getConnectionStatus().getConnection();
        applySchema(connection);
        return connection;
    }

    @NonNull
    private ConnectionContext getConnectionCtx() {
        boolean needsToCloseConnection;
        Connection connection;
        if (!jdbcConfiguration.isAllowConnectionPerOperation() || transactionOperations.hasConnection()) {
            connection = transactionOperations.getConnection();
            needsToCloseConnection = false;
        } else {
            connection = connectionOperations.getConnectionStatus().getConnection();
            needsToCloseConnection = true;
        }
        applySchema(connection);
        return new ConnectionContext(connection, needsToCloseConnection);
    }

    @NonNull
    @Override
    public <R> R execute(@NonNull ConnectionCallback<R> callback) {
        return executeWrite(connection -> {
            try {
                return callback.call(connection);
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL Callback: " + e.getMessage(), e);
            }
        });
    }

    @NonNull
    @Override
    public <R> R prepareStatement(@NonNull String sql, @NonNull PreparedStatementCallback<R> callback) {
        ArgumentUtils.requireNonNull("sql", sql);
        ArgumentUtils.requireNonNull("callback", callback);
        for (SqlExecutionObserver observer : observers) {
            observer.query(sql);
        }
        ConnectionContext connectionCtx = getConnectionCtx();
        try {
            R result = null;
            try {
                PreparedStatement ps = connectionCtx.connection.prepareStatement(sql);
                try {
                    result = callback.call(ps);
                    return result;
                } finally {
                    if (!(result instanceof AutoCloseable)) {
                        ps.close();
                    }
                }
            } finally {
                if (!(result instanceof AutoCloseable) && connectionCtx.needsToBeClosed) {
                    connectionCtx.connection.close();
                }
            }
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
            jsonMapper != null ? () -> jsonMapper : null,
            conversionService).map(resultSet, type);
    }

    @NonNull
    @Override
    public <E, D> D readDTO(@NonNull String prefix, @NonNull ResultSet resultSet, @NonNull Class<E> rootEntity, @NonNull Class<D> dtoType) throws DataAccessException {
        return new DTOMapper<E, ResultSet, D>(
            getEntity(rootEntity),
            columnNameResultSetReader,
            jsonMapper != null ? () -> jsonMapper : null,
            conversionService).map(resultSet, dtoType);
    }

    @NonNull
    @Override
    public <T> Stream<T> entityStream(@NonNull ResultSet resultSet, @Nullable String prefix, @NonNull Class<T> rootEntity) {
        ArgumentUtils.requireNonNull("resultSet", resultSet);
        ArgumentUtils.requireNonNull("rootEntity", rootEntity);
        TypeMapper<ResultSet, T> mapper = new SqlResultEntityTypeMapper<>(prefix, getEntity(rootEntity), columnNameResultSetReader,
            jsonMapper != null ? () -> jsonMapper : null, conversionService);
        Iterable<T> iterable = () -> new Iterator<>() {
            boolean fetched = false;
            boolean end = false;

            @Override
            public boolean hasNext() {
                if (fetched) {
                    return true;
                }
                if (end) {
                    return false;
                }
                try {
                    if (resultSet.next()) {
                        fetched = true;
                    } else {
                        end = true;
                    }
                } catch (SQLException e) {
                    throw new DataAccessException("Error retrieving next JDBC result: " + e.getMessage(), e);
                }
                return !end;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                fetched = false;
                return mapper.map(resultSet, rootEntity);
            }
        };
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Maps SQL exception, used in context of update but could be used elsewhere.
     * It will return custom {@DataAccessException} based on the {@link SQLException} or null
     * if it cannot be mapped to the custom {@link DataAccessException}.
     *
     * @param sqlException the SQL exception
     * @param dialect the SQL dialect
     * @return custom {@link DataAccessException} exception based on {@link SQLException} that was thrown or null
     * if exception is not mappable to {@link DataAccessException} in given dialect {@link SqlExceptionMapper}
     */
    @Nullable
    private DataAccessException mapSqlException(SQLException sqlException, Dialect dialect) {
        List<SqlExceptionMapper> dialectSqlExceptionMapperList = sqlExceptionMappers.getOrDefault(dialect, List.of());
        for (SqlExceptionMapper dialectSqlExceptionMapper : dialectSqlExceptionMapperList) {
            DataAccessException dataAccessException = dialectSqlExceptionMapper.mapSqlException(sqlException);
            if (dataAccessException != null) {
                return dataAccessException;
            }
        }
        return null;
    }

    @NonNull
    private ResultConsumer.Context<ResultSet> newMappingContext(ResultSet rs) {
        return new ResultConsumer.Context<>() {
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
                        jsonMapper != null ? () -> jsonMapper : null,
                        conversionService);
                return mapper.map(rs, type);
            }

            @NonNull
            @Override
            public <E, D> D readDTO(@NonNull String prefix, @NonNull Class<E> rootEntity, @NonNull Class<D> dtoType) throws DataAccessException {
                RuntimePersistentEntity<E> entity = getEntity(rootEntity);
                TypeMapper<ResultSet, D> introspectedDataMapper = new DTOMapper<>(
                        entity,
                        columnNameResultSetReader,
                        jsonMapper != null ? () -> jsonMapper : null,
                        conversionService);
                return introspectedDataMapper.map(rs, dtoType);
            }
        };
    }

    private <T> JdbcOperationContext createContext(EntityOperation<T> operation, Connection connection, SqlStoredQuery<T, ?> storedQuery) {
        return new JdbcOperationContext(operation.getAnnotationMetadata(), operation.getInvocationContext(), operation.getRepositoryType(), storedQuery.getDialect(), connection);
    }

    /**
     * Gets the generated id on record insert.
     *
     * @param generatedKeysResultSet the generated keys result set
     * @param identity               the identity persistent field
     * @param dialect                the SQL dialect
     * @return the generated id
     */
    private Object getGeneratedIdentity(@NonNull ResultSet generatedKeysResultSet, RuntimePersistentProperty<?> identity, Dialect dialect) {
        if (dialect == Dialect.POSTGRES) {
            // Postgres returns all fields, not just id, so we need to access generated id by the name
            return columnNameResultSetReader.readDynamic(generatedKeysResultSet, identity.getPersistedName(), identity.getDataType());
        }
        return columnIndexResultSetReader.readDynamic(generatedKeysResultSet, 1, identity.getDataType());
    }

    /**
     * Handles {@link SQLException} first trying to map it to {@link DataAccessException} using {@link SqlExceptionMapper}.
     * If mapped exception is not {@link DataAccessException} then returns {@link DataAccessException} using provided fallbackMapper.
     *
     * @param sqlException      The SQL exception that was thrown
     * @param dialect           The dialect
     * @param fallbackMapper    The fallback mapper that returns {@link DataAccessException} if {@link SQLException} was not mapped to {@link DataAccessException}
     * @return DataAccessException
     */
    private DataAccessException sqlExceptionToDataAccessException(SQLException sqlException, Dialect dialect, Function<SQLException, DataAccessException> fallbackMapper) {
        DataAccessException dataAccessException = mapSqlException(sqlException, dialect);
        if (dataAccessException != null) {
            return dataAccessException;
        }
        return fallbackMapper.apply(sqlException);
    }

    @Override
    public boolean isSupportsBatchInsert(JdbcOperationContext jdbcOperationContext, RuntimePersistentEntity<?> persistentEntity) {
        return isSupportsBatchInsert(persistentEntity, jdbcOperationContext.dialect);
    }

    private final class JdbcParameterBinder implements BindableParametersStoredQuery.Binder {

        private final SqlStoredQuery<?, ?> sqlStoredQuery;
        private final Connection connection;
        private final PreparedStatement ps;
        private int index = 1;

        public JdbcParameterBinder(Connection connection, PreparedStatement ps, SqlStoredQuery<?, ?> sqlStoredQuery) {
            this.connection = connection;
            this.ps = ps;
            this.sqlStoredQuery = sqlStoredQuery;
        }

        @Override
        public Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
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

        private ConversionContext createTypeConversionContext(RuntimePersistentProperty<?> property,
                                                              Argument<?> argument) {
            Objects.requireNonNull(connection);
            if (property != null) {
                return new RuntimePersistentPropertyJdbcCC(connection, property);
            }
            if (argument != null) {
                return new ArgumentJdbcCC(connection, argument);
            }
            return new JdbcConversionContextImpl(connection);
        }

        @Override
        public void bindOne(QueryParameterBinding binding, Object value) {
            JsonDataType jsonDataType = null;
            if (binding.getDataType() == DataType.JSON) {
                jsonDataType = binding.getJsonDataType();
            }
            setStatementParameter(ps, index, binding.getDataType(), jsonDataType, value, sqlStoredQuery);
            index++;
        }

        @Override
        public void bindMany(QueryParameterBinding binding, Collection<Object> values) {
            for (Object value : values) {
                bindOne(binding, value);
            }
        }

        @Override
        public int currentIndex() {
            return index;
        }

    }

    private final class JdbcEntityOperations<T> extends AbstractSyncEntityOperations<JdbcOperationContext, T, SQLException> {

        private final SqlStoredQuery<T, ?> storedQuery;
        private Integer rowsUpdated;
        private Map<QueryParameterBinding, Object> previousValues;

        private JdbcEntityOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, T entity, SqlStoredQuery<T, ?> storedQuery) {
            this(ctx, storedQuery, persistentEntity, entity, false);
        }

        private JdbcEntityOperations(JdbcOperationContext ctx, SqlStoredQuery<T, ?> storedQuery, RuntimePersistentEntity<T> persistentEntity, T entity, boolean insert) {
            super(ctx,
                DefaultJdbcRepositoryOperations.this.cascadeOperations,
                entityEventRegistry, persistentEntity,
                DefaultJdbcRepositoryOperations.this.conversionService, entity, insert);
            this.storedQuery = storedQuery;
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
            previousValues = storedQuery.collectAutoPopulatedPreviousValues(entity);
        }

        private PreparedStatement prepare(Connection connection, SqlStoredQuery<T, ?> storedQuery) throws SQLException {
            if (storedQuery instanceof SqlPreparedQuery<T, ?> sqlPreparedQuery) {
                sqlPreparedQuery.prepare(entity);
            }
            if (insert) {
                Dialect dialect = storedQuery.getDialect();
                if (hasGeneratedId && (dialect == Dialect.ORACLE || dialect == Dialect.SQL_SERVER)) {
                    if (isJsonEntityGeneratedId(storedQuery, persistentEntity)) {
                        // This is being closed in try with resources from where it is being called
                        @SuppressWarnings({"java:S2095"})
                        CallableStatement callableStatement = connection.prepareCall(this.storedQuery.getQuery());
                        // Auto generated id by the database will be only numeric in this case
                        callableStatement.registerOutParameter(storedQuery.getQueryBindings().size() + 1, Types.NUMERIC);
                        return callableStatement;
                    }
                    return connection.prepareStatement(this.storedQuery.getQuery(), new String[]{persistentEntity.getIdentity().getPersistedName()});
                } else {
                    return connection.prepareStatement(this.storedQuery.getQuery(), hasGeneratedId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
                }
            } else {
                return connection.prepareStatement(this.storedQuery.getQuery());
            }
        }

        @Override
        protected void execute() throws SQLException {
            for (SqlExecutionObserver observer : observers) {
                observer.query(storedQuery.getQuery());
            }
            try {
                if (storedQuery.getOperationType() == StoredQuery.OperationType.INSERT_RETURNING
                    || storedQuery.getOperationType() == StoredQuery.OperationType.UPDATE_RETURNING
                    || storedQuery.getOperationType() == StoredQuery.OperationType.DELETE_RETURNING) {
                    executeReturning();
                } else {
                    executeUpdate();
                }
                if (storedQuery.isOptimisticLock()) {
                    checkOptimisticLocking(1, rowsUpdated);
                }
            } catch (SQLException e) {
                DataAccessException dataAccessException = mapSqlException(e, ctx.dialect);
                if (dataAccessException != null) {
                    throw dataAccessException;
                }
                throw e;
            }
        }

        private void executeReturning() {
            try (PreparedStatement ps = ctx.connection.prepareStatement(storedQuery.getQuery())) {
                storedQuery.bindParameters(new JdbcParameterBinder(ctx.connection, ps, storedQuery), ctx.invocationContext, entity, previousValues);
                List<T> result = (List<T>) findAll(storedQuery, ps);
                if (result.isEmpty()) {
                    throw new DataAccessException("Returning clause produced no results");
                }
                rowsUpdated = result.size();
                entity = result.iterator().next();
            } catch (SQLException e) {
                throw new DataAccessException("Error executing SQL Query: " + e.getMessage(), e);
            }
        }

        private void executeUpdate() throws SQLException {
            try (PreparedStatement ps = prepare(ctx.connection, storedQuery)) {
                storedQuery.bindParameters(new JdbcParameterBinder(ctx.connection, ps, storedQuery), ctx.invocationContext, entity, previousValues);
                rowsUpdated = ps.executeUpdate();
                if (hasGeneratedId) {
                    if (isJsonEntityGeneratedId(storedQuery, persistentEntity) && ps instanceof CallableStatement callableStatement) {
                        Object id = callableStatement.getObject(storedQuery.getQueryBindings().size() + 1);
                        BeanProperty<T, Object> property = persistentEntity.getIdentity().getProperty();
                        entity = updateEntityId(property, entity, id);
                    } else {
                        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                                Object id = getGeneratedIdentity(generatedKeys, identity, storedQuery.getDialect());
                                BeanProperty<T, Object> property = identity.getProperty();
                                entity = updateEntityId(property, entity, id);
                            } else {
                                throw new DataAccessException("Failed to generate ID for entity: " + entity);
                            }
                        }
                    }
                }
            }
        }
    }

    private final class JdbcEntitiesOperations<T> extends AbstractSyncEntitiesOperations<JdbcOperationContext, T, SQLException> {

        private final SqlStoredQuery<T, ?> storedQuery;
        private int rowsUpdated;

        private JdbcEntitiesOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, SqlStoredQuery<T, ?> storedQuery) {
            this(ctx, persistentEntity, entities, storedQuery, false);
        }

        private JdbcEntitiesOperations(JdbcOperationContext ctx, RuntimePersistentEntity<T> persistentEntity, Iterable<T> entities, SqlStoredQuery<T, ?> storedQuery, boolean insert) {
            super(ctx,
                DefaultJdbcRepositoryOperations.this.cascadeOperations,
                DefaultJdbcRepositoryOperations.this.conversionService,
                entityEventRegistry, persistentEntity, entities, insert);
            this.storedQuery = storedQuery;
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
            for (Data d : entities) {
                if (d.vetoed) {
                    continue;
                }
                d.previousValues = storedQuery.collectAutoPopulatedPreviousValues(d.entity);
            }
        }

        private PreparedStatement prepare(Connection connection) throws SQLException {
            if (insert) {
                Dialect dialect = storedQuery.getDialect();
                if (hasGeneratedId && (dialect == Dialect.ORACLE || dialect == Dialect.SQL_SERVER)) {
                    if (isJsonEntityGeneratedId(storedQuery, persistentEntity)) {
                        // This is being closed in try with resources from where it is being called
                        @SuppressWarnings({"java:S2095"})
                        CallableStatement callableStatement = connection.prepareCall(this.storedQuery.getQuery());
                        // expected auto generated value in insert will be numeric
                        callableStatement.registerOutParameter(storedQuery.getQueryBindings().size() + 1, Types.NUMERIC);
                        return callableStatement;
                    }
                    return connection.prepareStatement(storedQuery.getQuery(), new String[]{persistentEntity.getIdentity().getPersistedName()});
                } else {
                    return connection.prepareStatement(storedQuery.getQuery(), hasGeneratedId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
                }
            } else {
                return connection.prepareStatement(storedQuery.getQuery());
            }
        }

        private void setParameters(PreparedStatement stmt, SqlStoredQuery<T, ?> storedQuery) throws SQLException {
            for (Data d : entities) {
                if (d.vetoed) {
                    continue;
                }
                storedQuery.bindParameters(new JdbcParameterBinder(ctx.connection, stmt, storedQuery), ctx.invocationContext, d.entity, d.previousValues);
                stmt.addBatch();
            }
        }

        @Override
        protected void execute() {
            for (SqlExecutionObserver observer : observers) {
                observer.query(storedQuery.getQuery());
            }
            if (storedQuery.getOperationType() == StoredQuery.OperationType.INSERT_RETURNING
                || storedQuery.getOperationType() == StoredQuery.OperationType.UPDATE_RETURNING) {
                throw new IllegalStateException("Batch operations don't support returning operations");
            }
            try (PreparedStatement ps = prepare(ctx.connection)) {
                setParameters(ps, storedQuery);
                rowsUpdated = Arrays.stream(ps.executeBatch()).sum();
                if (hasGeneratedId) {
                    RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                    List<Object> ids = new ArrayList<>();
                    try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                        Dialect dialect = storedQuery.getDialect();
                        while (generatedKeys.next()) {
                            ids.add(getGeneratedIdentity(generatedKeys, identity, dialect));
                        }
                    }
                    Iterator<Object> iterator = ids.iterator();
                    for (Data d : entities) {
                        if (d.vetoed) {
                            continue;
                        }
                        if (!iterator.hasNext()) {
                            throw new DataAccessException("Failed to generate ID for entity: " + d.entity);
                        } else {
                            Object id = iterator.next();
                            d.entity = updateEntityId(identity.getProperty(), d.entity, id);
                        }
                    }
                }
                if (storedQuery.isOptimisticLock()) {
                    int expected = (int) entities.stream().filter(d -> !d.vetoed).count();
                    checkOptimisticLocking(expected, rowsUpdated);
                }
            } catch (SQLException e) {
                throw sqlExceptionToDataAccessException(e, ctx.dialect, sqlException -> new DataAccessException("Error executing batch SQL UPDATE: " + sqlException.getMessage(), sqlException));
            }
        }

    }

    @SuppressWarnings("VisibilityModifier")
    protected static class JdbcOperationContext extends OperationContext {

        public final Connection connection;
        public final Dialect dialect;
        private final InvocationContext<?, ?> invocationContext;

        /**
         * The default constructor.
         *
         * @param annotationMetadata the annotation metadata
         * @param invocationContext  the invocation context
         * @param repositoryType     the repository type
         * @param dialect            the dialect
         * @param connection         the connection
         */
        public JdbcOperationContext(AnnotationMetadata annotationMetadata, InvocationContext<?, ?> invocationContext, Class<?> repositoryType, Dialect dialect, Connection connection) {
            super(annotationMetadata, repositoryType);
            this.dialect = dialect;
            this.connection = connection;
            this.invocationContext = invocationContext;
        }
    }

    private static final class RuntimePersistentPropertyJdbcCC extends JdbcConversionContextImpl implements RuntimePersistentPropertyConversionContext {

        private final RuntimePersistentProperty<?> property;

        public RuntimePersistentPropertyJdbcCC(Connection connection, RuntimePersistentProperty<?> property) {
            super(ConversionContext.of(property.getArgument()), connection);
            this.property = property;
        }

        @Override
        public RuntimePersistentProperty<?> getRuntimePersistentProperty() {
            return property;
        }
    }

    private static final class ArgumentJdbcCC extends JdbcConversionContextImpl implements ArgumentConversionContext<Object> {

        private final Argument argument;

        public ArgumentJdbcCC(Connection connection, Argument argument) {
            super(ConversionContext.of(argument), connection);
            this.argument = argument;
        }

        @Override
        public Argument<Object> getArgument() {
            return argument;
        }
    }

    private static class JdbcConversionContextImpl extends AbstractConversionContext
        implements JdbcConversionContext {

        private final Connection connection;

        public JdbcConversionContextImpl(Connection connection) {
            this(ConversionContext.DEFAULT, connection);
        }

        public JdbcConversionContextImpl(ConversionContext conversionContext, Connection connection) {
            super(conversionContext);
            this.connection = connection;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

    }

    private static final class ConnectionContext {

        private final Connection connection;
        private final boolean needsToBeClosed;

        private ConnectionContext(Connection connection, boolean needsToBeClosed) {
            this.connection = connection;
            this.needsToBeClosed = needsToBeClosed;
        }

        public Connection getConnection() {
            return connection;
        }
    }

}
