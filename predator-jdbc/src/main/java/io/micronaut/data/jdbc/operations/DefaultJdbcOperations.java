package io.micronaut.data.jdbc.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.jdbc.mapper.PreparedStatementWriter;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.operations.reactive.ReactiveCapableRepository;
import io.micronaut.data.operations.reactive.ReactiveRepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import io.micronaut.data.runtime.operations.ExecutorAsyncOperations;
import io.micronaut.data.runtime.operations.ExecutorReactiveOperations;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of {@link JdbcRepositoryOperations}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachBean(DataSource.class)
public class DefaultJdbcOperations implements JdbcRepositoryOperations, AsyncCapableRepository, ReactiveCapableRepository {

    private final ExecutorAsyncOperations asyncOperations;
    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final DataSource dataSource;
    private final Map<Class, StoredInsert> storedInserts = new ConcurrentHashMap<>(10);
    private final Map<Class, RuntimePersistentEntity> entities = new ConcurrentHashMap<>(10);
    private final PreparedStatementWriter preparedStatementWriter = new PreparedStatementWriter();
    
    /**
     * Default constructor.
     * @param dataSource The data source
     * @param executorService The executor service
     */
    protected DefaultJdbcOperations(@NonNull DataSource dataSource, @NonNull ExecutorService executorService) {
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
    public <T> T findOne(@NonNull Class<T> type, @NonNull Serializable id) {
        return null;
    }

    @Nullable
    @Override
    public <T, R> R findOne(@NonNull PreparedQuery<T, R> preparedQuery) {
        return null;
    }

    @NonNull
    @Override
    public <T> Iterable<T> findAll(@NonNull PagedQuery<T> query) {
        return null;
    }

    @Override
    public <T> long count(PagedQuery<T> pagedQuery) {
        return 0;
    }

    @NonNull
    @Override
    public <T, R> Iterable<R> findAll(@NonNull PreparedQuery<T, R> preparedQuery) {
        return null;
    }

    @NonNull
    @Override
    public <T, R> Stream<R> findStream(@NonNull PreparedQuery<T, R> preparedQuery) {
        return null;
    }

    @NonNull
    @Override
    public <T> Stream<T> findStream(@NonNull PagedQuery<T> query) {
        return null;
    }

    @Override
    public <R> Page<R> findPage(@NonNull PagedQuery<R> query) {
        return null;
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


        return writeTransactionTemplate.execute((status) -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                T entity = operation.getEntity();
                boolean generateId = insert.isGenerateId();
                PreparedStatement stmt = connection.prepareStatement(insert.getSql(), generateId ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
                for (Map.Entry<RuntimePersistentProperty<T>, Integer> entry : insert.getParameterBinding().entrySet()) {
                    RuntimePersistentProperty<T> prop = entry.getKey();
                    DataType type = prop.getDataType();
                    Object value = prop.getProperty().get(entity);
                    preparedStatementWriter.setDynamic(
                            stmt,
                            entry.getValue(),
                            type,
                            value
                    );
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
            } catch (SQLException e) {
                throw new DataAccessException("Error executing INSERT: " + e.getMessage());
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
            });
            results.add(o);
        }
        return results;
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
