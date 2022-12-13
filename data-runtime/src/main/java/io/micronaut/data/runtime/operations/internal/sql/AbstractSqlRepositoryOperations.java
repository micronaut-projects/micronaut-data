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
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
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
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.internal.BasicStoredQuery;
import io.micronaut.data.runtime.query.internal.QueryResultStoredQuery;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
    protected final Map<Class, SqlQueryBuilder> queryBuilders = new HashMap<>(10);
    protected final Map<Class, String> repositoriesWithHardcodedDataSource = new HashMap<>(10);
    private final Map<QueryKey, SqlStoredQuery> entityInserts = new ConcurrentHashMap<>(10);
    private final Map<QueryKey, SqlStoredQuery> entityUpdates = new ConcurrentHashMap<>(10);
    private final Map<Association, String> associationInserts = new ConcurrentHashMap<>(10);

    /**
     * Default constructor.
     *
     * @param dataSourceName             The datasource name
     * @param columnNameResultSetReader  The column name result reader
     * @param columnIndexResultSetReader The column index result reader
     * @param preparedStatementWriter    The prepared statement writer
     * @param codecs                     The media type codecs
     * @param dateTimeProvider           The date time provider
     * @param runtimeEntityRegistry      The entity registry
     * @param beanContext                The bean context
     * @param conversionService          The conversion service
     * @param attributeConverterRegistry The attribute converter registry
     */
    protected AbstractSqlRepositoryOperations(
            String dataSourceName,
            ResultReader<RS, String> columnNameResultSetReader,
            ResultReader<RS, Integer> columnIndexResultSetReader,
            QueryStatement<PS, Integer> preparedStatementWriter,
            List<MediaTypeCodec> codecs,
            DateTimeProvider<Object> dateTimeProvider,
            RuntimeEntityRegistry runtimeEntityRegistry,
            BeanContext beanContext,
            DataConversionService<?> conversionService,
            AttributeConverterRegistry attributeConverterRegistry) {
        super(codecs, dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.dataSourceName = dataSourceName;
        this.columnNameResultSetReader = columnNameResultSetReader;
        this.columnIndexResultSetReader = columnIndexResultSetReader;
        this.preparedStatementWriter = preparedStatementWriter;
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
     * @param value             The value
     * @param dialect           The dialect
     */
    protected void setStatementParameter(PS preparedStatement, int index, DataType dataType, Object value, Dialect dialect) {
        switch (dataType) {
            case UUID:
                if (value != null && dialect.requiresStringUUID(dataType)) {
                    value = value.toString();
                }
                break;
            case JSON:
                if (value != null && jsonCodec != null && !value.getClass().equals(String.class)) {
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
                    setStatementParameter(preparedStatement, index, idReader.getDataType(), id, dialect);
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
        preparedStatementWriter.setDynamic(preparedStatement, index, dataType, value);
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
        if (storedQuery instanceof SqlStoredQuery) {
            SqlStoredQuery<E, R> sqlStoredQuery = (SqlStoredQuery<E, R>) storedQuery;
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
