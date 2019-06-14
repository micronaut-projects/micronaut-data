package io.micronaut.data.jdbc.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.jdbc.mapper.ColumnIndexResultSetReader;
import io.micronaut.data.jdbc.mapper.ColumnNameResultSetReader;
import io.micronaut.data.jdbc.mapper.PreparedStatementWriter;
import io.micronaut.data.model.*;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import io.micronaut.scheduling.TaskExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Nonnull;
import javax.inject.Named;
import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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

    private final ExecutorAsyncOperations asyncOperations;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final DataSource dataSource;
    private final Map<Class, StoredInsert> storedInserts = new ConcurrentHashMap<>(10);
    private final Map<Class, RuntimePersistentEntity> entities = new ConcurrentHashMap<>(10);
    private final PreparedStatementWriter preparedStatementWriter = new PreparedStatementWriter();
    private final ColumnNameResultSetReader columnNameResultSetReader = new ColumnNameResultSetReader();
    private final ColumnIndexResultSetReader columnIndexResultSetReader = new ColumnIndexResultSetReader();

    /**
     * Default constructor.
     * @param dataSource The data source
     * @param executorService The executor service
     */
    protected DefaultJdbcOperations(@NonNull DataSource dataSource,
                                    @Named(TaskExecutors.IO) @NonNull ExecutorService executorService) {
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
            PreparedStatement ps = prepareStatement(connection, preparedQuery);
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
        return Optional.empty();
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull BatchOperation<T> operation) {
        return Optional.empty();
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public <T> T persist(@NonNull InsertOperation<T> operation) {
        @SuppressWarnings("unchecked") StoredInsert<T> insert = storedInserts.computeIfAbsent(operation.getRootEntity(), aClass -> {
            AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
            String insert1 = annotationMetadata.stringValue(
                    PredatorMethod.class,
                    PredatorMethod.META_MEMBER_INSERT_STMT
            ).orElse(null);
            if (insert1 == null) {
                throw new IllegalStateException("No insert statement present in repository. Ensure it extends GenericRepository");
            }

            T entity = operation.getEntity();
            @SuppressWarnings("unchecked") Class<T> type = (Class<T>) entity.getClass();
            RuntimePersistentEntity<T> persistentEntity = getPersistentEntity(type);
            Map<RuntimePersistentProperty<T>, Integer> parameterBinding = buildSqlParameterBinding(annotationMetadata, persistentEntity);
            return new StoredInsert(insert1, persistentEntity, parameterBinding);
        });


        return withWriteConnection((connection) -> {
            T entity = operation.getEntity();
            boolean generateId = insert.isGenerateId();
            String insertSql = insert.getSql();
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL Insert: {}", insertSql);
            }
            PreparedStatement stmt = connection.prepareStatement(insertSql, generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
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
                        preparedStatementWriter.setDynamic(
                                stmt,
                                index,
                                type,
                                value
                        );
                    }

                } else {
                    preparedStatementWriter.setDynamic(
                            stmt,
                            index,
                            type,
                            value
                    );
                }
            }
            int i = stmt.executeUpdate();
            if (generateId) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    long id = generatedKeys.getLong(1);
                    BeanWrapper.getWrapper(entity).setProperty(
                            insert.getIdentity().getName(),
                            id
                    );
                } else {
                    throw new DataAccessException("ID failed to generate. No result returned.");
                }
            }
            return entity;
        });

    }

    private <T, R> Iterable<R> findIterable(@NonNull PreparedQuery<T, R> preparedQuery) {
        Class<T> rootEntity = preparedQuery.getRootEntity();
        Class<R> resultType = preparedQuery.getResultType();
        boolean isRootResult = resultType == rootEntity;


        return withReadConnection(connection -> {
            PreparedStatement ps = prepareStatement(connection, preparedQuery);
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

    private <T, R> PreparedStatement prepareStatement(Connection connection, @NonNull PreparedQuery<T, R> preparedQuery) throws SQLException {
        String query = preparedQuery.getQuery();
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Query: {}", query);
        }
        PreparedStatement ps = connection.prepareStatement(query);
        Map<String, Object> parameterValues = preparedQuery.getParameterValues();
        for (Map.Entry<String, Object> entry : parameterValues.entrySet()) {
            // TODO: better parameter type handling
            int index = Integer.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (QUERY_LOG.isTraceEnabled()) {
                QUERY_LOG.trace("Binding parameter at position {} to value {}", index, value);
            }
            DataType dataType = DataType.OBJECT;
            preparedStatementWriter.setDynamic(
                    ps,
                    index,
                    dataType,
                    value);
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
        return AbstractQueryInterceptor.buildParameterBinding(annotationMetadata, PredatorMethod.META_MEMBER_INSERT_BINDING)
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
        List<T> results = new ArrayList<>();
        for (T t : operation) {

            T o = persist(new InsertOperation<T>() {
                @NonNull
                @Override
                public Class<T> getRootEntity() {
                    return operation.getRootEntity();
                }

                @NonNull
                @Override
                public T getEntity() {
                    return t;
                }

                @Nonnull
                @Override
                public String getName() {
                    return operation.getName();
                }

                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return operation.getAnnotationMetadata();
                }
            });
            results.add(o);
        }
        return results;
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
        private final PersistentProperty identity;
        private final boolean generateId;
        private final String sql;

        /**
         * Default constructor.
         * @param sql The SQL INSERT
         * @param persistentEntity The entity
         * @param parameterBinding The parameter binding
         */
        StoredInsert(
                String sql,
                RuntimePersistentEntity<T> persistentEntity,
                Map<RuntimePersistentProperty<T>, Integer> parameterBinding) {
            this.sql = sql;
            this.persistentEntity = persistentEntity;
            this.parameterBinding = parameterBinding;
            this.identity = persistentEntity.getIdentity();
            this.generateId = identity != null && identity.isGenerated();
        }

        /**
         * @return The SQL
         */
        public @NonNull String getSql() {
            return sql;
        }

        /**
         * @return The entity
         */
        public @NonNull RuntimePersistentEntity<T> getPersistentEntity() {
            return persistentEntity;
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
        public @Nullable PersistentProperty getIdentity() {
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
