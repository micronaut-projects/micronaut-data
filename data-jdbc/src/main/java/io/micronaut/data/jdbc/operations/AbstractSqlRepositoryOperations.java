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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
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
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.mapper.QueryStatement;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
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
    private static final Object IGNORED_PARAMETER = new Object();
    @SuppressWarnings("WeakerAccess")
    protected final ResultReader<RS, String> columnNameResultSetReader;
    @SuppressWarnings("WeakerAccess")
    protected final ResultReader<RS, Integer> columnIndexResultSetReader;
    @SuppressWarnings("WeakerAccess")
    protected final QueryStatement<PS, Integer> preparedStatementWriter;
    protected final Map<Class, SqlQueryBuilder> queryBuilders = new HashMap<>(10);
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
     * @param dataSourceName             The datasource name
     * @param columnNameResultSetReader  The column name result reader
     * @param columnIndexResultSetReader The column index result reader
     * @param preparedStatementWriter    The prepared statement writer
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The injected dateTimeProvider instance
     * @param beanContext                The bean context
     */
    protected AbstractSqlRepositoryOperations(
            String dataSourceName,
            ResultReader<RS, String> columnNameResultSetReader,
            ResultReader<RS, Integer> columnIndexResultSetReader,
            QueryStatement<PS, Integer> preparedStatementWriter,
            List<MediaTypeCodec> codecs,
            @NonNull DateTimeProvider dateTimeProvider,
            BeanContext beanContext) {
        this.columnNameResultSetReader = columnNameResultSetReader;
        this.columnIndexResultSetReader = columnIndexResultSetReader;
        this.preparedStatementWriter = preparedStatementWriter;
        this.jsonCodec = resolveJsonCodec(codecs);
        this.dateTimeProvider = dateTimeProvider;
        Collection<BeanDefinition<GenericRepository>> beanDefinitions = beanContext
                .getBeanDefinitions(GenericRepository.class, Qualifiers.byStereotype(Repository.class));
        for (BeanDefinition<GenericRepository> beanDefinition : beanDefinitions) {
            String targetDs = beanDefinition.stringValue(Repository.class).orElse("default");
            if (targetDs.equalsIgnoreCase(dataSourceName)) {
                Class<GenericRepository> beanType = beanDefinition.getBeanType();
                SqlQueryBuilder queryBuilder = new SqlQueryBuilder(beanDefinition.getAnnotationMetadata());
                queryBuilders.put(beanType, queryBuilder);
            }
        }
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
     * Prepare a statement for execution.
     *
     * @param statementFunction The statement function
     * @param preparedQuery     The prepared query
     * @param isUpdate          Is this an update
     * @param isSingleResult    Is it a single result
     * @param <T>               The query declaring type
     * @param <R>               The query result type
     * @return The prepared statement
     */
    protected <T, R> PS prepareStatement(
            StatementSupplier<PS> statementFunction,
            @NonNull PreparedQuery<T, R> preparedQuery,
            boolean isUpdate,
            boolean isSingleResult) {
        Object[] queryParameters = preparedQuery.getParameterArray();
        int[] parameterBinding = preparedQuery.getIndexedParameterBinding();
        DataType[] parameterTypes = preparedQuery.getIndexedParameterTypes();
        String query = preparedQuery.getQuery();
        SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(preparedQuery.getRepositoryType(), DEFAULT_SQL_BUILDER);
        final Dialect dialect = queryBuilder.getDialect();
        final boolean hasIn = preparedQuery.hasInExpression();
        if (hasIn) {
            Matcher matcher = IN_EXPRESSION_PATTERN.matcher(query);
            // this has to be done is two passes, one to remove and establish new indexes
            // and again to expand existing indexes
            while (matcher.find()) {
                int inIndex = Integer.parseInt(matcher.group(1));
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
                        if (queryBuilder.positionalParameterFormat().equals(SqlQueryBuilder.DEFAULT_POSITIONAL_PARAMETER_MARKER)) {
                            String replacement = " IN(" + String.join(",", Collections.nCopies(size, "?")) + ")";
                            query = matcher.replaceFirst(replacement);
                        } else {
                            String[] placeholders = new String[size];
                            for (int i = 0; i < placeholders.length; i++) {
                                String name = queryBuilder.formatParameter(queryParameterIndex + i + 1).getName();
                                placeholders[i] = Matcher.quoteReplacement(name);
                            }
                            String replacement = " IN(" + String.join(",", Arrays.asList(placeholders)) + ")";
                            query = matcher.replaceFirst(replacement);
                        }
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
        final PS ps;
        try {
            ps = statementFunction.create(query);
        } catch (Exception e) {
            throw new DataAccessException("Unable to prepare query [" + query + "]: " + e.getMessage(), e);
        }
        int index = shiftIndex(0);
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
                        Object timestamp = ConversionService.SHARED.convert(dateTimeProvider.getNow(), lastUpdatedType).orElse(null);
                        if (timestamp == null) {
                            throw new IllegalStateException("Unsupported date type: " + lastUpdatedType);
                        }
                        value = timestamp;
                    } else {
                        int j = propertyPath.indexOf('.');
                        if (j > -1) {
                            String subProp = propertyPath.substring(j + 1);
                            value = queryParameters[Integer.valueOf(propertyPath.substring(0, j))];
                            value = BeanWrapper.getWrapper(value).getRequiredProperty(subProp, Argument.OBJECT_ARGUMENT);
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
                setStatementParameter(ps, index++, dataType, null, dialect);
            } else if (value != IGNORED_PARAMETER) {
                if (value instanceof Iterable) {
                    Iterable iter = (Iterable) value;
                    for (Object o : iter) {
                        setStatementParameter(ps, index++, dataType, o, dialect);
                    }
                } else if (value.getClass().isArray()) {
                    if (value instanceof byte[]) {
                        setStatementParameter(ps, index++, dataType, value, dialect);
                    } else {
                        int len = Array.getLength(value);
                        for (int j = 0; j < len; j++) {
                            Object o = Array.get(value, j);
                            setStatementParameter(ps, index++, dataType, o, dialect);
                        }
                    }
                } else {
                    setStatementParameter(ps, index++, dataType, value, dialect);
                }
            }
        }
        return ps;
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
        final Dialect dialect = insert.dialect;

        int index;
        DataType type;
        Object value;
        for (int i = 0; i < parameterBinding.length; i++) {
            index = shiftIndex(i);
            String path = parameterBinding[i];
            RuntimePersistentProperty<T> prop = persistentEntity.getPropertyByName(path);
            if (prop == null) {
                int j = path.indexOf('.');
                if (j < 0) {
                    continue;
                }

                RuntimePersistentProperty embeddedProp = (RuntimePersistentProperty)
                        persistentEntity.getPropertyByPath(path).orElse(null);
                if (embeddedProp == null) {
                    continue;
                }

                // embedded case
                prop = persistentEntity.getPropertyByName(path.substring(0, j));
                if (!(prop instanceof Association)) {
                    continue;
                }

                // association
                Association assoc = (Association) prop;
                if (assoc.getKind() != Relation.Kind.EMBEDDED) {
                    continue;
                }

                value = prop.getProperty().get(entity);

                RuntimePersistentEntity<?> embeddedEntity = entities.get(embeddedProp.getProperty().getType());

                if (embeddedEntity != null) {
                    Object bean = embeddedProp.getProperty().get(value);
                    RuntimePersistentProperty embeddedIdentity = embeddedEntity.getIdentity();

                    type = embeddedIdentity.getDataType();
                    value = embeddedIdentity.getProperty().get(bean);
                } else {
                    type = embeddedProp.getDataType();
                    value = value != null ? embeddedProp.getProperty().get(value) : null;
                }
            } else {
                type = prop.getDataType();
                BeanProperty<T, Object> beanProperty = (BeanProperty<T, Object>) prop.getProperty();
                value = beanProperty.get(entity);

                if (prop instanceof Association) {
                    Association association = (Association) prop;
                    if (association.isForeignKey()) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    RuntimePersistentEntity<Object> associatedEntity = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
                    RuntimePersistentProperty<Object> identity = associatedEntity.getIdentity();
                    if (identity == null) {
                        throw new IllegalArgumentException("Associated entity has not ID: " + associatedEntity.getName());
                    } else {
                        type = identity.getDataType();
                    }
                    BeanProperty<Object, ?> identityProperty = identity.getProperty();
                    value = value != null ? identityProperty.get(value) : null;
                } else if (!prop.isGenerated()) {
                    if (beanProperty.hasStereotype(AutoPopulated.NAME)) {
                        if (beanProperty.hasAnnotation(DateCreated.NAME) ||
                                beanProperty.hasAnnotation(DateUpdated.NAME)) {
                            now = now != null ? now : dateTimeProvider.getNow();

                            value = now;
                            beanProperty.convertAndSet(entity, value);
                        } else if (UUID.class.isAssignableFrom(beanProperty.getType())) {
                            value = UUID.randomUUID();
                            beanProperty.set(entity, value);
                        } else {
                            throw new DataAccessException("Unsupported auto-populated annotation type: " + beanProperty.getAnnotationTypeByStereotype(AutoPopulated.class).orElse(null));
                        }
                    } else {
                        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                            DataSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", value, index);
                        }
                        if (type == DataType.JSON && jsonCodec != null && value != null && !prop.getType().equals(String.class)) {
                            value = new String(jsonCodec.encode(value), StandardCharsets.UTF_8);
                        }
                        if (value != null && dialect.requiresStringUUID(type)) {
                            preparedStatementWriter.setString(
                                    stmt,
                                    index,
                                    value.toString()
                            );
                        } else {
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

            if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                DataSettings.QUERY_LOG.trace("Binding value {} to parameter at position: {}", value, index);
            }
            setStatementParameter(stmt, index, type, value, dialect);
        }
    }

    /**
     * Used to define the index whether it is 1 based (JDBC) or 0 based (R2DBC).
     *
     * @param i The index to shift
     * @return the index
     */
    protected int shiftIndex(int i) {
        return i + 1;
    }

    /**
     * Resolve the INSERT for the given {@link EntityOperation}.
     *
     * @param operation The operation
     * @param <T>       The entity type
     * @return The insert
     */
    @NonNull
    protected final <T> StoredInsert<T> resolveInsert(@NonNull EntityOperation<T> operation) {
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

    /**
     * Build a sort for ID for the given entity.
     *
     * @param persistentEntity The entity
     * @param <T>              The entity type
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
     *
     * @param query   The query
     * @param dialect The dialect
     * @return True if it is
     */
    protected final boolean isSqlServerWithoutOrderBy(String query, Dialect dialect) {
        return dialect == Dialect.SQL_SERVER && !query.contains(AbstractSqlLikeQueryBuilder.ORDER_BY_CLAUSE);
    }

    /**
     * Compute the size of the given object.
     *
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
     *
     * @param preparedStatement The prepared statement
     * @param index             The index
     * @param dataType          The data type
     * @param value             The value
     * @param dialect           The dialect
     */
    protected final void setStatementParameter(PS preparedStatement, int index, DataType dataType, Object value, Dialect dialect) {
        switch (dataType) {
            case JSON:
                if (value != null && jsonCodec != null && !value.getClass().equals(String.class)) {
                    System.out.print(value);
                    value = new String(jsonCodec.encode(value), StandardCharsets.UTF_8);
                }
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
                break;
            default:
                break;
        }

        preparedStatementWriter.setDynamic(
                preparedStatement,
                index,
                dialect.getDataType(dataType),
                value);
    }

    /**
     * Resolves a stored insert for the given entity.
     *
     * @param annotationMetadata The repository annotation metadata
     * @param repositoryType     The repository type
     * @param rootEntity         The root entity
     * @param persistentEntity   The persistent entity
     * @param <T>                The generic type
     * @return The insert
     */
    protected @NonNull
    <T> StoredInsert<T> resolveEntityInsert(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            @NonNull Class<?> rootEntity,
            @NonNull RuntimePersistentEntity<?> persistentEntity) {

        //noinspection unchecked
        return entityInserts.computeIfAbsent(new QueryKey(repositoryType, rootEntity), (queryKey) -> {
            final SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER);
            final QueryResult queryResult = queryBuilder.buildInsert(annotationMetadata, persistentEntity);

            final String sql = queryResult.getQuery();
            final Map<String, String> parameters = queryResult.getParameters();
            Dialect dialect = queryBuilder.getDialect();
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
     *
     * @param annotationMetadata The repository annotation metadata
     * @param repositoryType     The repository type
     * @param rootEntity         The root entity
     * @param persistentEntity   The persistent entity
     * @param <T>                The generic type
     * @return The insert
     */
    protected @NonNull
    <T> StoredInsert<T> resolveEntityUpdate(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            @NonNull Class<?> rootEntity,
            @NonNull RuntimePersistentEntity<?> persistentEntity) {

        final QueryKey key = new QueryKey(repositoryType, rootEntity);
        //noinspection unchecked
        return entityUpdates.computeIfAbsent(key, (queryKey) -> {
            final SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER);

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
            Dialect dialect = queryBuilder.getDialect();
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
     *
     * @param repositoryType   The repository type
     * @param persistentEntity The entity
     * @param association      The association
     * @param <T>              The entity generic type
     * @return The insert statement
     */
    protected <T> String resolveAssociationInsert(
            Class repositoryType,
            RuntimePersistentEntity<T> persistentEntity,
            RuntimeAssociation<T> association) {
        return associationInserts.computeIfAbsent(association, association1 -> {
            final SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER);
            return queryBuilder.buildJoinTableInsert(persistentEntity, association1);
        });
    }

    /**
     * Functional interface used to supply a statement.
     *
     * @param <PS> The prepared statement type
     */
    @FunctionalInterface
    protected interface StatementSupplier<PS> {
        PS create(String ps) throws Exception;
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
         *
         * @param sql              The SQL INSERT
         * @param persistentEntity The entity
         * @param parameterBinding The parameter binding
         * @param supportsBatch    Whether batch insert is supported
         * @param dialect          The dialect
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
        public @NonNull
        Dialect getDialect() {
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
        public RuntimePersistentProperty getIdentity() {
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
