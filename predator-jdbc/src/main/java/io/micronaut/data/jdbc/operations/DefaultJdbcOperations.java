package io.micronaut.data.jdbc.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.mapper.ColumnIndexResultSetReader;
import io.micronaut.data.jdbc.mapper.ColumnNameResultSetReader;
import io.micronaut.data.jdbc.mapper.PreparedStatementWriter;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Named;
import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
public class DefaultJdbcOperations implements JdbcRepositoryOperations, AsyncCapableRepository, ReactiveCapableRepository {

    private static final Logger QUERY_LOG = LoggerFactory.getLogger("io.micronaut.data.query");
    private static final Pattern IN_EXPRESSION_PATTERN = Pattern.compile("\\s\\?\\$IN\\((\\d+)\\)");
    private static final String NOT_TRUE_EXPRESSION = "1 = 2";
    private static final SqlQueryBuilder DEFAULT_SQL_BUILDER = new SqlQueryBuilder();

    private final ExecutorAsyncOperations asyncOperations;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final DataSource dataSource;
    private final Map<Class, StoredInsert> storedInserts = new ConcurrentHashMap<>(10);
    private final Map<Class, RuntimePersistentEntity> entities = new ConcurrentHashMap<>(10);
    private final Map<Class, RuntimePersistentProperty> idReaders = new ConcurrentHashMap<>(10);
    private final Map<Class, Dialect> dialects = new HashMap<>(10);
    private final Map<Dialect, QueryBuilder> queryBuilders = new HashMap<>(Dialect.values().length);
    private final PreparedStatementWriter preparedStatementWriter = new PreparedStatementWriter();
    private final ColumnNameResultSetReader columnNameResultSetReader = new ColumnNameResultSetReader();
    private final ColumnIndexResultSetReader columnIndexResultSetReader = new ColumnIndexResultSetReader();

    /**
     * Default constructor.
     * @param dataSource The data source
     * @param dataSourceName The data source name
     * @param executorService The executor service
     * @param beanContext The bean context
     */
    protected DefaultJdbcOperations(@NonNull DataSource dataSource,
                                    @Parameter String dataSourceName,
                                    @Named(TaskExecutors.IO) @NonNull ExecutorService executorService,
                                    BeanContext beanContext) {
        ArgumentUtils.requireNonNull("dataSource", dataSource);
        ArgumentUtils.requireNonNull("executorService", executorService);
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        this.dataSource = dataSource;
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setReadOnly(true);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager, def);
        this.asyncOperations = new ExecutorAsyncOperations(
                this,
                executorService
        );
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
    @Override
    public AsyncRepositoryOperations async() {
        return asyncOperations;
    }

    @NonNull
    @Override
    public ReactiveRepositoryOperations reactive() {
        return new ExecutorReactiveOperations(asyncOperations);
    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return withReadConnection(connection -> {
            PreparedStatement ps = prepareStatement(connection, preparedQuery, false);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Class<T> rootEntity = preparedQuery.getRootEntity();
                Class<R> resultType = preparedQuery.getResultType();
                if (resultType == rootEntity) {
                    @SuppressWarnings("unchecked")
                    RuntimePersistentEntity<R> persistentEntity = getPersistentEntity((Class<R>) rootEntity);
                    return readEntity(rs, persistentEntity);
                } else {
                    if (preparedQuery.isDtoProjection()) {
                        // TODO: fix DTOs
                        throw new DataAccessException("DTO projections not yet supported");
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
            return null;
        });
    }

    @Override
    public <T, R> boolean exists(@NonNull PreparedQuery<T, R> preparedQuery) {
        return withReadConnection(connection -> {
            PreparedStatement ps = prepareStatement(connection, preparedQuery, false);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        });
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return StreamSupport.stream(findIterable(preparedQuery).spliterator(), false);
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return CollectionUtils.iterableToList(findIterable(preparedQuery));
    }

    @NonNull
    @Override
    public Optional<Number> executeUpdate(@NonNull PreparedQuery<?, Number> preparedQuery) {
        return withWriteConnection((connection -> {
            PreparedStatement ps = prepareStatement(connection, preparedQuery, true);
            return Optional.of(ps.executeUpdate());
        }));
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull BatchOperation<T> operation) {
        throw new UnsupportedOperationException("The deleteAll method via batch is unsupported. Execute the SQL update directly");
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        @SuppressWarnings("unchecked") StoredInsert<T> insert = resolveInsert(operation);
        return withWriteConnection((connection) -> {
            T entity = operation.getEntity();
            boolean generateId = insert.isGenerateId();
            String insertSql = insert.getSql();
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL Insert: {}", insertSql);
            }
            PreparedStatement stmt = connection
                    .prepareStatement(insertSql, generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
            setInsertParameters(insert, entity, stmt);
            int i = stmt.executeUpdate();
            RuntimePersistentProperty identity = insert.getIdentity();
            if (generateId && identity != null) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);
                    BeanProperty property = identity.getProperty();
                    if (property.getType().isInstance(id)) {
                        property.set(entity, id);
                    } else {
                        property.convertAndSet(entity, id);
                    }
                } else {
                    throw new DataAccessException("ID failed to generate. No result returned.");
                }
            }
            return entity;
        });

    }

    private <T> void setInsertParameters(StoredInsert<T> insert, T entity, PreparedStatement stmt) {
        for (Map.Entry<RuntimePersistentProperty<T>, Integer> entry : insert.getParameterBinding().entrySet()) {
            RuntimePersistentProperty<T> prop = entry.getKey();
            DataType type = prop.getDataType();
            Object value = prop.getProperty().get(entity);
            int index = entry.getValue();
            if (prop instanceof Association) {
                Association association = (Association) prop;
                if (!association.isForeignKey()) {
                    if (value != null) {
                        RuntimePersistentEntity<Object> associatedEntity = getPersistentEntity((Class<Object>) value.getClass());
                        RuntimePersistentProperty<Object> identity = associatedEntity.getIdentity();
                        if (identity == null) {
                            throw new IllegalArgumentException("Associated entity has not ID: " + associatedEntity.getName());
                        }
                        value = identity.getProperty().get(value);
                    }
                    if (QUERY_LOG.isTraceEnabled()) {
                        QUERY_LOG.trace("Binding value {} to parameter at position: {}", value, index);
                    }

                    preparedStatementWriter.setDynamic(
                            stmt,
                            index,
                            type,
                            value
                    );
                }

            } else {
                if (QUERY_LOG.isTraceEnabled()) {
                    QUERY_LOG.trace("Binding value {} to parameter at position: {}", value, index);
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

    @NonNull
    private <T> StoredInsert resolveInsert(@NonNull EntityOperation<T> operation) {
        return storedInserts.computeIfAbsent(operation.getRootEntity(), aClass -> {
            AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
            String insertStatement = annotationMetadata.stringValue(
                    PredatorMethod.class,
                    PredatorMethod.META_MEMBER_INSERT_STMT
            ).orElse(null);
            if (insertStatement == null) {
                throw new IllegalStateException("No insert statement present in repository. Ensure it extends GenericRepository");
            }

            RuntimePersistentEntity<T> persistentEntity = getPersistentEntity(operation.getRootEntity());
            Map<RuntimePersistentProperty<T>, Integer> parameterBinding = buildSqlParameterBinding(annotationMetadata, persistentEntity);
            // MSSQL doesn't support RETURN_GENERATED_KEYS https://github.com/Microsoft/mssql-jdbc/issues/245
            boolean supportsBatch = annotationMetadata.findAnnotation(Repository.class)
                    .flatMap(av -> av.enumValue("dialect", Dialect.class)
                    .map(dialect -> dialect != Dialect.SQL_SERVER)).orElse(true);
            return new StoredInsert(insertStatement, persistentEntity, parameterBinding, supportsBatch);
        });
    }

    private <T, R> Iterable<R> findIterable(@NonNull PreparedQuery<T, R> preparedQuery) {
        Class<T> rootEntity = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();
        boolean isRootResult = resultType == rootEntity;


        return withReadConnection(connection -> {
            PreparedStatement ps = prepareStatement(connection, preparedQuery, false);
            ResultSet rs = ps.executeQuery();
            if (isRootResult) {
                RuntimePersistentEntity<R> persistentEntity = getPersistentEntity(resultType);
                return () -> new Iterator<R>() {
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
                        return readEntity(rs, persistentEntity);
                    }
                };
            } else {
                if (preparedQuery.isDtoProjection()) {
                    // TODO: improve projection on entity properties
                    throw new DataAccessException("DTO projections not yet supported");
                } else {
                    return () -> new Iterator<R>() {
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
                                return (R) v;
                            } else {
                                return columnIndexResultSetReader.convertRequired(v, resultType);
                            }
                        }
                    };
                }
            }
        });
    }

    private <R> R readEntity(ResultSet rs, RuntimePersistentEntity<R> persistentEntity) {
        BeanIntrospection<R> introspection = persistentEntity.getIntrospection();
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        R entity;
        if (ArrayUtils.isEmpty(constructorArguments)) {
            entity = introspection.instantiate();
        } else {
            // TODO: constructor arguments
            throw new IllegalStateException("Constructor arguments not yet supported");
        }
        BeanWrapper<R> wrapper = BeanWrapper.getWrapper(entity);
        for (PersistentProperty persistentProperty : persistentEntity.getPersistentProperties()) {
            String persistedName = persistentProperty.getPersistedName();
            if (persistentProperty instanceof Association) {
                Association association = (Association) persistentProperty;
                if (!association.isForeignKey()) {
                    RuntimePersistentEntity associatedEntity = getPersistentEntity(((RuntimePersistentProperty) association).getProperty().getType());
                    Object referenced = associatedEntity.getIntrospection().instantiate();
                    RuntimePersistentProperty identity = associatedEntity.getIdentity();
                    if (identity != null) {
                        Object v = columnNameResultSetReader.readDynamic(rs, persistedName, identity.getDataType());
                        BeanWrapper.getWrapper(referenced).setProperty(
                                identity.getName(),
                                v
                        );
                    }
                    wrapper.setProperty(
                            persistentProperty.getName(),
                            referenced
                    );
                }
            } else {
                Object v = columnNameResultSetReader.readDynamic(rs, persistedName, persistentProperty.getDataType());
                wrapper.setProperty(
                        persistentProperty.getName(),
                        v
                );
            }
        }
        RuntimePersistentProperty<R> identity = persistentEntity.getIdentity();
        if (identity != null) {
            String persistedName = identity.getPersistedName();
            Object v = columnNameResultSetReader.readDynamic(rs, persistedName, identity.getDataType());
            wrapper.setProperty(
                    identity.getName(),
                    v
            );
        }
        return entity;
    }

    private <T, R> PreparedStatement prepareStatement(Connection connection, @NonNull PreparedQuery<T, R> preparedQuery, boolean isUpdate) throws SQLException {
        String query = preparedQuery.getQuery();

        final boolean hasIn = preparedQuery.hasInExpression();
        Map<Integer, Object> parameterValues = preparedQuery.getIndexedParameterValues();
        Map<Integer, DataType> parameterTypes = preparedQuery.getIndexedParameterTypes();
        if (hasIn) {
            parameterValues = new HashMap<>(parameterValues); // create a copy
            query = expandInExpressions(query, parameterValues);
        }

        if (!isUpdate) {
            Pageable pageable = preparedQuery.getPageable();
            if (pageable != Pageable.UNPAGED) {
                Sort sort = pageable.getSort();
                Dialect dialect = dialects.getOrDefault(preparedQuery.getRepositoryType(), Dialect.ANSI);
                QueryBuilder queryBuilder = queryBuilders.getOrDefault(dialect, DEFAULT_SQL_BUILDER);
                if (sort.isSorted()) {
                    query += queryBuilder.buildOrderBy(getPersistentEntity(preparedQuery.getRootEntity()), sort).getQuery();
                } else if (dialect == Dialect.SQL_SERVER) {
                    // SQL server requires order by
                    RuntimePersistentEntity<T> persistentEntity = getPersistentEntity(preparedQuery.getRootEntity());
                    RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                    if (identity == null) {
                        throw new DataAccessException("Pagination requires an entity ID on SQL Server");
                    }
                    sort = Sort.unsorted().order(Sort.Order.asc(identity.getName()));
                    query += queryBuilder.buildOrderBy(persistentEntity, sort).getQuery();
                }

                query += queryBuilder.buildPagination(pageable).getQuery();
            }
        }

        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Query: {}", query);
        }
        final PreparedStatement ps = connection.prepareStatement(query);

        for (Map.Entry<Integer, Object> entry : parameterValues.entrySet()) {
            int index = entry.getKey();
            Object value = entry.getValue();
            if (QUERY_LOG.isTraceEnabled()) {
                QUERY_LOG.trace("Binding parameter at position {} to value {}", index, value);
            }
            DataType dataType = parameterTypes.get(index);
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

    private RuntimePersistentProperty<Object> getIdReader(Object o) {
        Class<Object> type = (Class<Object>) o.getClass();
        RuntimePersistentProperty beanProperty = idReaders.get(type);
        if (beanProperty == null) {

            RuntimePersistentEntity<Object> entity = getPersistentEntity(type);
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

    private <T> T withReadConnection(SqlFunction<T> callback) {
        //noinspection Duplicates
        return readTransactionTemplate.execute((status) -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                return callback.apply(connection);
            } catch (SQLException e) {
                throw new DataAccessException("Error executing Read Operation: " + e.getMessage());
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        });
    }

    private <T> T withWriteConnection(SqlFunction<T> callback) {
        //noinspection Duplicates
        return writeTransactionTemplate.execute((status) -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                return callback.apply(connection);
            } catch (SQLException e) {
                throw new DataAccessException("Error executing Write Operation: " + e.getMessage());
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        });
    }

    private <T> RuntimePersistentEntity<T> getPersistentEntity(Class<T> type) {
        RuntimePersistentEntity<T> entity = entities.get(type);
        if (entity == null) {
            entity = PersistentEntity.of(type);
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
            return withWriteConnection((connection) -> {
                List<T> results = new ArrayList<>();
                boolean generateId = insert.isGenerateId();
                String insertSql = insert.getSql();

                PreparedStatement stmt = connection
                        .prepareStatement(insertSql, generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Batch SQL Insert: {}", insertSql);
                }
                for (T entity : operation) {
                    setInsertParameters(insert, entity, stmt);
                    stmt.addBatch();
                    results.add(entity);
                }
                stmt.executeBatch();
                RuntimePersistentProperty identity = insert.getIdentity();
                if (generateId && identity != null) {
                    BeanProperty idProperty = identity.getProperty();
                    Iterator<T> resultIterator = results.iterator();
                    ResultSet generatedKeys = stmt.getGeneratedKeys();
                    while (resultIterator.hasNext()) {
                        T entity = resultIterator.next();
                        if (!generatedKeys.next()) {
                            throw new DataAccessException("Failed to generate ID for entity: " + entity);
                        } else {
                            long id = generatedKeys.getLong(1);
                            if (idProperty.getType().isInstance(id)) {
                                idProperty.set(entity, id);
                            } else {
                                idProperty.convertAndSet(entity, id);
                            }
                        }
                    }
                }
                return results;
            });
        }
    }

    /**
     * SQL callback interface.
     * @param <T> The return type
     */
    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    /**
     * A stored insert statement.
     * @param <T> The entity type
     */
    protected class StoredInsert<T> {
        private final RuntimePersistentEntity<T> persistentEntity;
        private final Map<RuntimePersistentProperty<T>, Integer> parameterBinding;
        private final RuntimePersistentProperty identity;
        private final boolean generateId;
        private final String sql;
        private final boolean supportsBatch;

        /**
         * Default constructor.
         * @param sql The SQL INSERT
         * @param persistentEntity The entity
         * @param parameterBinding The parameter binding
         * @param supportsBatch Whether batch insert is supported
         */
        StoredInsert(
                String sql,
                RuntimePersistentEntity<T> persistentEntity,
                Map<RuntimePersistentProperty<T>, Integer> parameterBinding,
                boolean supportsBatch) {
            this.sql = sql;
            this.persistentEntity = persistentEntity;
            this.parameterBinding = parameterBinding;
            this.identity = persistentEntity.getIdentity();
            this.generateId = identity != null && identity.isGenerated();
            this.supportsBatch = supportsBatch;
        }

        /**
         * @return The SQL
         */
        public @NonNull String getSql() {
            return sql;
        }

        /**
         * Are batch inserts supported.
         * @return True if the are
         */
        public boolean supportsBatchInsert() {
            return supportsBatch || identity == null || !identity.isGenerated();
        }

        /**
         * @return The parameter binding
         */
        public @NonNull Map<RuntimePersistentProperty<T>, Integer> getParameterBinding() {
            return parameterBinding;
        }

        /**
         * @return The identity
         */
        public @Nullable RuntimePersistentProperty getIdentity() {
            return identity;
        }

        /**
         * @return Is the id generated
         */
        public boolean isGenerateId() {
            return generateId;
        }
    }
}
