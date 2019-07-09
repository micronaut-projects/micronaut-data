package io.micronaut.data.jdbc.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.mapper.ColumnIndexResultSetReader;
import io.micronaut.data.jdbc.mapper.ColumnNameResultSetReader;
import io.micronaut.data.jdbc.mapper.JdbcQueryStatement;
import io.micronaut.data.jdbc.mapper.SqlResultConsumer;
import io.micronaut.data.jdbc.runtime.ConnectionCallback;
import io.micronaut.data.jdbc.runtime.PreparedStatementCallback;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.runtime.config.PredatorSettings;
import io.micronaut.data.runtime.mapper.DTOMapper;
import io.micronaut.data.runtime.mapper.ResultConsumer;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.TypeMapper;
import io.micronaut.data.runtime.mapper.sql.SqlResultEntityTypeMapper;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.data.transaction.TransactionOperations;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.sql.*;
import java.util.Date;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
public class DefaultJdbcRepositoryOperations implements
        JdbcRepositoryOperations,
        AsyncCapableRepository,
        ReactiveCapableRepository,
        AutoCloseable {

    private static final Pattern IN_EXPRESSION_PATTERN = Pattern.compile("\\s\\?\\$IN\\((\\d+)\\)");
    private static final String NOT_TRUE_EXPRESSION = "1 = 2";
    private static final SqlQueryBuilder DEFAULT_SQL_BUILDER = new SqlQueryBuilder();

    private final Map<Class, StoredInsert> storedInserts = new ConcurrentHashMap<>(10);
    private final Map<Class, RuntimePersistentEntity> entities = new ConcurrentHashMap<>(10);
    private final Map<Class, RuntimePersistentProperty> idReaders = new ConcurrentHashMap<>(10);
    private final Map<Class, Dialect> dialects = new HashMap<>(10);
    private final Map<Dialect, SqlQueryBuilder> queryBuilders = new HashMap<>(Dialect.values().length);
    private final JdbcQueryStatement preparedStatementWriter = new JdbcQueryStatement();
    private final ColumnNameResultSetReader columnNameResultSetReader = new ColumnNameResultSetReader();
    private final ColumnIndexResultSetReader columnIndexResultSetReader = new ColumnIndexResultSetReader();
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
     */
    protected DefaultJdbcRepositoryOperations(@Parameter String dataSourceName,
                                              DataSource dataSource,
                                              @Parameter TransactionOperations<Connection> transactionOperations,
                                              @Named("io") @Nullable ExecutorService executorService,
                                              BeanContext beanContext) {
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
            Connection connection = status.getResource();
            try {
                PreparedStatement ps = prepareStatement(connection, preparedQuery, false, true);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Class<T> rootEntity = preparedQuery.getRootEntity();
                    Class<R> resultType = preparedQuery.getResultType();
                    if (resultType == rootEntity) {
                        @SuppressWarnings("unchecked")
                        RuntimePersistentEntity<R> persistentEntity = getEntity((Class<R>) rootEntity);
                        TypeMapper<ResultSet, R> mapper = new SqlResultEntityTypeMapper<>(
                                persistentEntity,
                                columnNameResultSetReader,
                                preparedQuery.getJoinFetchPaths()
                        );
                        R result = mapper.map(rs, resultType);
                        preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class)
                                .ifPresent(consumer -> consumer.accept(result, newMappingContext(rs)));
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
                        columnNameResultSetReader
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
                Connection connection = status.getResource();
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
        return StreamSupport.stream(findIterable(preparedQuery, false).spliterator(), false);
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return CollectionUtils.iterableToList(
                findIterable(preparedQuery, true)
        );
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        //noinspection ConstantConditions
        return transactionOperations.executeWrite(status -> {
            try {
                Connection connection = status.getResource();
                PreparedStatement ps = prepareStatement(connection, preparedQuery, true, false);
                return Optional.of(ps.executeUpdate());
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
                Connection connection = status.getResource();
                T entity = operation.getEntity();
                boolean generateId = insert.isGenerateId();
                String insertSql = insert.getSql();
                if (PredatorSettings.QUERY_LOG.isDebugEnabled()) {
                    PredatorSettings.QUERY_LOG.debug("Executing SQL Insert: {}", insertSql);
                }
                PreparedStatement stmt = connection
                        .prepareStatement(insertSql, generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
                setInsertParameters(insert, entity, stmt);
                int i = stmt.executeUpdate();
                BeanProperty<T, Object> identity = insert.getIdentity();
                if (generateId && identity != null) {
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
                throw new DataAccessException("SQL Error: " + e.getMessage(), e);
            }
        });

    }

    private <T> void setInsertParameters(StoredInsert<T> insert, T entity, PreparedStatement stmt) {
        Date now = null;
        for (Map.Entry<RuntimePersistentProperty<T>, Integer> entry : insert.getParameterBinding().entrySet()) {
            RuntimePersistentProperty<T> prop = entry.getKey();
            DataType type = prop.getDataType();
            BeanProperty<T, Object> beanProperty = (BeanProperty<T, Object>) prop.getProperty();
            Object value = beanProperty.get(entity);
            int index = entry.getValue();
            if (prop instanceof Association) {
                Association association = (Association) prop;
                if (!association.isForeignKey()) {
                    if (value != null) {
                        @SuppressWarnings("unchecked")
                        RuntimePersistentEntity<Object> associatedEntity = getEntity((Class<Object>) value.getClass());
                        RuntimePersistentProperty<Object> identity = associatedEntity.getIdentity();
                        if (identity == null) {
                            throw new IllegalArgumentException("Associated entity has not ID: " + associatedEntity.getName());
                        }
                        value = identity.getProperty().get(value);
                    }
                    if (PredatorSettings.QUERY_LOG.isTraceEnabled()) {
                        PredatorSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", value, index);
                    }

                    preparedStatementWriter.setDynamic(
                            stmt,
                            index,
                            type,
                            value
                    );
                }

            } else if (!prop.isGenerated()) {
                if (beanProperty.hasStereotype(AutoPopulated.class)) {
                    if (beanProperty.hasAnnotation(DateCreated.class)) {
                        now = now != null ? now : new Date();
                        if (PredatorSettings.QUERY_LOG.isTraceEnabled()) {
                            PredatorSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", now, index);
                        }
                        preparedStatementWriter.setDynamic(
                                stmt,
                                index,
                                type,
                                now
                        );
                        beanProperty.convertAndSet(entity, now);
                    } else if (beanProperty.hasAnnotation(DateUpdated.class)) {
                        now = now != null ? now : new Date();
                        if (PredatorSettings.QUERY_LOG.isTraceEnabled()) {
                            PredatorSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", now, index);
                        }
                        preparedStatementWriter.setDynamic(
                                stmt,
                                index,
                                type,
                                now
                        );
                        beanProperty.convertAndSet(entity, now);
                    } else if (UUID.class.isAssignableFrom(beanProperty.getType())) {
                        UUID uuid = UUID.randomUUID();
                        if (PredatorSettings.QUERY_LOG.isTraceEnabled()) {
                            PredatorSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", uuid, index);
                        }
                        preparedStatementWriter.setDynamic(
                                stmt,
                                index,
                                type,
                                uuid
                        );
                        beanProperty.set(entity, uuid);
                    } else {
                        throw new DataAccessException("Unsupported auto-populated annotation type: " + beanProperty.getAnnotationTypeByStereotype(AutoPopulated.class).orElse(null));
                    }
                } else {
                    if (PredatorSettings.QUERY_LOG.isTraceEnabled()) {
                        PredatorSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", value, index);
                    }
                    preparedStatementWriter.setDynamic(
                            stmt,
                            index,
                            type,
                            value
                    );
                }
            }
        }
    }

    @NonNull
    private <T> StoredInsert resolveInsert(@NonNull EntityOperation<T> operation) {
        return storedInserts.computeIfAbsent(operation.getRootEntity(), aClass -> {
            AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
            String insertStatement = annotationMetadata.stringValue(
                    PredatorMethod.class,
                    PredatorMethod.META_MEMBER_INSERT_STMT
            ).orElse(null);
            if (insertStatement == null) {
                throw new IllegalStateException("No insert statement present in repository. Ensure it extends GenericRepository and is annotated with @JdbcRepository");
            }

            RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
            Map<RuntimePersistentProperty<T>, Integer> parameterBinding = buildSqlParameterBinding(annotationMetadata, persistentEntity);
            // MSSQL doesn't support RETURN_GENERATED_KEYS https://github.com/Microsoft/mssql-jdbc/issues/245 with BATCHi
            boolean supportsBatch = annotationMetadata.findAnnotation(Repository.class)
                    .flatMap(av -> av.enumValue("dialect", Dialect.class)
                            .map(dialect -> dialect != Dialect.SQL_SERVER)).orElse(true);
            return new StoredInsert<>(insertStatement, persistentEntity, parameterBinding, supportsBatch);
        });
    }

    private <T, R> Iterable<R> findIterable(@NonNull PreparedQuery<T, R> preparedQuery, boolean consume) {
        Class<T> rootEntity = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();


        return transactionOperations.executeRead(status -> {
            Connection connection = status.getResource();

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
                throw new DataAccessException("SQL Error executing Query: " + e.getMessage(), e);
            }
            boolean dtoProjection = preparedQuery.isDtoProjection();
            boolean isRootResult = resultType == rootEntity;
            Iterable<R> iterable;
            if (isRootResult || dtoProjection) {
                SqlResultConsumer sqlMappingConsumer = preparedQuery.getParameterInRole(SqlResultConsumer.ROLE, SqlResultConsumer.class).orElse(null);
                TypeMapper<ResultSet, R> mapper;
                if (dtoProjection) {
                    mapper = new DTOMapper<>(
                            getEntity(rootEntity),
                            columnNameResultSetReader
                    );
                } else {
                    mapper = new SqlResultEntityTypeMapper<>(
                            getEntity(resultType),
                            columnNameResultSetReader,
                            preparedQuery.getJoinFetchPaths()
                    );
                }
                iterable = () -> new Iterator<R>() {
                    boolean nextCalled = false;

                    @Override
                    public boolean hasNext() {
                        try {
                            if (!nextCalled) {
                                nextCalled = true;
                                return rs.next();
                            } else {
                                return nextCalled;
                            }
                        } catch (SQLException e) {
                            throw new DataAccessException("Error retrieving next JDBC result: " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public R next() {
                        nextCalled = false;
                        R o = mapper.map(rs, resultType);
                        if (sqlMappingConsumer != null) {
                            sqlMappingConsumer.accept(rs, o);
                        }
                        return o;
                    }
                };
            } else {
                iterable = () -> new Iterator<R>() {
                    boolean nextCalled = false;

                    @Override
                    public boolean hasNext() {
                        try {
                            if (!nextCalled) {
                                nextCalled = true;
                                return rs.next();
                            } else {
                                return nextCalled;
                            }
                        } catch (SQLException e) {
                            return false;
                        }
                    }

                    @Override
                    public R next() {
                        nextCalled = false;
                        Object v = columnIndexResultSetReader.readDynamic(rs, 1, preparedQuery.getResultDataType());
                        if (resultType.isInstance(v)) {
                            //noinspection unchecked
                            return (R) v;
                        } else {
                            return columnIndexResultSetReader.convertRequired(v, resultType);
                        }
                    }
                };
            }
            return consume ? CollectionUtils.iterableToList(iterable) : iterable;
        });
    }

    private <T, R> PreparedStatement prepareStatement(
            Connection connection,
            @NonNull PreparedQuery<T, R> preparedQuery,
            boolean isUpdate,
            boolean isSingleResult) throws SQLException {
        String query = preparedQuery.getQuery();

        final boolean hasIn = preparedQuery.hasInExpression();
        Map<Integer, Object> parameterValues = preparedQuery.getIndexedParameterValues();
        DataType[] parameterTypes = preparedQuery.getIndexedParameterTypes();
        if (hasIn) {
            parameterValues = new HashMap<>(parameterValues); // create a copy
            query = expandInExpressions(query, parameterValues);
        }

        if (!isUpdate) {
            Pageable pageable = preparedQuery.getPageable();
            Class<T> rootEntity = preparedQuery.getRootEntity();
            if (pageable != Pageable.UNPAGED) {
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
            } else if (isSingleResult && !preparedQuery.isCount()) {
                Dialect dialect = dialects.getOrDefault(preparedQuery.getRepositoryType(), Dialect.ANSI);
                boolean isSqlServer = isSqlServerWithoutOrderBy(query, dialect);
                if (!isSqlServer || rootEntity == preparedQuery.getResultType()) {

                    QueryBuilder queryBuilder = queryBuilders.getOrDefault(dialect, DEFAULT_SQL_BUILDER);
                    if (isSqlServer) {
                        RuntimePersistentEntity<T> persistentEntity = getEntity(rootEntity);
                        Sort sort = sortById(persistentEntity);
                        query += queryBuilder.buildOrderBy(persistentEntity, sort).getQuery();
                    }
                    query += queryBuilder.buildPagination(Pageable.from(0, 1)).getQuery();
                }
            }
        }

        if (PredatorSettings.QUERY_LOG.isDebugEnabled()) {
            PredatorSettings.QUERY_LOG.debug("Executing Query: {}", query);
        }
        final PreparedStatement ps = connection.prepareStatement(query);
        int paramTypeLen = parameterTypes.length;
        for (Map.Entry<Integer, Object> entry : parameterValues.entrySet()) {
            int index = entry.getKey();
            Object value = entry.getValue();
            if (PredatorSettings.QUERY_LOG.isTraceEnabled()) {
                PredatorSettings.QUERY_LOG.trace("Binding parameter at position {} to value {}", index, value);
            }
            DataType dataType = paramTypeLen >= index ? parameterTypes[index - 1] : null;
            if (dataType == null) {
                dataType = DataType.OBJECT;
            }
            if (value == null) {
                preparedStatementWriter.setDynamic(
                        ps,
                        index,
                        dataType,
                        null);
            } else {
                if (value instanceof Iterable) {
                    Iterable i = (Iterable) value;
                    for (Object o : i) {
                        setStatementParameter(ps, index, dataType, o);
                        index++;
                    }
                } else if (value.getClass().isArray()) {
                    int len = Array.getLength(value);
                    for (int i = 0; i < len; i++) {
                        Object o = Array.get(value, i);
                        setStatementParameter(ps, index, dataType, o);
                        index++;
                    }
                } else {
                    setStatementParameter(ps, index, dataType, value);
                }
            }
        }
        return ps;
    }

    @NonNull
    private <T> Sort sortById(RuntimePersistentEntity<T> persistentEntity) {
        Sort sort;
        RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
        if (identity == null) {
            throw new DataAccessException("Pagination requires an entity ID on SQL Server");
        }
        sort = Sort.unsorted().order(Sort.Order.asc(identity.getName()));
        return sort;
    }

    private boolean isSqlServerWithoutOrderBy(String query, Dialect dialect) {
        return dialect == Dialect.SQL_SERVER && !query.contains(AbstractSqlLikeQueryBuilder.ORDER_BY_CLAUSE);
    }

    private void setStatementParameter(PreparedStatement ps, int index, DataType dataType, Object o) {
        if (o != null) {
            if (dataType == DataType.ENTITY) {
                RuntimePersistentProperty<Object> idReader = getIdReader(o);
                Object id = idReader.getProperty().get(o);
                if (id == null) {
                    throw new DataAccessException("Supplied entity is a transient instance: " + o);
                }
                o = id;
                preparedStatementWriter.setDynamic(
                        ps,
                        index,
                        idReader.getDataType(),
                        o);
            } else {

                preparedStatementWriter.setDynamic(
                        ps,
                        index,
                        dataType,
                        o);
            }
        }
    }

    private @NonNull
    RuntimePersistentProperty<Object> getIdReader(Object o) {
        Class<Object> type = (Class<Object>) o.getClass();
        RuntimePersistentProperty beanProperty = idReaders.get(type);
        if (beanProperty == null) {

            RuntimePersistentEntity<Object> entity = getEntity(type);
            RuntimePersistentProperty<Object> identity = entity.getIdentity();
            if (identity == null) {
                throw new DataAccessException("Entity has no ID: " + entity.getName());
            }
            beanProperty = identity;
            idReaders.put(type, beanProperty);
        }
        return beanProperty;
    }

    private String expandInExpressions(String query, Map<Integer, Object> parameterValues) {
        Set<Integer> indexes = parameterValues.keySet();
        Matcher matcher = IN_EXPRESSION_PATTERN.matcher(query);
        while (matcher.find()) {
            int inIndex = Integer.valueOf(matcher.group(1));
            Object value = parameterValues.get(inIndex);
            if (value == null) {
                query = matcher.replaceFirst(NOT_TRUE_EXPRESSION);
                parameterValues.remove(inIndex);
            } else {
                int size = sizeOf(value);
                if (size == 0) {
                    parameterValues.remove(inIndex);
                    query = matcher.replaceFirst(NOT_TRUE_EXPRESSION);
                } else {
                    String replacement = " IN(" + String.join(",", Collections.nCopies(size, "?")) + ")";
                    query = matcher.replaceFirst(replacement);
                    for (Integer index : indexes) {
                        if (index > inIndex) {
                            Object v = parameterValues.remove(index);
                            parameterValues.put(index + size, v);
                        }
                    }
                }
            }

            matcher = IN_EXPRESSION_PATTERN.matcher(query);

        }
        return query;
    }

    private int sizeOf(Object value) {
        if (value instanceof Collection) {
            return ((Collection) value).size();
        } else if (value instanceof Iterable) {
            int i = 0;
            for (Object ignored : ((Iterable) value)) {
                i++;
            }
            return i;
        } else if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return 1;
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
    public <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        RuntimePersistentEntity<T> entity = entities.get(type);
        if (entity == null) {
            entity = new RuntimePersistentEntity<T>(type) {
                @Override
                protected RuntimePersistentEntity<T> getEntity(Class<T> type) {
                    return DefaultJdbcRepositoryOperations.this.getEntity(type);
                }
            };
            entities.put(type, entity);
        }
        return entity;
    }

    private <T> Map<RuntimePersistentProperty<T>, Integer> buildSqlParameterBinding(AnnotationMetadata annotationMetadata, RuntimePersistentEntity<T> entity) {
        AnnotationValue<PredatorMethod> annotation = annotationMetadata.getAnnotation(PredatorMethod.class);
        if (annotation == null) {
            return Collections.emptyMap();
        }
        List<AnnotationValue<Property>> parameterData = annotation.getAnnotations(PredatorMethod.META_MEMBER_INSERT_BINDING,
                Property.class);
        Map<String, String> parameterValues;
        if (CollectionUtils.isNotEmpty(parameterData)) {
            parameterValues = new HashMap<>(parameterData.size());
            for (AnnotationValue<Property> annotationValue : parameterData) {
                String name = annotationValue.stringValue("name").orElse(null);
                String argument = annotationValue.stringValue("value").orElse(null);
                if (name != null && argument != null) {
                    parameterValues.put(name, argument);
                }
            }
        } else {
            parameterValues = Collections.emptyMap();
        }
        return parameterValues
                .entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> {
                            String name = entry.getKey();
                            RuntimePersistentProperty<T> prop = entity.getPropertyByName(name);
                            if (prop == null) {
                                throw new IllegalStateException("No property [" + name + "] found on entity: " + entity.getName());
                            }
                            return prop;
                        },
                        entry -> Integer.valueOf(entry.getValue())
                ));
    }

    @NonNull
    @Override
    public <T> Iterable<T> persistAll(@NonNull BatchOperation<T> operation) {
        @SuppressWarnings("unchecked") StoredInsert<T> insert = resolveInsert(operation);
        if (!insert.supportsBatch) {
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
                Connection connection = status.getResource();
                List<T> results = new ArrayList<>();
                boolean generateId = insert.isGenerateId();
                String insertSql = insert.getSql();

                try {
                    PreparedStatement stmt = connection
                            .prepareStatement(insertSql, generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
                    if (PredatorSettings.QUERY_LOG.isDebugEnabled()) {
                        PredatorSettings.QUERY_LOG.debug("Executing Batch SQL Insert: {}", insertSql);
                    }
                    for (T entity : operation) {
                        setInsertParameters(insert, entity, stmt);
                        stmt.addBatch();
                        results.add(entity);
                    }
                    stmt.executeBatch();
                    BeanProperty<T, Object> identity = insert.getIdentity();
                    if (generateId && identity != null) {
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
    public <T> Stream<T> entityStream(@NonNull ResultSet resultSet, @Nullable String prefix, @NonNull Class<T> rootEntity) {
        ArgumentUtils.requireNonNull("resultSet", resultSet);
        ArgumentUtils.requireNonNull("rootEntity", rootEntity);
        TypeMapper<ResultSet, T> mapper = new SqlResultEntityTypeMapper<>(prefix, getEntity(rootEntity), columnNameResultSetReader);
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

    /**
     * A stored insert statement.
     *
     * @param <T> The entity type
     */
    protected final class StoredInsert<T> {
        private final Map<RuntimePersistentProperty<T>, Integer> parameterBinding;
        private final RuntimePersistentProperty identity;
        private final boolean generateId;
        private final String sql;
        private final boolean supportsBatch;

        /**
         * Default constructor.
         *
         * @param sql              The SQL INSERT
         * @param persistentEntity The entity
         * @param parameterBinding The parameter binding
         * @param supportsBatch    Whether batch insert is supported
         */
        StoredInsert(
                String sql,
                RuntimePersistentEntity<T> persistentEntity,
                Map<RuntimePersistentProperty<T>, Integer> parameterBinding,
                boolean supportsBatch) {
            this.sql = sql;
            this.parameterBinding = parameterBinding;
            this.identity = persistentEntity.getIdentity();
            this.generateId = identity != null && identity.isGenerated();
            this.supportsBatch = supportsBatch;
        }

        /**
         * @return The SQL
         */
        public @NonNull
        String getSql() {
            return sql;
        }

        /**
         * @return The parameter binding
         */
        public @NonNull
        Map<RuntimePersistentProperty<T>, Integer> getParameterBinding() {
            return parameterBinding;
        }

        /**
         * @return The identity
         */
        public @Nullable BeanProperty<T, Object> getIdentity() {
            if (identity != null) {
                return identity.getProperty();
            }
            return null;
        }

        /**
         * @return Is the id generated
         */
        public boolean isGenerateId() {
            return generateId;
        }
    }
}
