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
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.runtime.PropertyAutoPopulator;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.builder.AbstractSqlLikeQueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.*;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.event.EntityEventRegistry;
import io.micronaut.data.runtime.mapper.QueryStatement;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.support.DefaultRuntimeEntityRegistry;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
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
@SuppressWarnings("unchecked")
public abstract class AbstractSqlRepositoryOperations<RS, PS> implements RepositoryOperations {
    protected static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    protected static final SqlQueryBuilder DEFAULT_SQL_BUILDER = new SqlQueryBuilder();
    @SuppressWarnings("WeakerAccess")
    protected final ResultReader<RS, String> columnNameResultSetReader;
    @SuppressWarnings("WeakerAccess")
    protected final ResultReader<RS, Integer> columnIndexResultSetReader;
    @SuppressWarnings("WeakerAccess")
    protected final QueryStatement<PS, Integer> preparedStatementWriter;
    protected final Map<Class, SqlQueryBuilder> queryBuilders = new HashMap<>(10);
    protected final MediaTypeCodec jsonCodec;
    protected final EntityEventListener<Object> entityEventRegistry;
    protected final RuntimeEntityRegistry runtimeEntityRegistry;

    private final Map<QueryKey, SqlOperation> entityInserts = new ConcurrentHashMap<>(10);
    private final Map<QueryKey, SqlOperation> entityUpdates = new ConcurrentHashMap<>(10);
    private final Map<Association, String> associationInserts = new ConcurrentHashMap<>(10);
    private final Map<Class, RuntimePersistentProperty> idReaders = new ConcurrentHashMap<>(10);


    /**
     * Default constructor.
     *
     * @param dataSourceName             The datasource name
     * @param columnNameResultSetReader  The column name result reader
     * @param columnIndexResultSetReader The column index result reader
     * @param preparedStatementWriter    The prepared statement writer
     * @param codecs                     The media type codecs
     * @param beanContext                The bean context
     * @deprecated Use {@link #AbstractSqlRepositoryOperations(String, ResultReader, ResultReader, QueryStatement, List, RuntimeEntityRegistry, BeanContext)}
     */
    @Deprecated
    protected AbstractSqlRepositoryOperations(
            String dataSourceName,
            ResultReader<RS, String> columnNameResultSetReader,
            ResultReader<RS, Integer> columnIndexResultSetReader,
            QueryStatement<PS, Integer> preparedStatementWriter,
            List<MediaTypeCodec> codecs,
            BeanContext beanContext) {
        //noinspection rawtypes
        this(
                dataSourceName,
                columnNameResultSetReader,
                columnIndexResultSetReader,
                preparedStatementWriter,
                codecs,
                new DefaultRuntimeEntityRegistry(
                        new EntityEventRegistry(beanContext),
                        (Collection) beanContext.getBeanRegistrations(PropertyAutoPopulator.class)),
                beanContext
        );
    }

    /**
     * Default constructor.
     *
     * @param dataSourceName             The datasource name
     * @param columnNameResultSetReader  The column name result reader
     * @param columnIndexResultSetReader The column index result reader
     * @param preparedStatementWriter    The prepared statement writer
     * @param codecs                     The media type codecs
     * @param runtimeEntityRegistry      The entity registry
     * @param beanContext                The bean context
     */
    protected AbstractSqlRepositoryOperations(
            String dataSourceName,
            ResultReader<RS, String> columnNameResultSetReader,
            ResultReader<RS, Integer> columnIndexResultSetReader,
            QueryStatement<PS, Integer> preparedStatementWriter,
            List<MediaTypeCodec> codecs,
            RuntimeEntityRegistry runtimeEntityRegistry,
            BeanContext beanContext) {
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.entityEventRegistry = runtimeEntityRegistry.getEntityEventListener();
        this.columnNameResultSetReader = columnNameResultSetReader;
        this.columnIndexResultSetReader = columnIndexResultSetReader;
        this.preparedStatementWriter = preparedStatementWriter;
        this.jsonCodec = resolveJsonCodec(codecs);
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
        return runtimeEntityRegistry.getEntity(type);
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
            boolean isSingleResult) throws Exception {
        Object[] queryParameters = preparedQuery.getParameterArray();
        int[] parameterBinding = preparedQuery.getIndexedParameterBinding();
        DataType[] parameterTypes = preparedQuery.getIndexedParameterTypes();
        String[] indexedParameterPaths = preparedQuery.getIndexedParameterPaths();
        String[] indexedParameterAutoPopulatedPropertyPaths = preparedQuery.getIndexedParameterAutoPopulatedPropertyPaths();
        String[] indexedParameterAutoPopulatedPreviousPropertyPaths = preparedQuery.getIndexedParameterAutoPopulatedPreviousPropertyPaths();
        int[] indexedParameterAutoPopulatedPreviousPropertyIndexes = preparedQuery.getIndexedParameterAutoPopulatedPreviousPropertyIndexes();
        String query = preparedQuery.getQuery();
        SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(preparedQuery.getRepositoryType(), DEFAULT_SQL_BUILDER);
        final Dialect dialect = queryBuilder.getDialect();
        RuntimePersistentEntity<T> persistentEntity = getEntity(preparedQuery.getRootEntity());

        int[] parametersListSizes = null;
        for (int i = 0; i < parameterBinding.length; i++) {
            int parameterIndex = parameterBinding[i];
            DataType dataType = parameterTypes[i];
            if (parameterIndex == -1) {
                continue;
            }
            if (dataType.isArray()) {
                continue;
            }
            Object value = queryParameters[parameterIndex];
            if (value == null || value instanceof byte[]) {
                continue;
            }
            int size = sizeOf(value);
            if (size == 1) {
                continue;
            }
            if (parametersListSizes == null) {
                parametersListSizes = new int[parameterBinding.length];
                Arrays.fill(parametersListSizes, 1);
            }
            parametersListSizes[i] = size;
        }
        if (parametersListSizes != null) {
            String positionalParameterFormat = queryBuilder.positionalParameterFormat();
            Pattern positionalParameterPattern = queryBuilder.positionalParameterPattern();
            String[] queryParametersSplit = positionalParameterPattern.split(query);
            StringBuilder sb = new StringBuilder(queryParametersSplit[0]);
            int inx = 1;
            for (int i = 0; i < parameterBinding.length; i++) {
                int parameterSetSize = parametersListSizes[i];
                sb.append(String.format(positionalParameterFormat, inx));
                for (int sx = 1; sx < parameterSetSize; sx++) {
                    sb.append(",").append(String.format(positionalParameterFormat, inx + sx));
                }
                sb.append(queryParametersSplit[inx++]);
            }
            query = sb.toString();
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
                String propertyPath = indexedParameterPaths[i];
                String autoPopulatedPropertyPath = indexedParameterAutoPopulatedPropertyPaths[i];
                if (autoPopulatedPropertyPath != null) {
                    RuntimePersistentProperty<T> persistentProperty = persistentEntity.getPropertyByName(autoPopulatedPropertyPath);
                    if (persistentProperty == null) {
                        throw new IllegalStateException("Cannot find auto populated property: " + autoPopulatedPropertyPath);
                    }
                    Object previousValue = null;
                    int autoPopulatedPreviousPropertyIndex = indexedParameterAutoPopulatedPreviousPropertyIndexes[i];
                    if (autoPopulatedPreviousPropertyIndex > -1) {
                        previousValue = queryParameters[autoPopulatedPreviousPropertyIndex];
                    } else {
                        String previousValuePath = indexedParameterAutoPopulatedPreviousPropertyPaths[i];
                        if (previousValuePath != null) {
                            previousValue = resolveQueryParameterByPath(query, i, queryParameters, previousValuePath);
                        }
                    }
                    value = runtimeEntityRegistry.autoPopulateRuntimeProperty(persistentProperty, previousValue);
                } else if (propertyPath != null) {
                    value = resolveQueryParameterByPath(query, i, queryParameters, propertyPath);
                }  else {
                    throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
                }
            }

            if (value == null || dataType.isArray() || value instanceof byte[]) {
                setStatementParameter(ps, index++, dataType, value, dialect);
            } else if (value instanceof Iterable) {
                Iterator<?> iterator = ((Iterable<?>) value).iterator();
                if (!iterator.hasNext()) {
                    setStatementParameter(ps, index++, dataType, null, dialect);
                } else {
                    while (iterator.hasNext()) {
                        setStatementParameter(ps, index++, dataType, iterator.next(), dialect);
                    }
                }
            } else if (value.getClass().isArray()) {
                int len = Array.getLength(value);
                if (len == 0) {
                    setStatementParameter(ps, index++, dataType, null, dialect);
                } else {
                    for (int j = 0; j < len; j++) {
                        Object o = Array.get(value, j);
                        setStatementParameter(ps, index++, dataType, o, dialect);
                    }
                }
            } else {
                setStatementParameter(ps, index++, dataType, value, dialect);
            }
        }
        return ps;
    }

    private Object resolveQueryParameterByPath(String query, int i, Object[] queryParameters, String propertyPath) {
        int j = propertyPath.indexOf('.');
        if (j > -1) {
            String[] properties = propertyPath.split("\\.");
            Object value = queryParameters[Integer.parseInt(properties[0])];
            for (int k = 1; k < properties.length && value != null; k++) {
                String property = properties[k];
                value = BeanWrapper.getWrapper(value).getRequiredProperty(property, Argument.OBJECT_ARGUMENT);
            }
            return value;
        } else {
            throw new IllegalStateException("Invalid query [" + query + "]. Unable to establish parameter value for parameter at position: " + (i + 1));
        }
    }

    /**
     * Trigger the pre persist event.
     * @param entity The entity
     * @param pe The persistent entity
     * @param annotationMetadata The annotation metadata
     * @param <T> The generic type
     * @return The entity or null if the operation was vetoed
     */
    @SuppressWarnings("unchecked")
    protected <T> T triggerPrePersist(@NonNull T entity, RuntimePersistentEntity<T> pe, AnnotationMetadata annotationMetadata) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(pe, entity);
        if (entityEventRegistry.prePersist((EntityEventContext<Object>) event)) {
            return event.getEntity();
        }
        return null;
    }

    /**
     * Trigger the post persist event.
     * @param entity The entity
     * @param pe The persistent entity
     * @param annotationMetadata The annotation metadata
     * @param <T> The generic type
     * @return The entity possibly modified
     */
    @SuppressWarnings("unchecked")
    protected <T> T triggerPostPersist(@NonNull T entity, RuntimePersistentEntity<T> pe, AnnotationMetadata annotationMetadata) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(pe, entity);
        entityEventRegistry.postPersist((EntityEventContext<Object>) event);
        return event.getEntity();
    }

    /**
     * Trigger the pre remove event.
     * @param entity The entity
     * @param pe The persistent entity
     * @param annotationMetadata The annotation metadata
     * @param <T> The generic type
     * @return The entity or null if the operation was vetoed
     */
    @SuppressWarnings("unchecked")
    protected <T> T triggerPreRemove(@NonNull T entity, RuntimePersistentEntity<T> pe, AnnotationMetadata annotationMetadata) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(pe, entity);
        if (entityEventRegistry.preRemove((EntityEventContext<Object>) event)) {
            return event.getEntity();
        } else {
            return null;
        }
    }

    /**
     * Trigger the post remove event.
     * @param entity The entity
     * @param pe The persistent entity
     * @param annotationMetadata The annotation metadata
     * @param <T> The generic type
     */
    @SuppressWarnings("unchecked")
    protected <T> void triggerPostRemove(@NonNull T entity, RuntimePersistentEntity<T> pe, AnnotationMetadata annotationMetadata) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(pe, entity);
        entityEventRegistry.postRemove((EntityEventContext<Object>) event);
    }

    /**
     * Trigger the pre update event.
     * @param entity The entity
     * @param pe The persistent entity
     * @param annotationMetadata The annotation metadata
     * @param <T> The generic type
     * @return The entity or null if the operation was vetoed
     */
    @SuppressWarnings("unchecked")
    protected <T> T triggerPreUpdate(@NonNull T entity, RuntimePersistentEntity<T> pe, AnnotationMetadata annotationMetadata) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(pe, entity);
        if (entityEventRegistry.preUpdate((EntityEventContext<Object>) event)) {
            return event.getEntity();
        }
        return null;
    }

    /**
     * Trigger the post update event.
     * @param entity The entity
     * @param pe The persistent entity
     * @param annotationMetadata The annotation metadata
     * @param <T> The generic type
     * @return The entity, possibly modified
     */
    @SuppressWarnings("unchecked")
    protected <T> T triggerPostUpdate(@NonNull T entity, RuntimePersistentEntity<T> pe, AnnotationMetadata annotationMetadata) {
        DefaultEntityEventContext<T> context = new DefaultEntityEventContext<>(pe, entity);
        entityEventRegistry.postUpdate((EntityEventContext<Object>) context);
        return context.getEntity();
    }

    /**
     * Trigger the post load event.
     * @param entity The entity
     * @param pe The persistent entity
     * @param annotationMetadata The annotation metadata
     * @param <T> The generic type
     * @return The entity, possibly modified
     */
    @SuppressWarnings("unchecked")
    protected <T> T triggerPostLoad(@NonNull T entity, RuntimePersistentEntity<T> pe, AnnotationMetadata annotationMetadata) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(pe, entity);
        entityEventRegistry.postLoad((EntityEventContext<Object>) event);
        return event.getEntity();
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
        preparedStatementWriter.setDynamic(
                preparedStatement,
                index,
                dataType,
                value);
    }

    /**
     * Resolves a stored insert for the given entity.
     *
     * @param annotationMetadata The repository annotation metadata
     * @param repositoryType     The repository type
     * @param rootEntity         The root entity
     * @param persistentEntity   The persistent entity
     * @return The insert
     */
    protected @NonNull SqlOperation resolveEntityInsert(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            @NonNull Class<?> rootEntity,
            @NonNull RuntimePersistentEntity<?> persistentEntity) {

        //noinspection unchecked
        return entityInserts.computeIfAbsent(new QueryKey(repositoryType, rootEntity), (queryKey) -> {
            final SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER);
            final QueryResult queryResult = queryBuilder.buildInsert(annotationMetadata, persistentEntity);

            return new SqlOperation(
                    queryBuilder.getDialect(),
                    queryResult.getQuery(),
                    queryResult.getParameterBindings().stream().map(QueryParameterBinding::getPath).toArray(String[]::new),
                    new String[0],
                    false
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
     * @return The insert
     */
    protected @NonNull SqlOperation resolveEntityUpdate(
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

            return new SqlOperation(
                    queryBuilder.getDialect(),
                    queryResult.getQuery(),
                    queryResult.getParameterBindings().stream().map(QueryParameterBinding::getPath).toArray(String[]::new),
                    new String[0],
                    false
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

    private boolean isSupportsBatch(PersistentEntity persistentEntity, Dialect dialect) {
        switch (dialect) {
            case SQL_SERVER:
                return false;
            case ORACLE:
                if (persistentEntity.getIdentity() != null) {
                    // Oracle doesn't support a batch with returning generated ID: "DML Returning cannot be batched"
                    return !persistentEntity.getIdentity().isGenerated();
                }
                return false;
            default:
                return true;
        }
    }

    /**
     * Does supports batch for update queries.
     * @param persistentEntity The persistent entity
     * @param dialect The dialect
     * @return true if supported
     */
    protected boolean isSupportsBatchInsert(PersistentEntity persistentEntity, Dialect dialect) {
        switch (dialect) {
            case SQL_SERVER:
                return false;
            case ORACLE:
                if (persistentEntity.getIdentity() != null) {
                    // Oracle doesn't support a batch with returning generated ID: "DML Returning cannot be batched"
                    return !persistentEntity.getIdentity().isGenerated();
                }
                return false;
            default:
                return true;
        }
    }

    /**
     * Does supports batch for update queries.
     * @param persistentEntity The persistent entity
     * @param dialect The dialect
     * @return true if supported
     */
    protected boolean isSupportsBatchUpdate(PersistentEntity persistentEntity, Dialect dialect) {
        return true;
    }

    /**
     * Does supports batch for delete queries.
     * @param persistentEntity The persistent entity
     * @param dialect The dialect
     * @return true if supported
     */
    protected boolean isSupportsBatchDelete(PersistentEntity persistentEntity, Dialect dialect) {
        return true;
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

    class StoredSqlOperation extends SqlOperation {

        StoredSqlOperation(Dialect dialect, AnnotationMetadata annotationMetadata) {
            super(dialect,
                    annotationMetadata.stringValue(Query.class, "rawQuery")
                            .orElseGet(() -> annotationMetadata.stringValue(Query.class).orElse(null)),
                    annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS),
                    annotationMetadata.stringValues(DataMethod.class, DataMethod.META_MEMBER_PARAMETER_AUTO_POPULATED_PREVIOUS_PROPERTY_PATHS),
                    annotationMetadata.booleanValue(DataMethod.class, DataMethod.META_MEMBER_OPTIMISTIC_LOCK).orElse(false)
            );
        }

    }

    class SqlOperation {

        protected final String[] parameterBindingPaths;
        protected final String[] autoPopulatedPreviousProperties;
        protected final String query;
        protected final Dialect dialect;
        protected final boolean isOptimisticLock;

        SqlOperation(Dialect dialect,
                     String query,
                     String[] parameterBindingPaths,
                     String[] autoPopulatedPreviousProperties,
                     boolean isOptimisticLock) {
            Objects.requireNonNull(query, "Query cannot be null");
            Objects.requireNonNull(dialect, "Dialect cannot be null");
            this.parameterBindingPaths = parameterBindingPaths;
            this.autoPopulatedPreviousProperties = autoPopulatedPreviousProperties;
            this.query = query;
            this.dialect = dialect;
            this.isOptimisticLock = isOptimisticLock;
        }

        public String getQuery() {
            return query;
        }

        public Dialect getDialect() {
            return dialect;
        }

        /**
         * Return true if query contains previous version check.
         * If true and modifying query updates less records than expected {@link io.micronaut.data.exceptions.OptimisticLockException should be thrown.}
         *
         * @return true if the query contains optimistic lock
         */
        public boolean isOptimisticLock() {
            return isOptimisticLock;
        }

        /**
         * Collect auto-populated property values before pre-actions are triggered and property values are modified.
         *
         * @param persistentEntity The persistent entity
         * @param entity The entity instance
         * @param <T> The entity type
         * @return collected values
         */
        public <T> Map<String, Object> collectAutoPopulatedPreviousValues(RuntimePersistentEntity<T> persistentEntity, T entity) {
            if (autoPopulatedPreviousProperties == null || autoPopulatedPreviousProperties.length == 0) {
                return null;
            }
            return Arrays.stream(autoPopulatedPreviousProperties)
                    .filter(StringUtils::isNotEmpty)
                    .map(propertyPath -> {
                        Object value = entity;
                        for (String property : propertyPath.split("\\.")) {
                            if (value == null) {
                                break;
                            }
                            value = BeanWrapper.getWrapper(value).getRequiredProperty(property, Argument.OBJECT_ARGUMENT);
                        }
                        return new AbstractMap.SimpleEntry<>(propertyPath, value);
                    })
                    .filter(e -> e.getValue() != null)
                    .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        }

        public <T> void setParameters(PS stmt, RuntimePersistentEntity<T> persistentEntity, T entity, Map<String, Object> previousValues) {
            for (int i = 0; i < parameterBindingPaths.length; i++) {
                String propertyPath = parameterBindingPaths[i];
                if (StringUtils.isEmpty(propertyPath)) {
                    if (previousValues != null) {
                        String autoPopulatedPreviousProperty = autoPopulatedPreviousProperties[i];
                        Object previousValue = previousValues.get(autoPopulatedPreviousProperty);
                        if (previousValue != null) {
                            PersistentPropertyPath pp = persistentEntity.getPropertyPath(autoPopulatedPreviousProperty);
                            if (pp == null) {
                                throw new IllegalStateException("Unrecognized path: " + autoPopulatedPreviousProperty);
                            }
                            setStatementParameter(stmt, shiftIndex(i), pp.getProperty().getDataType(), previousValue, dialect);
                            continue;
                        }
                    }
                    setStatementParameter(stmt, shiftIndex(i), DataType.ENTITY, entity, dialect);
                    continue;
                }
                setPropertyPathParameter(stmt, shiftIndex(i), persistentEntity, entity, propertyPath);
            }
        }

        public <T> void setPropertyPathParameter(PS stmt, int index, RuntimePersistentEntity<T> persistentEntity, T entity, String propertyStringPath) {
            if (propertyStringPath.startsWith("0.")) {
                propertyStringPath = propertyStringPath.substring(2);
            }
            PersistentPropertyPath propertyPath = persistentEntity.getPropertyPath(propertyStringPath);
            if (propertyPath == null) {
                throw new IllegalStateException("Unrecognized path: " + propertyStringPath);
            }
            Object value = entity;
            for (Association association : propertyPath.getAssociations()) {
                RuntimePersistentProperty<?> property = (RuntimePersistentProperty) association;
                BeanProperty beanProperty = property.getProperty();
                value = beanProperty.get(value);
                if (value == null) {
                    break;
                }
            }
            RuntimePersistentProperty<?> property = (RuntimePersistentProperty<?>) propertyPath.getProperty();
            if (value != null) {
                BeanProperty beanProperty = property.getProperty();
                value = beanProperty.get(value);
            }
            DataType type = property.getDataType();
            if (value == null && type == DataType.ENTITY) {
                RuntimePersistentEntity<?> referencedEntity = getEntity(property.getType());
                RuntimePersistentProperty<?> identity = referencedEntity.getIdentity();
                if (identity == null) {
                    throw new IllegalStateException("Cannot set an entity value without identity: " + referencedEntity);
                }
                type = identity.getDataType();
            }
            setStatementParameter(stmt, index, type, value, dialect);
        }
    }

}
