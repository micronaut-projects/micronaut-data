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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentEntityUtils;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.QueryResultInfo;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.operations.HintsCapableRepository;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.mapper.QueryStatement;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.sql.JsonQueryResultMapper;
import io.micronaut.data.runtime.mapper.sql.SqlJsonValueMapper;
import io.micronaut.data.runtime.mapper.sql.SqlTypeMapper;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.internal.BasicStoredQuery;
import io.micronaut.data.runtime.query.internal.QueryResultStoredQuery;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.json.JsonMapper;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract SQL repository implementation not specifically bound to JDBC.
 *
 * @param <RS>  The result set type
 * @param <PS>  The prepared statement type
 * @param <Exc> The exception type
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@Internal
public abstract class AbstractSqlRepositoryOperations<RS, PS, Exc extends Exception>
        extends AbstractRepositoryOperations implements ApplicationContextProvider,
        PreparedQueryDecorator,
        MethodContextAwareStoredQueryDecorator,
        HintsCapableRepository {

    protected static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    protected final String dataSourceName;
    @SuppressWarnings("WeakerAccess")
    protected final ResultReader<RS, String> columnNameResultSetReader;
    @SuppressWarnings("WeakerAccess")
    protected final ResultReader<RS, Integer> columnIndexResultSetReader;
    @SuppressWarnings("WeakerAccess")
    protected final QueryStatement<PS, Integer> preparedStatementWriter;
    protected final JsonMapper jsonMapper;
    protected final SqlJsonColumnMapperProvider<RS> sqlJsonColumnMapperProvider;
    protected final Map<Class, SqlQueryBuilder> queryBuilders = new HashMap<>(10);
    protected final Map<Class, String> repositoriesWithHardcodedDataSource = new HashMap<>(10);
    private final Map<QueryKey, SqlStoredQuery> entityInserts = new ConcurrentHashMap<>(10);
    private final Map<QueryKey, SqlStoredQuery> entityUpdates = new ConcurrentHashMap<>(10);
    private final Map<Association, String> associationInserts = new ConcurrentHashMap<>(10);

    /**
     * Default constructor.
     *
     * @param dataSourceName               The datasource name
     * @param columnNameResultSetReader    The column name result reader
     * @param columnIndexResultSetReader   The column index result reader
     * @param preparedStatementWriter      The prepared statement writer
     * @param dateTimeProvider             The date time provider
     * @param runtimeEntityRegistry        The entity registry
     * @param beanContext                  The bean context
     * @param conversionService            The conversion service
     * @param attributeConverterRegistry   The attribute converter registry
     * @param jsonMapper                   The JSON mapper
     * @param sqlJsonColumnMapperProvider  The SQL JSON column mapper provider
     */
    protected AbstractSqlRepositoryOperations(
            String dataSourceName,
            ResultReader<RS, String> columnNameResultSetReader,
            ResultReader<RS, Integer> columnIndexResultSetReader,
            QueryStatement<PS, Integer> preparedStatementWriter,
            DateTimeProvider<Object> dateTimeProvider,
            RuntimeEntityRegistry runtimeEntityRegistry,
            BeanContext beanContext,
            DataConversionService conversionService,
            AttributeConverterRegistry attributeConverterRegistry,
            JsonMapper jsonMapper,
            SqlJsonColumnMapperProvider<RS> sqlJsonColumnMapperProvider) {
        super(dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.dataSourceName = dataSourceName;
        this.columnNameResultSetReader = columnNameResultSetReader;
        this.columnIndexResultSetReader = columnIndexResultSetReader;
        this.preparedStatementWriter = preparedStatementWriter;
        this.jsonMapper = jsonMapper;
        this.sqlJsonColumnMapperProvider = sqlJsonColumnMapperProvider;
        Collection<BeanDefinition<Object>> beanDefinitions = beanContext
                .getBeanDefinitions(Object.class, Qualifiers.byStereotype(Repository.class));
        for (BeanDefinition<Object> beanDefinition : beanDefinitions) {
            String targetDs = beanDefinition.stringValue(Repository.class).orElse(null);
            Class<Object> beanType = beanDefinition.getBeanType();
            if (targetDs == null || targetDs.equalsIgnoreCase(dataSourceName)) {
                SqlQueryBuilder queryBuilder = new SqlQueryBuilder(beanDefinition.getAnnotationMetadata());
                queryBuilders.put(beanType, queryBuilder);
            } else {
                repositoriesWithHardcodedDataSource.put(beanType, targetDs);
            }
        }
    }

    @Override
    public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
        return new DefaultSqlPreparedQuery<>(preparedQuery);
    }

    @Override
    public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
        Class<?> repositoryType = context.getTarget().getClass();
        SqlQueryBuilder queryBuilder = findQueryBuilder(repositoryType);
        RuntimePersistentEntity<E> runtimePersistentEntity = runtimeEntityRegistry.getEntity(storedQuery.getRootEntity());
        return new DefaultSqlStoredQuery<>(storedQuery, runtimePersistentEntity, queryBuilder);
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
    protected <T, R> PS prepareStatement(StatementSupplier<PS> statementFunction,
                                         @NonNull PreparedQuery<T, R> preparedQuery,
                                         boolean isUpdate,
                                         boolean isSingleResult) throws Exc {
        SqlPreparedQuery<T, R> sqlPreparedQuery = getSqlPreparedQuery(preparedQuery);
        sqlPreparedQuery.prepare(null);
        if (!isUpdate) {
            sqlPreparedQuery.attachPageable(preparedQuery.getPageable(), isSingleResult);
        }

        String query = sqlPreparedQuery.getQuery();
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Query: {}", query);
        }
        final PS ps;
        try {
            ps = statementFunction.create(query);
        } catch (Exception e) {
            throw new DataAccessException("Unable to prepare query [" + query + "]: " + e.getMessage(), e);
        }
        return ps;
    }

    /**
     * Set the parameter value on the given statement.
     *
     * @param preparedStatement The prepared statement
     * @param index             The index
     * @param dataType          The data type
     * @param jsonDataType      The JSON representation type if data type is JSON
     * @param value             The value
     * @param storedQuery       The SQL stored query
     */
    protected void setStatementParameter(PS preparedStatement, int index, DataType dataType, JsonDataType jsonDataType, Object value, SqlStoredQuery<?, ?> storedQuery) {
        Dialect dialect = storedQuery.getDialect();
        switch (dataType) {
            case UUID:
                if (value != null && dialect.requiresStringUUID(dataType)) {
                    value = value.toString();
                }
                break;
            case JSON:
                value = getJsonValue(storedQuery, jsonDataType, index, value);
                break;
            case ENTITY:
                if (value != null) {
                    RuntimePersistentProperty<Object> idReader = getIdReader(value);
                    Object id = idReader.getProperty().get(value);
                    if (id == null) {
                        throw new DataAccessException("Supplied entity is a transient instance: " + value);
                    }
                    setStatementParameter(preparedStatement, index, idReader.getDataType(), jsonDataType, id, storedQuery);
                    return;
                }
                break;
            default:
                break;
        }

        dataType = dialect.getDataType(dataType);

        if (QUERY_LOG.isTraceEnabled()) {
            QUERY_LOG.trace("Binding parameter at position {} to value {} with data type: {}", index, value, dataType);
        }

        // We want to avoid potential conversion for JSON because mapper already returned value ready to be set as statement parameter
        if (dataType == DataType.JSON && value != null) {
            preparedStatementWriter.setValue(preparedStatement, index, value);
            return;
        }

        preparedStatementWriter.setDynamic(preparedStatement, index, dataType, value);
    }

    private Object getJsonValue(SqlStoredQuery<?, ?> storedQuery, JsonDataType jsonDataType, int index, Object value) {
        if (value == null || value.getClass().equals(String.class)) {
            return value;
        }
        SqlJsonValueMapper sqlJsonValueMapper = sqlJsonColumnMapperProvider.getJsonValueMapper(storedQuery, jsonDataType, value);
        if (sqlJsonValueMapper == null) {
            // if json mapper is not on the classpath and object needs to use JSON value mapper
            throw new IllegalStateException("For JSON data types support Micronaut JsonMapper needs to be available on the classpath.");
        }
        try {
            return sqlJsonValueMapper.mapValue(value, jsonDataType);
        } catch (IOException e) {
            throw new DataAccessException("Failed setting JSON field parameter at index " + index, e);
        }
    }

    /**
     * Resolves a stored insert for the given entity.
     *
     * @param annotationMetadata The repository annotation metadata
     * @param repositoryType     The repository type
     * @param rootEntity         The root entity
     * @param persistentEntity   The persistent entity
     * @param <E>                The entity type
     * @return The insert
     */
    @NonNull
    protected <E> SqlStoredQuery<E, E> resolveEntityInsert(AnnotationMetadata annotationMetadata,
                                                           Class<?> repositoryType,
                                                           @NonNull Class<E> rootEntity,
                                                           @NonNull RuntimePersistentEntity<E> persistentEntity) {

        //noinspection unchecked
        return entityInserts.computeIfAbsent(new QueryKey(repositoryType, rootEntity), (queryKey) -> {
            final SqlQueryBuilder queryBuilder = findQueryBuilder(repositoryType);
            final QueryResult queryResult = queryBuilder.buildInsert(annotationMetadata, persistentEntity);

            return new DefaultSqlStoredQuery<>(QueryResultStoredQuery.single(DataMethod.OperationType.INSERT, "Custom insert", AnnotationMetadata.EMPTY_METADATA, queryResult, rootEntity), persistentEntity, queryBuilder);
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
    protected <T> String resolveAssociationInsert(Class repositoryType,
                                                  RuntimePersistentEntity<T> persistentEntity,
                                                  RuntimeAssociation<T> association) {
        return associationInserts.computeIfAbsent(association, association1 -> {
            final SqlQueryBuilder queryBuilder = findQueryBuilder(repositoryType);
            return queryBuilder.buildJoinTableInsert(persistentEntity, association1);
        });
    }

    /**
     * Resolves a stored update for the given entity.
     *
     * @param annotationMetadata The repository annotation metadata
     * @param repositoryType     The repository type
     * @param rootEntity         The root entity
     * @param persistentEntity   The persistent entity
     * @param <E>                The entity type
     * @return The insert
     */
    @NonNull
    protected <E> SqlStoredQuery<E, E> resolveEntityUpdate(AnnotationMetadata annotationMetadata,
                                                           Class<?> repositoryType,
                                                           @NonNull Class<E> rootEntity,
                                                           @NonNull RuntimePersistentEntity<E> persistentEntity) {

        final QueryKey key = new QueryKey(repositoryType, rootEntity);
        //noinspection unchecked
        return entityUpdates.computeIfAbsent(key, (queryKey) -> {
            final SqlQueryBuilder queryBuilder = findQueryBuilder(repositoryType);

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
            final QueryResult queryResult = queryBuilder.buildUpdate(annotationMetadata, queryModel, updateProperties);
            return new DefaultSqlStoredQuery<>(QueryResultStoredQuery.single(DataMethod.OperationType.UPDATE, "Custom update", AnnotationMetadata.EMPTY_METADATA, queryResult, rootEntity), persistentEntity, queryBuilder);
        });
    }

    /**
     * Resolve SQL insert association operation.
     *
     * @param repositoryType   The repository type
     * @param association      The association
     * @param persistentEntity The persistent entity
     * @param entity           The entity
     * @param <T>              The entity type
     * @return The operation
     */
    protected <T> SqlStoredQuery<T, ?> resolveSqlInsertAssociation(Class<?> repositoryType, RuntimeAssociation<T> association, RuntimePersistentEntity<T> persistentEntity, T entity) {
        String sqlInsert = resolveAssociationInsert(repositoryType, persistentEntity, association);
        final SqlQueryBuilder queryBuilder = findQueryBuilder(repositoryType);
        List<QueryParameterBinding> parameters = new ArrayList<>();
        for (Map.Entry<PersistentProperty, Object> property : idPropertiesWithValues(persistentEntity.getIdentity(), entity).collect(Collectors.toList())) {
            parameters.add(new QueryParameterBinding() {

                @Override
                public String getName() {
                    return property.getKey().getName();
                }

                @Override
                public DataType getDataType() {
                    return property.getKey().getDataType();
                }

                @Override
                public JsonDataType getJsonDataType() {
                    return property.getKey().getJsonDataType();
                }

                @Override
                public Object getValue() {
                    return property.getValue();
                }
            });
        }
        for (PersistentPropertyPath pp : idProperties(association.getAssociatedEntity().getIdentity()).collect(Collectors.toList())) {
            parameters.add(new QueryParameterBinding() {

                @Override
                public String getName() {
                    return pp.getProperty().getName();
                }

                @Override
                public DataType getDataType() {
                    return pp.getProperty().getDataType();
                }

                @Override
                public JsonDataType getJsonDataType() {
                    return pp.getProperty().getJsonDataType();
                }

                @Override
                public String[] getPropertyPath() {
                    return pp.getArrayPath();
                }
            });
        }

        RuntimePersistentEntity associatedEntity = association.getAssociatedEntity();
        return new DefaultSqlStoredQuery<>(new BasicStoredQuery<>(sqlInsert, new String[0], parameters, persistentEntity.getIntrospection().getBeanType(), Object.class), associatedEntity, queryBuilder);
    }

    private SqlQueryBuilder findQueryBuilder(Class<?> repositoryType) {
        SqlQueryBuilder queryBuilder = queryBuilders.get(repositoryType);
        if (queryBuilder != null) {
            return queryBuilder;
        }
        String hardcodedDatasource = repositoriesWithHardcodedDataSource.get(repositoryType);
        if (hardcodedDatasource != null) {
            throw new IllegalStateException("Repository [" + repositoryType + "] requires datasource: [" + hardcodedDatasource + "] but this repository operations uses: [" + dataSourceName + "]");
        }
        throw new IllegalStateException("Cannot find a query builder for repository: [" + repositoryType + "]");
    }

    private Stream<PersistentPropertyPath> idProperties(PersistentProperty property) {
        List<PersistentPropertyPath> paths = new ArrayList<>();
        PersistentEntityUtils.traversePersistentProperties(property, (associations, persistentProperty) -> {
            paths.add(new PersistentPropertyPath(associations, property));
        });
        return paths.stream();
    }

    private Stream<Map.Entry<PersistentProperty, Object>> idPropertiesWithValues(PersistentProperty property, Object value) {
        List<Map.Entry<PersistentProperty, Object>> values = new ArrayList<>();
        PersistentEntityUtils.traversePersistentProperties(property, (associations, persistentProperty) -> {
            values.add(new AbstractMap.SimpleEntry<>(persistentProperty, new PersistentPropertyPath(associations, property).getPropertyValue(value)));
        });
        return values.stream();
    }

    protected final <E, R> SqlPreparedQuery<E, R> getSqlPreparedQuery(PreparedQuery<E, R> preparedQuery) {
        if (preparedQuery instanceof SqlPreparedQuery) {
            return (SqlPreparedQuery<E, R>) preparedQuery;
        }
        throw new IllegalStateException("Expected for prepared query to be of type: SqlPreparedQuery got: " + preparedQuery.getClass().getName());
    }

    protected final <E, R> SqlStoredQuery<E, R> getSqlStoredQuery(StoredQuery<E, R> storedQuery) {
        if (storedQuery instanceof SqlStoredQuery<E, R> sqlStoredQuery) {
            if (sqlStoredQuery.isExpandableQuery() && !(sqlStoredQuery instanceof SqlPreparedQuery)) {
                return new DefaultSqlPreparedQuery<>(sqlStoredQuery);
            }
            return sqlStoredQuery;
        }
        throw new IllegalStateException("Expected for prepared query to be of type: SqlStoredQuery got: " + storedQuery.getClass().getName());
    }

    /**
     * Does supports batch for update queries.
     *
     * @param persistentEntity The persistent entity
     * @param dialect          The dialect
     * @return true if supported
     */
    protected boolean isSupportsBatchInsert(PersistentEntity persistentEntity, Dialect dialect) {
        switch (dialect) {
            case SQL_SERVER:
                return false;
            case MYSQL:
            case ORACLE:
                if (persistentEntity.getIdentity() != null) {
                    // Oracle and MySql doesn't support a batch with returning generated ID: "DML Returning cannot be batched"
                    return !persistentEntity.getIdentity().isGenerated();
                }
                return false;
            default:
                return true;
        }
    }

    /**
     * Does supports batch for update queries.
     *
     * @param persistentEntity The persistent entity
     * @param dialect          The dialect
     * @return true if supported
     */
    protected boolean isSupportsBatchUpdate(PersistentEntity persistentEntity, Dialect dialect) {
        return true;
    }

    /**
     * Does supports batch for delete queries.
     *
     * @param persistentEntity The persistent entity
     * @param dialect          The dialect
     * @return true if supported
     */
    protected boolean isSupportsBatchDelete(PersistentEntity persistentEntity, Dialect dialect) {
        return true;
    }

    /**
     * Creates {@link SqlTypeMapper} for reading results from single column into an entity. For now, we support reading from JSON column,
     * however in support we might add XML support etc.
     *
     * @param sqlPreparedQuery the SQL prepared query
     * @param columnName the column name where we are reading from
     * @param jsonDataType the JSON representation type
     * @param resultSetType resultSetType the result set type (different for R2dbc and Jdbc)
     * @param persistentEntity the persistent entity
     * @param loadListener the load listener if needed after entity loaded
     * @return the {@link SqlTypeMapper} able to decode from column value into given type
     * @param <T> the entity type
     * @param <R> the result type
     */
    protected final <T, R> SqlTypeMapper<RS, R> createQueryResultMapper(SqlPreparedQuery<?, ?> sqlPreparedQuery, String columnName, JsonDataType jsonDataType, Class<RS> resultSetType,
                                                                        RuntimePersistentEntity<T> persistentEntity, BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener) {
        QueryResultInfo queryResultInfo = sqlPreparedQuery.getQueryResultInfo();
        if (queryResultInfo != null && queryResultInfo.getType() != io.micronaut.data.annotation.QueryResult.Type.JSON) {
            throw new IllegalStateException("Unexpected query result type: " + queryResultInfo.getType());
        }
        return createJsonQueryResultMapper(sqlPreparedQuery, columnName, jsonDataType, resultSetType, persistentEntity, loadListener);
    }

    /**
     * Reads an object from the result set and given column.
     *
     * @param sqlPreparedQuery the SQL prepared query
     * @param rs the result set
     * @param columnName the column name where we are reading from
     * @param jsonDataType the JSON representation type
     * @param persistentEntity the persistent entity
     * @param resultType the result type
     * @param resultSetType the result set type
     * @param loadListener the load listener if needed after entity loaded
     * @return an object read from the result set column
     * @param <R> the result type
     * @param <T> the entity type
     */
    protected final <R, T> R mapQueryColumnResult(SqlPreparedQuery<?, ?> sqlPreparedQuery, RS rs, String columnName, JsonDataType jsonDataType,
                                          RuntimePersistentEntity<T> persistentEntity, Class<R> resultType, Class<RS> resultSetType,
                                          BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener) {
        SqlTypeMapper<RS, R> mapper = createQueryResultMapper(sqlPreparedQuery, columnName, jsonDataType, resultSetType, persistentEntity, loadListener);
        return mapper.map(rs, resultType);
    }

    /**
     * Handles SQL exception, used in context of update but could be used elsewhere.
     * It can throw custom exception based on the {@link SQLException}.
     *
     * @param sqlException the SQL exception
     * @param dialect the SQL dialect
     * @return custom exception based on {@link SQLException} that was thrown or that same
     * exception if nothing specific was about it
     */
    protected static Throwable handleSqlException(SQLException sqlException, Dialect dialect) {
        if (dialect == Dialect.ORACLE) {
            return OracleSqlExceptionHandler.handleSqlException(sqlException);
        }
        return sqlException;
    }

    /**
     * Return an indicator telling whether prepared query result produces JSON result.
     *
     * @param preparedQuery the prepared query
     * @param queryResultInfo the query result info, if not null will hold info about result type
     * @return true if result is JSON
     */
    protected boolean isJsonResult(PreparedQuery<?, ?> preparedQuery, QueryResultInfo queryResultInfo) {
        if (preparedQuery.isCount()) {
            return false;
        }
        DataType resultDataType = preparedQuery.getResultDataType();
        if (resultDataType == DataType.JSON || (resultDataType == DataType.ENTITY && getEntity(preparedQuery.getResultType()).isJsonView())) {
            return true;
        }
        return queryResultInfo != null && queryResultInfo.getType() == io.micronaut.data.annotation.QueryResult.Type.JSON;
    }

    /**
     * Gets column name for JSON result. If {@link io.micronaut.data.annotation.QueryResult} annotation is present,
     * takes column value from there, otherwise defaults to 'DATA' column name.
     *
     * @param queryResultInfo the query result info from the {@link io.micronaut.data.annotation.QueryResult} annotation, null if annotation not present
     * @return the JSON column name
     */
    protected String getJsonColumn(QueryResultInfo queryResultInfo) {
        if (queryResultInfo != null) {
            return queryResultInfo.getColumnName();
        }
        return io.micronaut.data.annotation.QueryResult.DEFAULT_COLUMN;
    }

    /**
     * Gets JSON data type for JSON result. If {@link io.micronaut.data.annotation.QueryResult} annotation is present,
     * takes data type value from there, otherwise defaults to {@link JsonDataType#DEFAULT}.
     *
     * @param queryResultInfo the query result info from the {@link io.micronaut.data.annotation.QueryResult} annotation, null if annotation not present
     * @return the JSON data type
     */
    protected JsonDataType getJsonDataType(QueryResultInfo queryResultInfo) {
        if (queryResultInfo != null) {
            return queryResultInfo.getJsonDataType();
        }
        return JsonDataType.DEFAULT;
    }

    /**
     * Creates {@link JsonQueryResultMapper} for JSON deserialization.
     *
     * @param sqlPreparedQuery the SQL prepared query
     * @param columnName the column name where query result is stored
     * @param jsonDataType the json representation type
     * @param resultSetType the result set type
     * @param persistentEntity the persistent entity
     * @param loadListener the load listener if needed after entity loaded
     * @return the {@link JsonQueryResultMapper}
     * @param <T> the entity type
     */
    private <T, R> JsonQueryResultMapper<T, RS, R> createJsonQueryResultMapper(SqlPreparedQuery<?, ?> sqlPreparedQuery, String columnName, JsonDataType jsonDataType, Class<RS> resultSetType,
                                                                               RuntimePersistentEntity<T> persistentEntity, BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener) {
        return new JsonQueryResultMapper<>(columnName, jsonDataType, persistentEntity, columnNameResultSetReader,
            sqlJsonColumnMapperProvider.getJsonColumnReader(sqlPreparedQuery, resultSetType), loadListener);
    }

    /**
     * Handles {@link SQLException} for Oracle update commands. Can add more logic if needed, but this
     * now handles only optimistic locking exception for given error code.
     */
    private static final class OracleSqlExceptionHandler {
        private static final int JSON_VIEW_ETAG_NOT_MATCHING_ERROR = 42699;

        /**
         * Handles SQL exception for Oracle dialect, used in context of update but could be used elsewhere.
         * It can throw custom exception based on the {@link SQLException}.
         * Basically throws {@link OptimisticLockException} if error thrown is matching expected error code
         * that is used to represent ETAG not matching when updating Json View.
         *
         * @param sqlException the SQL exception
         * @return custom exception based on {@link SQLException} that was thrown or that same
         * exception if nothing specific was about it
         */
        static Throwable handleSqlException(SQLException sqlException) {
            if (sqlException.getErrorCode() == JSON_VIEW_ETAG_NOT_MATCHING_ERROR) {
                return new OptimisticLockException("ETAG did not match when updating record: " + sqlException.getMessage(), sqlException);
            }
            return sqlException;
        }
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
     * Functional interface used to supply a statement.
     *
     * @param <PS> The prepared statement type
     */
    @FunctionalInterface
    protected interface StatementSupplier<PS> {
        PS create(String ps) throws Exception;
    }
}
