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
package io.micronaut.data.runtime.operations.internal;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.event.DefaultEntityEventContext;
import io.micronaut.data.runtime.mapper.QueryStatement;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract SQL repository implementation not specifically bound to JDBC.
 *
 * @param <Cnt> The connection type
 * @param <RS>  The result set type
 * @param <PS>  The prepared statement type
 * @param <Exc> The exception type
 * @author graemerocher
 * @author Denis Stepanov
 * @since 1.0.0
 */
@SuppressWarnings("FileLength")
@Internal
public abstract class AbstractSqlRepositoryOperations<Cnt, RS, PS, Exc extends Exception>
        implements ApplicationContextProvider, OpContext<Cnt, PS> {
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
    protected final DateTimeProvider dateTimeProvider;
    protected final RuntimeEntityRegistry runtimeEntityRegistry;
    protected final DataConversionService<?> conversionService;
    protected final AttributeConverterRegistry attributeConverterRegistry;
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
        this.dateTimeProvider = dateTimeProvider;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.entityEventRegistry = runtimeEntityRegistry.getEntityEventListener();
        this.columnNameResultSetReader = columnNameResultSetReader;
        this.columnIndexResultSetReader = columnIndexResultSetReader;
        this.preparedStatementWriter = preparedStatementWriter;
        this.jsonCodec = resolveJsonCodec(codecs);
        this.conversionService = conversionService;
        this.attributeConverterRegistry = attributeConverterRegistry;
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

    @Override
    public ApplicationContext getApplicationContext() {
        return runtimeEntityRegistry.getApplicationContext();
    }

    private MediaTypeCodec resolveJsonCodec(List<MediaTypeCodec> codecs) {
        return CollectionUtils.isNotEmpty(codecs) ? codecs.stream().filter(c -> c.getMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE)).findFirst().orElse(null) : null;
    }

    @NonNull
    public final <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type) {
        return runtimeEntityRegistry.getEntity(type);
    }

    @Override
    public RuntimeEntityRegistry getRuntimeEntityRegistry() {
        return runtimeEntityRegistry;
    }

    /**
     * Prepare a statement for execution.
     *
     * @param connection        The connection
     * @param statementFunction The statement function
     * @param preparedQuery     The prepared query
     * @param isUpdate          Is this an update
     * @param isSingleResult    Is it a single result
     * @param <T>               The query declaring type
     * @param <R>               The query result type
     * @return The prepared statement
     */
    protected <T, R> PS prepareStatement(
            Cnt connection,
            StatementSupplier<PS> statementFunction,
            @NonNull PreparedQuery<T, R> preparedQuery,
            boolean isUpdate,
            boolean isSingleResult) throws Exc {
        SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(preparedQuery.getRepositoryType(), DEFAULT_SQL_BUILDER);
        final Dialect dialect = queryBuilder.getDialect();
        RuntimePersistentEntity<T> persistentEntity = getEntity(preparedQuery.getRootEntity());

        PreparedQuerySqlOperation pqSqlOperation = new PreparedQuerySqlOperation(preparedQuery, isUpdate, isSingleResult, dialect);
        pqSqlOperation.checkForParameterToBeExpanded(persistentEntity, null, queryBuilder);
        if (!isUpdate) {
            pqSqlOperation.attachPageable(preparedQuery.getPageable(), isSingleResult, persistentEntity, queryBuilder);
        }

        String query = pqSqlOperation.getQuery();
        if (QUERY_LOG.isDebugEnabled()) {
            QUERY_LOG.debug("Executing Query: {}", query);
        }
        final PS ps;
        try {
            ps = statementFunction.create(query);
        } catch (Exception e) {
            throw new DataAccessException("Unable to prepare query [" + query + "]: " + e.getMessage(), e);
        }
        pqSqlOperation.setParameters(this, connection, ps, persistentEntity, null, null);

        return ps;
    }

    /**
     * Prepare a statement and run a function.
     *
     * @param connection The connection.
     * @param query      The query
     * @param fn         The function to be run with a statement
     * @throws Exc The exception type
     */
    protected abstract void prepareStatement(Cnt connection, String query, DBOperation1<PS, Exc> fn) throws Exc;

    /**
     * Prepare a statement and run a function.
     *
     * @param connection     The connection
     * @param dialect        The dialect
     * @param identity       The identity property
     * @param hasGeneratedID Is genereted idenntity
     * @param insertSql      Te query
     * @param fn             The function to be run with a statement
     * @throws Exc The exception type
     */
    protected abstract void prepareStatement(Cnt connection, Dialect dialect, PersistentProperty identity, boolean hasGeneratedID, String insertSql, DBOperation1<PS, Exc> fn) throws Exc;

    /**
     * Process after a child element has been cascaded.
     *
     * @param entity       The parent entity.
     * @param associations The association leading to the child
     * @param prevChild    The previous child value
     * @param newChild     The new child value
     * @param <T>          The entity type
     * @return The entity instance
     */
    protected <T> T afterCascadedOne(T entity, List<Association> associations, Object prevChild, Object newChild) {
        RuntimeAssociation<Object> association = (RuntimeAssociation<Object>) associations.iterator().next();
        if (associations.size() == 1) {
            if (association.isForeignKey()) {
                RuntimeAssociation<Object> inverseAssociation = (RuntimeAssociation) association.getInverseSide().orElse(null);
                if (inverseAssociation != null) {
                    //don't cast to BeanProperty<T..> here because its the inverse, so we want to set the entity onto the newChild
                    BeanProperty property = inverseAssociation.getProperty();
                    newChild = setProperty(property, newChild, entity);
                }
            }
            if (prevChild != newChild) {
                entity = setProperty((BeanProperty<T, Object>) association.getProperty(), entity, newChild);
            }
            return entity;
        } else {
            BeanProperty<T, Object> property = (BeanProperty<T, Object>) association.getProperty();
            Object innerEntity = property.get(entity);
            Object newInnerEntity = afterCascadedOne(innerEntity, associations.subList(1, associations.size()), prevChild, newChild);
            if (newInnerEntity != innerEntity) {
                innerEntity = convertAndSetWithValue(property, entity, newInnerEntity);
            }
            return (T) innerEntity;
        }
    }

    /**
     * Process after a children element has been cascaded.
     *
     * @param entity       The parent entity.
     * @param associations The association leading to the child
     * @param prevChildren The previous children value
     * @param newChildren  The new children value
     * @param <T>          The entity type
     * @return The entity instance
     */
    protected <T> T afterCascadedMany(T entity, List<Association> associations, Iterable<Object> prevChildren, List<Object> newChildren) {
        RuntimeAssociation<Object> association = (RuntimeAssociation<Object>) associations.iterator().next();
        if (associations.size() == 1) {
            for (ListIterator<Object> iterator = newChildren.listIterator(); iterator.hasNext(); ) {
                Object c = iterator.next();
                if (association.isForeignKey()) {
                    RuntimeAssociation inverseAssociation = association.getInverseSide().orElse(null);
                    if (inverseAssociation != null) {
                        BeanProperty property = inverseAssociation.getProperty();
                        Object newc = setProperty(property, c, entity);
                        if (c != newc) {
                            iterator.set(newc);
                        }
                    }
                }
            }
            if (prevChildren != newChildren) {
                entity = convertAndSetWithValue((BeanProperty<T, Object>) association.getProperty(), entity, newChildren);
            }
            return entity;
        } else {
            BeanProperty<T, Object> property = (BeanProperty<T, Object>) association.getProperty();
            Object innerEntity = property.get(entity);
            Object newInnerEntity = afterCascadedMany(innerEntity, associations.subList(1, associations.size()), prevChildren, newChildren);
            if (newInnerEntity != innerEntity) {
                innerEntity = convertAndSetWithValue(property, entity, newInnerEntity);
            }
            return (T) innerEntity;
        }
    }

    /**
     * Trigger the post load event.
     *
     * @param entity             The entity
     * @param pe                 The persistent entity
     * @param annotationMetadata The annotation metadata
     * @param <T>                The generic type
     * @return The entity, possibly modified
     */
    @SuppressWarnings("unchecked")
    protected <T> T triggerPostLoad(@NonNull T entity, RuntimePersistentEntity<T> pe, AnnotationMetadata annotationMetadata) {
        final DefaultEntityEventContext<T> event = new DefaultEntityEventContext<>(pe, entity);
        entityEventRegistry.postLoad((EntityEventContext<Object>) event);
        return event.getEntity();
    }

    /**
     * Persist one operation.
     *
     * @param connection         The connection
     * @param annotationMetadata The annotationMetadata
     * @param repositoryType     The repositoryType
     * @param sqlOperation       The sql operation
     * @param associations       The associations
     * @param persisted          Already persisted values
     * @param op                 The operation
     * @param <T>                The entity type
     */
    protected <T> void persistOne(
            Cnt connection,
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            SqlOperation sqlOperation,
            List<Association> associations,
            Set<Object> persisted,
            EntityOperations<T> op) {
        try {
            boolean hasGeneratedID = op.persistentEntity.getIdentity() != null && op.persistentEntity.getIdentity().isGenerated();
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL Insert: {}", sqlOperation.getQuery());
            }
            boolean vetoed = op.triggerPrePersist();
            if (vetoed) {
                return;
            }
            op.cascadePre(Relation.Cascade.PERSIST, connection, sqlOperation.dialect, annotationMetadata, repositoryType, associations, persisted);
            prepareStatement(connection, sqlOperation.getDialect(), op.persistentEntity.getIdentity(), hasGeneratedID, sqlOperation.getQuery(), stmt -> {
                op.setParameters(this, connection, stmt, sqlOperation);
                if (hasGeneratedID) {
                    op.executeUpdateSetGeneratedId(stmt);
                } else {
                    op.executeUpdate(stmt);
                }
            });
            op.triggerPostPersist();
            op.cascadePost(Relation.Cascade.PERSIST, connection, sqlOperation.dialect, annotationMetadata, repositoryType, associations, persisted);
        } catch (Exception e) {
            throw new DataAccessException("SQL Error executing INSERT: " + e.getMessage(), e);
        }
    }

    /**
     * Persist batch operation.
     *
     * @param connection         The connection
     * @param annotationMetadata The annotationMetadata
     * @param repositoryType     The repositoryType
     * @param sqlOperation       The sql operation
     * @param associations       The associations
     * @param persisted          Already persisted values
     * @param op                 The operation
     * @param <T>                The entity type
     */
    protected <T> void persistInBatch(
            Cnt connection,
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            SqlOperation sqlOperation,
            List<Association> associations,
            Set<Object> persisted,
            EntitiesOperations<T> op) {
        boolean hasGeneratedID = op.persistentEntity.getIdentity() != null && op.persistentEntity.getIdentity().isGenerated();
        try {
            boolean allVetoed = op.triggerPrePersist();
            if (allVetoed) {
                return;
            }
            op.cascadePre(Relation.Cascade.PERSIST, connection, sqlOperation.dialect, annotationMetadata, repositoryType, associations, persisted);
            prepareStatement(connection, sqlOperation.getDialect(), op.persistentEntity.getIdentity(), hasGeneratedID, sqlOperation.getQuery(), stmt -> {
                if (QUERY_LOG.isDebugEnabled()) {
                    QUERY_LOG.debug("Executing Batch SQL Insert: {}", sqlOperation.getQuery());
                }
                op.setParameters(this, connection, stmt, sqlOperation);
                if (hasGeneratedID) {
                    op.executeUpdateSetGeneratedId(stmt);
                } else {
                    op.executeUpdate(stmt);
                }
            });
            op.triggerPostPersist();
            op.cascadePost(Relation.Cascade.PERSIST, connection, sqlOperation.dialect, annotationMetadata, repositoryType, associations, persisted);
        } catch (Exception e) {
            throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
        }
    }

    private <X, Y> X setProperty(BeanProperty<X, Y> beanProperty, X x, Y y) {
        if (beanProperty.isReadOnly()) {
            return beanProperty.withValue(x, y);
        }
        beanProperty.set(x, y);
        return x;
    }

    private <B, T> B convertAndSetWithValue(BeanProperty<B, T> beanProperty, B bean, T value) {
        if (beanProperty.isReadOnly()) {
            Argument<T> argument = beanProperty.asArgument();
            final ArgumentConversionContext<T> context = ConversionContext.of(argument);
            Object convertedValue = conversionService.convert(value, context).orElseThrow(() ->
                    new ConversionErrorException(argument, context.getLastError()
                            .orElse(() -> new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + beanProperty.getType())))
            );
            return beanProperty.withValue(bean, (T) convertedValue);
        }
        beanProperty.convertAndSet(bean, value);
        return bean;
    }

    /**
     * Delete one operation.
     *
     * @param connection         The connection
     * @param dialect            The dialect
     * @param annotationMetadata The annotationMetadata
     * @param op                 The operation
     * @param queryBuilder       The queryBuilder
     * @param <T>                The entity type
     */
    protected <T> void deleteOne(Cnt connection, Dialect dialect, AnnotationMetadata annotationMetadata, EntityOperations<T> op, SqlQueryBuilder queryBuilder) {
        StoredSqlOperation sqlOperation = new StoredAnnotationMetadataSqlOperation(dialect, annotationMetadata);
        op.collectAutoPopulatedPreviousValues(sqlOperation);
        boolean vetoed = op.triggerPreRemove();
        if (vetoed) {
            // operation vetoed
            return;
        }
        try {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL DELETE: {}", sqlOperation.getQuery());
            }
            op.checkForParameterToBeExpanded(sqlOperation, queryBuilder);
            prepareStatement(connection, sqlOperation.getQuery(), ps -> {
                op.setParameters(this, connection, ps, sqlOperation);
                op.executeUpdate(ps, (entries, deleted) -> {
                    if (QUERY_LOG.isTraceEnabled()) {
                        QUERY_LOG.trace("Delete operation deleted {} records", deleted);
                    }
                    if (sqlOperation.isOptimisticLock()) {
                        checkOptimisticLocking(entries, deleted);
                    }
                });
            });
            op.triggerPostRemove();
        } catch (OptimisticLockException ex) {
            throw ex;
        } catch (Exception e) {
            throw new DataAccessException("Error executing SQL DELETE: " + e.getMessage(), e);
        }
    }

    /**
     * Delete batch operation.
     *
     * @param connection         The connection
     * @param dialect            The dialect
     * @param annotationMetadata The annotationMetadata
     * @param op                 The operation
     * @param <T>                The entity type
     */
    protected <T> void deleteInBatch(Cnt connection, Dialect dialect, AnnotationMetadata annotationMetadata, EntitiesOperations<T> op) {
        StoredSqlOperation sqlOperation = new StoredAnnotationMetadataSqlOperation(dialect, annotationMetadata);
        op.collectAutoPopulatedPreviousValues(sqlOperation);
        boolean vetoed = op.triggerPreRemove();
        if (vetoed) {
            // operation vetoed
            return;
        }
        try {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Batch SQL DELETE: {}", sqlOperation.getQuery());
            }
            prepareStatement(connection, sqlOperation.getQuery(), ps -> {
                op.setParameters(this, connection, ps, sqlOperation);
                op.executeUpdate(ps, (entries, deleted) -> {
                    if (QUERY_LOG.isTraceEnabled()) {
                        QUERY_LOG.trace("Delete operation deleted {} records", deleted);
                    }
                    if (sqlOperation.isOptimisticLock()) {
                        checkOptimisticLocking(entries, deleted);
                    }
                });
            });
            op.triggerPostRemove();
        } catch (OptimisticLockException ex) {
            throw ex;
        } catch (Exception e) {
            throw new DataAccessException("Error executing SQL DELETE: " + e.getMessage(), e);
        }
    }

    /**
     * Update one operation.
     *
     * @param connection         The connection
     * @param annotationMetadata The annotationMetadata
     * @param repositoryType     The repositoryType
     * @param sqlOperation       The sql operation
     * @param associations       The associations
     * @param persisted          Already persisted values
     * @param op                 The operation
     * @param <T>                The entity type
     */
    protected <T> void updateOne(Cnt connection,
                                 AnnotationMetadata annotationMetadata,
                                 Class<?> repositoryType,
                                 SqlOperation sqlOperation,
                                 List<Association> associations,
                                 Set<Object> persisted,
                                 EntityOperations<T> op) {
        op.collectAutoPopulatedPreviousValues(sqlOperation);
        boolean vetoed = op.triggerPreUpdate();
        if (vetoed) {
            return;
        }
        op.cascadePre(Relation.Cascade.UPDATE, connection, sqlOperation.dialect, annotationMetadata, repositoryType, associations, persisted);
        try {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing SQL UPDATE: {}", sqlOperation.getQuery());
            }
            prepareStatement(connection, sqlOperation.getQuery(), ps -> {
                op.setParameters(this, connection, ps, sqlOperation);
                op.executeUpdate(ps, (entries, rowsUpdated) -> {
                    if (QUERY_LOG.isTraceEnabled()) {
                        QUERY_LOG.trace("Update operation updated {} records", rowsUpdated);
                    }
                    if (sqlOperation.isOptimisticLock()) {
                        checkOptimisticLocking(entries, rowsUpdated);
                    }
                });
            });
            op.triggerPostUpdate();
            op.cascadePost(Relation.Cascade.UPDATE, connection, sqlOperation.dialect, annotationMetadata, repositoryType, associations, persisted);
        } catch (OptimisticLockException ex) {
            throw ex;
        } catch (Exception e) {
            throw new DataAccessException("Error executing SQL UPDATE: " + e.getMessage(), e);
        }
    }

    /**
     * Update batch operation.
     *
     * @param connection         The connection
     * @param annotationMetadata The annotationMetadata
     * @param repositoryType     The repositoryType
     * @param sqlOperation       The sql operation
     * @param associations       The associations
     * @param persisted          Already persisted values
     * @param op                 The operation
     * @param <T>                The entity type
     */
    protected <T> void updateInBatch(Cnt connection,
                                     AnnotationMetadata annotationMetadata,
                                     Class<?> repositoryType,
                                     StoredSqlOperation sqlOperation,
                                     List<Association> associations,
                                     Set<Object> persisted,
                                     EntitiesOperations<T> op) {
        op.collectAutoPopulatedPreviousValues(sqlOperation);
        op.triggerPreUpdate();
        try {
            if (QUERY_LOG.isDebugEnabled()) {
                QUERY_LOG.debug("Executing Batch SQL Update: {}", sqlOperation.getQuery());
            }
            op.cascadePre(Relation.Cascade.UPDATE, connection, sqlOperation.dialect, annotationMetadata, repositoryType, associations, persisted);
            prepareStatement(connection, sqlOperation.getQuery(), ps -> {
                op.setParameters(this, connection, ps, sqlOperation);
                op.executeUpdate(ps, (expected, updated) -> {
                    if (QUERY_LOG.isTraceEnabled()) {
                        QUERY_LOG.trace("Update batch operation updated {} records", updated);
                    }
                    if (sqlOperation.isOptimisticLock()) {
                        checkOptimisticLocking(expected, updated);
                    }
                });
            });
            op.cascadePost(Relation.Cascade.UPDATE, connection, sqlOperation.dialect, annotationMetadata, repositoryType, associations, persisted);
            op.triggerPostUpdate();
        } catch (OptimisticLockException ex) {
            throw ex;
        } catch (Exception e) {
            throw new DataAccessException("Error executing SQL UPDATE: " + e.getMessage(), e);
        }
    }

    /**
     * Used to define the index whether it is 1 based (JDBC) or 0 based (R2DBC).
     *
     * @param i The index to shift
     * @return the index
     */
    public int shiftIndex(int i) {
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
     * Set the parameter value on the given statement.
     *
     * @param preparedStatement The prepared statement
     * @param index             The index
     * @param dataType          The data type
     * @param value             The value
     * @param dialect           The dialect
     */
    public void setStatementParameter(PS preparedStatement, int index, DataType dataType, Object value, Dialect dialect) {
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
    protected @NonNull
    SqlOperation resolveEntityInsert(
            AnnotationMetadata annotationMetadata,
            Class<?> repositoryType,
            @NonNull Class<?> rootEntity,
            @NonNull RuntimePersistentEntity<?> persistentEntity) {

        //noinspection unchecked
        return entityInserts.computeIfAbsent(new QueryKey(repositoryType, rootEntity), (queryKey) -> {
            final SqlQueryBuilder queryBuilder = queryBuilders.getOrDefault(repositoryType, DEFAULT_SQL_BUILDER);
            final QueryResult queryResult = queryBuilder.buildInsert(annotationMetadata, persistentEntity);

            return new StoredSqlOperation(
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

    /**
     * Cascade on the entity instance and collect cascade operations.
     *
     * @param dialect            The dialect
     * @param annotationMetadata The annotationMetadata
     * @param repositoryType     The repositoryType
     * @param fkOnly             Is FK only
     * @param cascadeType        The cascadeType
     * @param ctx                The cascade context
     * @param persistentEntity   The persistent entity
     * @param entity             The entity instance
     * @param cascadeOps         The cascade operations
     * @param <T>                The entity type
     */
    protected <T> void cascade(Dialect dialect, AnnotationMetadata annotationMetadata, Class<?> repositoryType,
                               boolean fkOnly,
                               Relation.Cascade cascadeType,
                               CascadeContext ctx,
                               RuntimePersistentEntity<T> persistentEntity,
                               T entity,
                               List<CascadeOp> cascadeOps) {
        for (RuntimeAssociation<T> association : persistentEntity.getAssociations()) {
            BeanProperty<T, Object> beanProperty = (BeanProperty<T, Object>) association.getProperty();
            Object child = beanProperty.get(entity);
            if (child == null) {
                continue;
            }
            if (association instanceof Embedded) {
                cascade(dialect, annotationMetadata, repositoryType, fkOnly, cascadeType, ctx.embedded(association),
                        (RuntimePersistentEntity) association.getAssociatedEntity(),
                        child,
                        cascadeOps);
                continue;
            }
            if (association.doesCascade(cascadeType) && (fkOnly || !association.isForeignKey())) {
                if (association.getInverseSide().map(assoc -> ctx.rootAssociations.contains(assoc) || ctx.associations.contains(assoc)).orElse(false)) {
                    continue;
                }
                final RuntimePersistentEntity<Object> associatedEntity = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
                switch (association.getKind()) {
                    case ONE_TO_ONE:
                    case MANY_TO_ONE:
                        cascadeOps.add(new CascadeOneOp(dialect, annotationMetadata, repositoryType, ctx.relation(association), cascadeType, associatedEntity, child));
                        continue;
                    case ONE_TO_MANY:
                    case MANY_TO_MANY:
                        final RuntimeAssociation inverse = association.getInverseSide().orElse(null);
                        Iterable<Object> children = (Iterable<Object>) association.getProperty().get(entity);
                        if (!children.iterator().hasNext()) {
                            continue;
                        }
                        if (inverse != null && inverse.getKind() == Relation.Kind.MANY_TO_ONE) {
                            List<Object> entities = new ArrayList<>(CollectionUtils.iterableToList(children));
                            for (ListIterator<Object> iterator = entities.listIterator(); iterator.hasNext(); ) {
                                Object c = iterator.next();
                                final BeanProperty property = inverse.getProperty();
                                c = setProperty(property, c, entity);
                                iterator.set(c);
                            }
                            children = entities;
                        }
                        cascadeOps.add(new CascadeManyOp(dialect, annotationMetadata, repositoryType, ctx.relation(association), cascadeType, associatedEntity, children));
                        continue;
                    default:
                        throw new IllegalArgumentException("Cannot cascade for relation: " + association.getKind());
                }
            }
        }
    }

    /**
     * Persist join table assocation.
     *
     * @param connection     The connection
     * @param repositoryType The repositoryType
     * @param dialect        The dialect
     * @param association    The association
     * @param parent         The parent
     * @param op             The operation
     * @param <T>            The entity type
     */
    protected <T> void persistJoinTableAssociation(Cnt connection,
                                                   Class<?> repositoryType,
                                                   Dialect dialect,
                                                   Association association,
                                                   Object parent,
                                                   BaseOperations<T> op) {
        RuntimePersistentEntity<Object> entity = getEntity((Class<Object>) parent.getClass());
        SqlOperation sqlInsertOperation = resolveSqlInsertAssociation(repositoryType, dialect, (RuntimeAssociation) association, entity, parent);
        try {
            prepareStatement(connection, sqlInsertOperation.getQuery(), ps -> {
                op.setParameters(this, connection, ps, sqlInsertOperation);
                op.executeUpdate(ps);
            });
        } catch (Exception e) {
            throw new DataAccessException("SQL error executing INSERT: " + e.getMessage(), e);
        }
    }

    private <T> SqlOperation resolveSqlInsertAssociation(Class<?> repositoryType, Dialect dialect, RuntimeAssociation<T> association, RuntimePersistentEntity<T> persistentEntity, T entity) {
        String sqlInsert = resolveAssociationInsert(repositoryType, persistentEntity, association);
        return new SqlOperation(sqlInsert, dialect) {

            @Override
            public <T, Cnt, PS> void setParameters(OpContext<Cnt, PS> context, Cnt connection, PS ps, RuntimePersistentEntity<T> pe, T e, Map<String, Object> previousValues) {
                int i = 0;
                for (Map.Entry<PersistentProperty, Object> property : idPropertiesWithValues(persistentEntity.getIdentity(), entity).collect(Collectors.toList())) {
                    Object value = context.convert(connection, property.getValue(), (RuntimePersistentProperty<?>) property.getKey());
                    context.setStatementParameter(
                            ps,
                            shiftIndex(i++),
                            property.getKey().getDataType(),
                            value,
                            dialect);
                }
                for (Map.Entry<PersistentProperty, Object> property : idPropertiesWithValues(pe.getIdentity(), e).collect(Collectors.toList())) {
                    Object value = context.convert(connection, property.getValue(), (RuntimePersistentProperty<?>) property.getKey());
                    context.setStatementParameter(
                            ps,
                            shiftIndex(i++),
                            property.getKey().getDataType(),
                            value,
                            dialect);
                }
            }
        };
    }

    private Stream<Map.Entry<PersistentProperty, Object>> idPropertiesWithValues(PersistentProperty property, Object value) {
        Object propertyValue = ((RuntimePersistentProperty) property).getProperty().get(value);
        if (property instanceof Embedded) {
            Embedded embedded = (Embedded) property;
            PersistentEntity embeddedEntity = embedded.getAssociatedEntity();
            return embeddedEntity.getPersistentProperties()
                    .stream()
                    .flatMap(prop -> idPropertiesWithValues(prop, propertyValue));
        } else if (property instanceof Association) {
            Association association = (Association) property;
            if (association.isForeignKey()) {
                return Stream.empty();
            }
            PersistentEntity associatedEntity = association.getAssociatedEntity();
            PersistentProperty identity = associatedEntity.getIdentity();
            if (identity == null) {
                throw new IllegalStateException("Identity cannot be missing for: " + associatedEntity);
            }
            return idPropertiesWithValues(identity, propertyValue);
        }
        return Stream.of(new AbstractMap.SimpleEntry<>(property, propertyValue));
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
     * Resolves a stored update for the given entity.
     *
     * @param annotationMetadata The repository annotation metadata
     * @param repositoryType     The repository type
     * @param rootEntity         The root entity
     * @param persistentEntity   The persistent entity
     * @return The insert
     */
    protected @NonNull
    SqlOperation resolveEntityUpdate(
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

            return new StoredSqlOperation(
                    queryBuilder.getDialect(),
                    queryResult.getQuery(),
                    queryResult.getParameterBindings().stream().map(QueryParameterBinding::getPath).toArray(String[]::new),
                    new String[0],
                    false
            );
        });
    }

    /**
     * Compare the expected modifications and the received rows count. If not equals throw {@link OptimisticLockException}.
     *
     * @param expected The expected value
     * @param received THe received value
     */
    protected void checkOptimisticLocking(int expected, int received) {
        if (received != expected) {
            throw new OptimisticLockException("Execute update returned unexpected row count. Expected: " + expected + " got: " + received);
        }
    }

    /**
     * Check if joined associated are all single ended (Can produce only one result).
     *
     * @param rootPersistentEntity The root entity
     * @param joinFetchPaths The join paths
     * @return true if there are no "many" joins
     */
    protected boolean isOnlySingleEndedJoins(RuntimePersistentEntity<?> rootPersistentEntity, Set<JoinPath> joinFetchPaths) {
        boolean onlySingleEndedJoins = joinFetchPaths.isEmpty() || joinFetchPaths.stream()
                .flatMap(jp -> {
                    PersistentPropertyPath propertyPath = rootPersistentEntity.getPropertyPath(jp.getPath());
                    if (propertyPath == null) {
                        return Stream.empty();
                    }
                    if (propertyPath.getProperty() instanceof Association) {
                        return Stream.concat(propertyPath.getAssociations().stream(), Stream.of((Association) propertyPath.getProperty()));
                    }
                    return propertyPath.getAssociations().stream();
                })
                .allMatch(association -> association.getKind() == Relation.Kind.EMBEDDED || association.getKind().isSingleEnded());
        return onlySingleEndedJoins;
    }

    private static List<Association> associated(List<Association> associations, Association association) {
        if (associations == null) {
            return Collections.singletonList(association);
        }
        List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
        newAssociations.addAll(associations);
        newAssociations.add(association);
        return newAssociations;
    }

    @Override
    public Object convert(Cnt connection, Object value, RuntimePersistentProperty<?> property) {
        AttributeConverter<Object, Object> converter = property.getConverter();
        if (converter != null) {
            return converter.convertToPersistedValue(value, createTypeConversionContext(connection, property, property.getArgument()));
        }
        return value;
    }

    @Override
    public Object convert(Class<?> converterClass, Cnt connection, Object value, @Nullable Argument<?> argument) {
        if (converterClass == null) {
            return value;
        }
        AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
        ConversionContext conversionContext = createTypeConversionContext(connection, null, argument);
        return converter.convertToPersistedValue(value, conversionContext);
    }

    /**
     * Creates implementation specific conversion context.
     *
     * @param connection The connection
     * @param property   The property
     * @param argument   The argument
     * @return new {@link ConversionContext}
     */
    protected abstract ConversionContext createTypeConversionContext(Cnt connection,
                                                                     @Nullable RuntimePersistentProperty<?> property,
                                                                     @Nullable Argument<?> argument);

    /**
     * Simple function interface without return type.
     *
     * @param <In>  The input type
     * @param <Exc> The exception type
     */
    protected interface DBOperation1<In, Exc extends Exception> {

        void process(In in) throws Exc;

    }

    /**
     * Simple function interface with two inputs and without return type.
     *
     * @param <In1> The input 1 type
     * @param <In2> The input 2 type
     * @param <Exc> The exception type
     */
    protected interface DBOperation2<In1, In2, Exc extends Exception> {

        void process(In1 in1, In2 in2) throws Exc;

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
     * The entity operations container.
     *
     * @param <T> The entity type
     */
    protected abstract class EntityOperations<T> extends BaseOperations<T> {

        /**
         * Create a new instance.
         *
         * @param persistentEntity The RuntimePersistentEntity
         */
        protected EntityOperations(RuntimePersistentEntity<T> persistentEntity) {
            super(persistentEntity);
        }

    }

    /**
     * The entities operations container.
     *
     * @param <T> The entity type
     */
    protected abstract class EntitiesOperations<T> extends BaseOperations<T> {

        /**
         * Create a new instance.
         *
         * @param persistentEntity The RuntimePersistentEntity
         */
        protected EntitiesOperations(RuntimePersistentEntity<T> persistentEntity) {
            super(persistentEntity);
        }

    }

    /**
     * The base entity operations class.
     *
     * @param <T> The entity type
     */
    protected abstract class BaseOperations<T> {

        protected final RuntimePersistentEntity<T> persistentEntity;

        protected BaseOperations(RuntimePersistentEntity<T> persistentEntity) {
            this.persistentEntity = persistentEntity;
        }

        /**
         * Cascade pre operation.
         *
         * @param cascadeType        The cascade type
         * @param cnt                The connection
         * @param dialect            The dialect
         * @param annotationMetadata The annotation metadata
         * @param repositoryType     The repository type
         * @param associations       The associations leading to the entity
         * @param persisted          The set containing previously processed values
         */
        protected abstract void cascadePre(Relation.Cascade cascadeType,
                                           Cnt cnt, Dialect dialect, AnnotationMetadata annotationMetadata, Class<?> repositoryType,
                                           List<Association> associations, Set<Object> persisted);

        /**
         * Cascade post operation.
         *
         * @param cascadeType        The cascade type
         * @param cnt                The connection
         * @param dialect            The dialect
         * @param annotationMetadata The annotation metadata
         * @param repositoryType     The repository type
         * @param associations       The associations leading to the entity
         * @param persisted          The set containing previously processed values
         */
        protected abstract void cascadePost(Relation.Cascade cascadeType,
                                            Cnt cnt, Dialect dialect, AnnotationMetadata annotationMetadata, Class<?> repositoryType,
                                            List<Association> associations, Set<Object> persisted);

        /**
         * Collect auto populated values before pre-triggers modifies them.
         *
         * @param sqlOperation The sql operation
         */
        protected abstract void collectAutoPopulatedPreviousValues(SqlOperation sqlOperation);

        /**
         * Delegate to SQL operation to check if query needs to be changed to allow for expanded parameters.
         *
         * @param sqlOperation The sqlOperation
         * @param queryBuilder The queryBuilder
         */
        protected void checkForParameterToBeExpanded(SqlOperation sqlOperation, SqlQueryBuilder queryBuilder) {
        }

        /**
         * Set sql parameters.
         *
         * @param context      The context
         * @param connection   The connection
         * @param stmt         The statement
         * @param sqlOperation The sql operation
         * @throws Exc The exception type
         */
        protected abstract void setParameters(OpContext<Cnt, PS> context, Cnt connection, PS stmt, SqlOperation sqlOperation) throws Exc;

        /**
         * Execute update and process entities modified and rows executed.
         *
         * @param stmt The statement
         * @param fn   The function
         * @throws Exc The exception type
         */
        protected abstract void executeUpdate(PS stmt, DBOperation2<Integer, Integer, Exc> fn) throws Exc;

        /**
         * Execute update.
         *
         * @param stmt The statement
         * @throws Exc The exception type
         */
        protected abstract void executeUpdate(PS stmt) throws Exc;

        /**
         * Execute update and update generated id.
         *
         * @param stmt The statement
         * @throws Exc The exception type
         */
        protected abstract void executeUpdateSetGeneratedId(PS stmt) throws Exc;

        /**
         * Veto an entity.
         *
         * @param predicate The veto predicate
         */
        protected abstract void veto(Predicate<T> predicate);

        /**
         * Update entity id.
         *
         * @param identity The identity property.
         * @param entity   The entity instance
         * @param id       The id instance
         * @return The entity instance
         */
        protected T updateEntityId(BeanProperty<T, Object> identity, T entity, Object id) {
            if (id == null) {
                return entity;
            }
            if (identity.getType().isInstance(id)) {
                return setProperty(identity, entity, id);
            }
            return convertAndSetWithValue(identity, entity, id);
        }

        /**
         * Trigger the pre persist event.
         *
         * @return true if operation was vetoed
         */
        protected boolean triggerPrePersist() {
            if (!persistentEntity.hasPrePersistEventListeners()) {
                return false;
            }
            return triggerPre(entityEventRegistry::prePersist);
        }

        /**
         * Trigger the pre update event.
         *
         * @return true if operation was vetoed
         */
        protected boolean triggerPreUpdate() {
            if (!persistentEntity.hasPreUpdateEventListeners()) {
                return false;
            }
            return triggerPre(entityEventRegistry::preUpdate);
        }

        /**
         * Trigger the pre remove event.
         *
         * @return true if operation was vetoed
         */
        protected boolean triggerPreRemove() {
            if (!persistentEntity.hasPreRemoveEventListeners()) {
                return false;
            }
            return triggerPre(entityEventRegistry::preRemove);
        }

        /**
         * Trigger the post update event.
         */
        protected void triggerPostUpdate() {
            if (!persistentEntity.hasPostUpdateEventListeners()) {
                return;
            }
            triggerPost(entityEventRegistry::postUpdate);
        }

        /**
         * Trigger the post remove event.
         */
        protected void triggerPostRemove() {
            if (!persistentEntity.hasPostRemoveEventListeners()) {
                return;
            }
            triggerPost(entityEventRegistry::postRemove);
        }

        /**
         * Trigger the post persist event.
         */
        protected void triggerPostPersist() {
            if (!persistentEntity.hasPostPersistEventListeners()) {
                return;
            }
            triggerPost(entityEventRegistry::postPersist);
        }

        /**
         * Trigger pre-actions on {@link EntityEventContext}.
         *
         * @param fn The entity context function
         * @return true if operation was vetoed
         */
        protected abstract boolean triggerPre(Function<EntityEventContext<Object>, Boolean> fn);

        /**
         * Trigger post-actions on {@link EntityEventContext}.
         *
         * @param fn The entity context function
         */
        protected abstract void triggerPost(Consumer<EntityEventContext<Object>> fn);

    }

    /**
     * The base cascade operation.
     */
    @SuppressWarnings("VisibilityModifier")
    protected abstract static class CascadeOp {

        public final Dialect dialect;
        public final AnnotationMetadata annotationMetadata;
        public final Class<?> repositoryType;
        public final CascadeContext ctx;
        public final Relation.Cascade cascadeType;
        public final RuntimePersistentEntity<Object> childPersistentEntity;

        CascadeOp(Dialect dialect, AnnotationMetadata annotationMetadata, Class<?> repositoryType,
                  CascadeContext ctx, Relation.Cascade cascadeType, RuntimePersistentEntity<Object> childPersistentEntity) {
            this.dialect = dialect;
            this.annotationMetadata = annotationMetadata;
            this.repositoryType = repositoryType;
            this.ctx = ctx;
            this.cascadeType = cascadeType;
            this.childPersistentEntity = childPersistentEntity;
        }
    }

    /**
     * The cascade operation of one entity.
     */
    @SuppressWarnings("VisibilityModifier")
    protected static final class CascadeOneOp extends CascadeOp {

        public final Object child;

        CascadeOneOp(Dialect dialect, AnnotationMetadata annotationMetadata, Class<?> repositoryType,
                     CascadeContext ctx, Relation.Cascade cascadeType, RuntimePersistentEntity<Object> childPersistentEntity, Object child) {
            super(dialect, annotationMetadata, repositoryType, ctx, cascadeType, childPersistentEntity);
            this.child = child;
        }
    }

    /**
     * The cascade operation of multiple entities - @Many mappings.
     */
    @SuppressWarnings("VisibilityModifier")
    protected static final class CascadeManyOp extends CascadeOp {

        public final Iterable<Object> children;

        CascadeManyOp(Dialect dialect, AnnotationMetadata annotationMetadata, Class<?> repositoryType,
                      CascadeContext ctx, Relation.Cascade cascadeType, RuntimePersistentEntity<Object> childPersistentEntity,
                      Iterable<Object> children) {
            super(dialect, annotationMetadata, repositoryType, ctx, cascadeType, childPersistentEntity);
            this.children = children;
        }
    }

    /**
     * The cascade context.
     */
    @SuppressWarnings("VisibilityModifier")
    protected static final class CascadeContext {

        /**
         * The associations leading to the parent.
         */
        public final List<Association> rootAssociations;
        /**
         * The parent instance that is being cascaded.
         */
        public final Object parent;
        /**
         * The associations leading to the cascaded instance.
         */
        public final List<Association> associations;

        /**
         * Create a new instance.
         *
         * @param rootAssociations The root associations.
         * @param parent           The parent
         * @param associations     The associations
         */
        CascadeContext(List<Association> rootAssociations, Object parent, List<Association> associations) {
            this.rootAssociations = rootAssociations;
            this.parent = parent;
            this.associations = associations;
        }

        public static CascadeContext of(List<Association> rootAssociations, Object parent) {
            return new CascadeContext(rootAssociations, parent, Collections.emptyList());
        }

        /**
         * Cascade embedded association.
         *
         * @param association The embedded association
         * @return The context
         */
        CascadeContext embedded(Association association) {
            return new CascadeContext(rootAssociations, parent, associated(associations, association));
        }

        /**
         * Cascade relation association.
         *
         * @param association The relation association
         * @return The context
         */
        CascadeContext relation(Association association) {
            return new CascadeContext(rootAssociations, parent, associated(associations, association));
        }

        /**
         * Get last association.
         *
         * @return last association
         */
        public Association getAssociation() {
            return CollectionUtils.last(associations);
        }

    }

}
