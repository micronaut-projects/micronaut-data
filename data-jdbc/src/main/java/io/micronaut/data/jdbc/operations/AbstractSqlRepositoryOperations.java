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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.EntityOperation;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.mapper.QueryStatement;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import org.slf4j.Logger;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Abstract SQL repository implementation not specifically bound to JDBC.
 *
 * @param <RS> The result set type
 * @param <PS> The prepared statement type
 * @author graemerocher
 * @since 1.0.0
 */
public abstract class AbstractSqlRepositoryOperations<RS, PS> implements RepositoryOperations {
    protected static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    protected static final SqlQueryBuilder DEFAULT_SQL_BUILDER = new SqlQueryBuilder();
    protected static final Pattern IN_EXPRESSION_PATTERN = Pattern.compile("\\s\\?\\$IN\\((\\d+)\\)");
    protected static final String NOT_TRUE_EXPRESSION = "1 = 2";
    @SuppressWarnings("WeakerAccess")
    protected final ResultReader<RS, String> columnNameResultSetReader;
    @SuppressWarnings("WeakerAccess")
    protected final ResultReader<RS, Integer> columnIndexResultSetReader;
    @SuppressWarnings("WeakerAccess")
    protected final QueryStatement<PS, Integer> preparedStatementWriter;
    protected final Map<Class, Dialect> dialects = new HashMap<>(10);
    protected final Map<Dialect, SqlQueryBuilder> queryBuilders = new HashMap<>(Dialect.values().length);
    protected final MediaTypeCodec jsonCodec;
    protected final DateTimeProvider dateTimeProvider;

    private final Map<Class, StoredInsert> storedInserts = new ConcurrentHashMap<>(10);
    private final Map<QueryKey, StoredInsert> entityInserts = new ConcurrentHashMap<>(10);
    private final Map<QueryKey, StoredInsert> entityUpdates = new ConcurrentHashMap<>(10);
    private final Map<Association, String> associationInserts = new ConcurrentHashMap<>(10);
    private final Map<Class, RuntimePersistentEntity> entities = new ConcurrentHashMap<>(10);
    private final Map<Class, RuntimePersistentProperty> idReaders = new ConcurrentHashMap<>(10);

    /**
     * Default constructor.
     *
     * @param columnNameResultSetReader  The column name result reader
     * @param columnIndexResultSetReader The column index result reader
     * @param preparedStatementWriter    The prepared statement writer
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The injected dateTimeProvider instance
     */
    protected AbstractSqlRepositoryOperations(
            ResultReader<RS, String> columnNameResultSetReader,
            ResultReader<RS, Integer> columnIndexResultSetReader,
            QueryStatement<PS, Integer> preparedStatementWriter,
            List<MediaTypeCodec> codecs,
            @NonNull DateTimeProvider dateTimeProvider) {
        this.columnNameResultSetReader = columnNameResultSetReader;
        this.columnIndexResultSetReader = columnIndexResultSetReader;
        this.preparedStatementWriter = preparedStatementWriter;
        this.jsonCodec = resolveJsonCodec(codecs);
        this.dateTimeProvider = dateTimeProvider;
    }

    private MediaTypeCodec resolveJsonCodec(List<MediaTypeCodec> codecs) {
        return CollectionUtils.isNotEmpty(codecs) ? codecs.stream().filter(c -> c.getMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE)).findFirst().orElse(null) : null;
    }

    @NonNull
    @Override
    public final <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        RuntimePersistentEntity<T> entity = entities.get(type);
        if (entity == null) {
            entity = new RuntimePersistentEntity<T>(type) {
                @Override
                protected RuntimePersistentEntity<T> getEntity(Class<T> type) {
                    return AbstractSqlRepositoryOperations.this.getEntity(type);
                }
            };
            entities.put(type, entity);
        }
        return entity;
    }

    /**
     * Sets the insert parameters for the given insert, entity and statement.
     *
     * @param insert The insert
     * @param entity The entity
     * @param stmt   The statement
     * @param <T>    The entity type
     */
    protected final <T> void setInsertParameters(@NonNull StoredInsert<T> insert, @NonNull T entity, @NonNull PS stmt) {
        Object now = null;
        RuntimePersistentEntity<T> persistentEntity = insert.getPersistentEntity();
        final String[] parameterBinding = insert.getParameterBinding();
        for (int i = 0; i < parameterBinding.length; i++) {
            String path = parameterBinding[i];
            RuntimePersistentProperty<T> prop = persistentEntity.getPropertyByName(path);
            if (prop == null) {
                int j = path.indexOf('.');
                if (j > -1) {
                    RuntimePersistentProperty embeddedProp = (RuntimePersistentProperty)
                            persistentEntity.getPropertyByPath(path).orElse(null);
                    if (embeddedProp != null) {

                        // embedded case
                        prop = persistentEntity.getPropertyByName(path.substring(0, j));
                        if (prop instanceof Association) {
                            Association assoc = (Association) prop;
                            if (assoc.getKind() == Relation.Kind.EMBEDDED) {

                                Object value = prop.getProperty().get(entity);
                                Object embeddedValue = value != null ? embeddedProp.getProperty().get(value) : null;
                                int index = i + 1;
                                preparedStatementWriter.setDynamic(
                                        stmt,
                                        index,
                                        embeddedProp.getDataType(),
                                        embeddedValue
                                );
                            }
                        }
                    }
                }
            } else {
                DataType type = prop.getDataType();
                BeanProperty<T, Object> beanProperty = (BeanProperty<T, Object>) prop.getProperty();
                Object value = beanProperty.get(entity);
                int index = i + 1;
                if (prop instanceof Association) {
                    Association association = (Association) prop;
                    if (!association.isForeignKey()) {
                        @SuppressWarnings("unchecked")
                        RuntimePersistentEntity<Object> associatedEntity = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
                        RuntimePersistentProperty<Object> identity = associatedEntity.getIdentity();
                        if (identity == null) {
                            throw new IllegalArgumentException("Associated entity has not ID: " + associatedEntity.getName());
                        } else {
                            type = identity.getDataType();
                        }
                        BeanProperty<Object, ?> identityProperty = identity.getProperty();
                        if (value != null) {
                            value = identityProperty.get(value);
                        }
                        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                            DataSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", value, index);
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
                            now = now != null ? now : dateTimeProvider.getNow();
                            if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                                DataSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", now, index);
                            }
                            preparedStatementWriter.setDynamic(
                                    stmt,
                                    index,
                                    type,
                                    now
                            );
                            beanProperty.convertAndSet(entity, now);
                        } else if (beanProperty.hasAnnotation(DateUpdated.class)) {
                            now = now != null ? now : dateTimeProvider.getNow();
                            if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                                DataSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", now, index);
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
                            if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                                DataSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", uuid, index);
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
                        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                            DataSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", value, index);
                        }
                        if (type == DataType.JSON && jsonCodec != null) {
                            value = new String(jsonCodec.encode(value), StandardCharsets.UTF_8);
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
    }

    /**
     * Resolve the INSERT for the given {@link EntityOperation}.
     *
     * @param operation The operation
     * @param <T>       The entity type
     * @return The insert
     */
    @NonNull
    protected final <T> StoredInsert resolveInsert(@NonNull EntityOperation<T> operation) {
        return storedInserts.computeIfAbsent(operation.getRootEntity(), aClass -> {
            AnnotationMetadata annotationMetadata = operation.getAnnotationMetadata();
            String insertStatement = annotationMetadata.stringValue(Query.class).orElse(null);
            if (insertStatement == null) {
                throw new IllegalStateException("No insert statement present in repository. Ensure it extends GenericRepository and is annotated with @JdbcRepository");
            }

            RuntimePersistentEntity<T> persistentEntity = getEntity(operation.getRootEntity());
            String[] parameterBinding = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
            // MSSQL doesn't support RETURN_GENERATED_KEYS https://github.com/Microsoft/mssql-jdbc/issues/245 with BATCHi
            final Dialect dialect = annotationMetadata.enumValue(Repository.class, "dialect", Dialect.class)
                    .orElse(Dialect.ANSI);
            boolean supportsBatch = dialect != Dialect.SQL_SERVER;
            return new StoredInsert<>(insertStatement, persistentEntity, parameterBinding, supportsBatch, dialect);
        });
    }

    /**
     * Obtain an ID reader for the given object.
     *
     * @param o The object
     * @return The ID reader
     */
    @NonNull
    protected final RuntimePersistentProperty<Object> getIdReader(@NonNull Object o) {
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

    private <T> Map<String, Integer> buildSqlParameterBinding(AnnotationMetadata annotationMetadata) {
        AnnotationValue<DataMethod> annotation = annotationMetadata.getAnnotation(DataMethod.class);
        if (annotation == null) {
            return Collections.emptyMap();
        }
        String[] parameterData = annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS);
        Map<String, Integer> parameterValues;
        if (ArrayUtils.isNotEmpty(parameterData)) {
            parameterValues = new HashMap<>(parameterData.length);
            for (int i = 0; i < parameterData.length; i++) {
                String p = parameterData[i];
                parameterValues.put(p, i + 1);
            }
        } else {
            parameterValues = Collections.emptyMap();
        }
        return parameterValues;
    }

    /**
     * Build a sort for ID for the given entity.
     * @param persistentEntity The entity
     * @param <T> The entity type
     * @return The sort
     */
    @NonNull
    protected final <T> Sort sortById(RuntimePersistentEntity<T> persistentEntity) {
        Sort sort;
        RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
        if (identity == null) {
            throw new DataAccessException("Pagination requires an entity ID on SQL Server");
        }
        sort = Sort.unsorted().order(Sort.Order.asc(identity.getName()));
        return sort;
    }

    /**
     * In the dialect SQL server and is order by required.
     * @param query The query
     * @param dialect The dialect
     * @return True if it is
     */
    protected final boolean isSqlServerWithoutOrderBy(String query, Dialect dialect) {
        return dialect == Dialect.SQL_SERVER && !query.contains(AbstractSqlLikeQueryBuilder.ORDER_BY_CLAUSE);
    }

    /**
     * Compute the size of the given object.
     * @param value The value
     * @return The size
     */
    protected final int sizeOf(Object value) {
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

    /**
     * Set the parameter value on the given statement.
     * @param preparedStatement The prepared statement
     * @param index The index
     * @param dataType The data type
     * @param value The value
     */
    protected final void setStatementParameter(PS preparedStatement, int index, DataType dataType, Object value) {
        switch (dataType) {
            case JSON:
                if (value != null && jsonCodec != null) {
                    value = new String(jsonCodec.encode(value), StandardCharsets.UTF_8);
                }
                preparedStatementWriter.setDynamic(
                        preparedStatement,
                        index,
                        dataType,
                        value);
            break;
            case ENTITY:
                if (value != null) {
                    RuntimePersistentProperty<Object> idReader = getIdReader(value);
                    Object id = idReader.getProperty().get(value);
                    if (id == null) {
                        throw new DataAccessException("Supplied entity is a transient instance: " + value);
                    }
                    value = id;
                    dataType = idReader.getDataType();
                }
                // intentional fall through
            default:
                preparedStatementWriter.setDynamic(
                        preparedStatement,
                        index,
                        dataType,
                        value);
        }
    }

    /**
     * Resolves a stored insert for the given entity.
     * @param annotationMetadata  The repository annotation metadata
     * @param repositoryType  The repository type
     * @param rootEntity The root entity
     * @param persistentEntity The persistent entity
     * @param <T> The generic type
     * @return The insert
     */
    protected @NonNull <T> StoredInsert<T> resolveEntityInsert(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            @NonNull Class<?> rootEntity,
            @NonNull RuntimePersistentEntity<?> persistentEntity) {

        //noinspection unchecked
        return entityInserts.computeIfAbsent(new QueryKey(repositoryType, rootEntity), (queryKey) -> {
            final Dialect dialect = dialects.getOrDefault(queryKey.repositoryType, Dialect.ANSI);
            final SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(dialect, DEFAULT_SQL_BUILDER);
            final QueryResult queryResult = queryBuilder.buildInsert(annotationMetadata, persistentEntity);

            final String sql = queryResult.getQuery();
            final Map<String, String> parameters = queryResult.getParameters();
            return new StoredInsert<>(
                    sql,
                    persistentEntity,
                    parameters.values().toArray(new String[0]),
                    dialect != Dialect.SQL_SERVER,
                    dialect
            );
        });
    }

    /**
     * Resolves a stored update for the given entity.
     * @param annotationMetadata  The repository annotation metadata
     * @param repositoryType  The repository type
     * @param rootEntity The root entity
     * @param persistentEntity The persistent entity
     * @param <T> The generic type
     * @return The insert
     */
    protected @NonNull <T> StoredInsert<T> resolveEntityUpdate(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            @NonNull Class<?> rootEntity,
            @NonNull RuntimePersistentEntity<?> persistentEntity) {

        final QueryKey key = new QueryKey(repositoryType, rootEntity);
        //noinspection unchecked
        return entityUpdates.computeIfAbsent(key, (queryKey) -> {
            final Dialect dialect = dialects.getOrDefault(queryKey.repositoryType, Dialect.ANSI);
            final SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(dialect, DEFAULT_SQL_BUILDER);

            final String idName;
            final PersistentProperty identity = persistentEntity.getIdentity();
            if (identity != null) {
                idName = identity.getName();
            } else {
                idName = TypeRole.ID;
            }
            final QueryModel queryModel = QueryModel.from(persistentEntity)
                    .idEq(new QueryParameter(idName));
            List<String> updateProperties = persistentEntity.getPersistentProperties()
                    .stream().filter(p ->
                            !((p instanceof Association) && ((Association) p).isForeignKey()) &&
                                    p.getAnnotationMetadata().booleanValue(AutoPopulated.class, "updateable").orElse(true)
                    )
                    .map(PersistentProperty::getName)
                    .collect(Collectors.toList());
            final QueryResult queryResult = queryBuilder.buildUpdate(
                    annotationMetadata,
                    queryModel,
                    updateProperties
            );

            final String sql = queryResult.getQuery();
            final Map<String, String> parameters = queryResult.getParameters();
            return new StoredInsert<>(
                    sql,
                    persistentEntity,
                    parameters.values().toArray(new String[0]),
                    dialect != Dialect.SQL_SERVER,
                    dialect
            );
        });
    }

    /**
     * Builds a join table insert.
     * @param repositoryType The repository type
     * @param persistentEntity  The entity
     * @param association The association
     * @param <T> The entity generic type
     * @return The insert statement
     */
    protected <T> String resolveAssociationInsert(
            Class repositoryType,
            RuntimePersistentEntity<T> persistentEntity,
            RuntimeAssociation<T> association) {
        return associationInserts.computeIfAbsent(association, association1 -> {
            final Dialect dialect = dialects.getOrDefault(repositoryType, Dialect.ANSI);
            final SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(dialect, DEFAULT_SQL_BUILDER);
            return queryBuilder.buildJoinTableInsert(persistentEntity, association1);
        });
    }

    /**
     * Used to cache queries for entities.
     */
    private class QueryKey {
        final Class repositoryType;
        final Class entityType;

        QueryKey(Class repositoryType, Class entityType) {
            this.repositoryType = repositoryType;
            this.entityType = entityType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            QueryKey queryKey = (QueryKey) o;
            return repositoryType.equals(queryKey.repositoryType) &&
                    entityType.equals(queryKey.entityType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(repositoryType, entityType);
        }
    }

    /**
     * A stored insert statement.
     *
     * @param <T> The entity type
     */
    protected final class StoredInsert<T> {
        private final String[] parameterBinding;
        private final RuntimePersistentProperty identity;
        private final boolean generateId;
        private final String sql;
        private final boolean supportsBatch;
        private final RuntimePersistentEntity<T> persistentEntity;
        private final Dialect dialect;

        /**
         * Default constructor.
         *  @param sql              The SQL INSERT
         * @param persistentEntity The entity
         * @param parameterBinding The parameter binding
         * @param supportsBatch    Whether batch insert is supported
         * @param dialect The dialect
         */
        StoredInsert(
                String sql,
                RuntimePersistentEntity<T> persistentEntity,
                String[] parameterBinding,
                boolean supportsBatch,
                Dialect dialect) {
            this.sql = sql;
            this.persistentEntity = persistentEntity;
            this.parameterBinding = parameterBinding;
            this.identity = persistentEntity.getIdentity();
            this.generateId = identity != null && identity.isGenerated();
            this.supportsBatch = supportsBatch;
            this.dialect = dialect;
        }

        /**
         * @return The dialect
         */
        public @NonNull Dialect getDialect() {
            return dialect;
        }

        /**
         * @return The persistent entity
         */
        public RuntimePersistentEntity<T> getPersistentEntity() {
            return persistentEntity;
        }

        /**
         * @return Whether batch inserts are allowed.
         */
        public boolean doesSupportBatch() {
            return supportsBatch;
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
        String[] getParameterBinding() {
            return parameterBinding;
        }

        /**
         * @return The identity
         */
        public @Nullable
        BeanProperty<T, Object> getIdentityProperty() {
            if (identity != null) {
                return identity.getProperty();
            }
            return null;
        }

        /**
         * @return The runtime persistent property.
         */
        RuntimePersistentProperty getIdentity() {
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
