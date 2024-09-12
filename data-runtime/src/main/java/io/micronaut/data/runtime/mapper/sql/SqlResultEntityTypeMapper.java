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
package io.micronaut.data.runtime.mapper.sql;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.model.*;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * A {@link io.micronaut.data.runtime.mapper.TypeMapper} that can take a {@link RuntimePersistentEntity} and a {@link ResultReader}
 * and materialize an instance using column naming conventions mapped by the entity.
 *
 * @param <RS> The result set type
 * @param <R>  The result type
 */
@Internal
public final class SqlResultEntityTypeMapper<RS, R> implements SqlTypeMapper<RS, R> {

    private final RuntimePersistentEntity<R> entity;
    private final ResultReader<RS, String> resultReader;
    private final Map<String, JoinPath> fetchJoinPaths;
    private final boolean hasJoins;
    private final String startingPrefix;
    private final SqlJsonColumnReader<RS> jsonColumnReader;
    private final DataConversionService conversionService;
    private final BiFunction<RuntimePersistentEntity<Object>, Object, Object> eventListener;
    private final boolean isDto;
    private boolean callNext = true;

    /**
     * Default constructor.
     *
     * @param prefix            The prefix to startup from.
     * @param entity            The entity
     * @param resultReader      The result reader
     * @param jsonColumnReader  The json column reader
     * @param conversionService The conversion service
     */
    public SqlResultEntityTypeMapper(
            String prefix,
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable SqlJsonColumnReader<RS> jsonColumnReader,
            DataConversionService conversionService) {
        this(entity, resultReader, Collections.emptySet(), prefix, jsonColumnReader, conversionService, null, false);
    }

    /**
     * Constructor used to customize the join paths.
     *
     * @param entity            The entity
     * @param resultReader      The result reader
     * @param joinPaths         The join paths
     * @param jsonColumnReader  The json column reader
     * @param conversionService The conversion service
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths,
            @Nullable SqlJsonColumnReader<RS> jsonColumnReader, DataConversionService conversionService) {
        this(entity, resultReader, joinPaths, null, jsonColumnReader, conversionService, null, false);
    }

    /**
     * Constructor used to customize the join paths.
     *
     * @param entity            The entity
     * @param resultReader      The result reader
     * @param joinPaths         The join paths
     * @param jsonColumnReader  The json column reader
     * @param loadListener      The event listener
     * @param conversionService The conversion service
     * @param isDto             Whether reading/mapping DTO projection
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths,
            @Nullable SqlJsonColumnReader<RS> jsonColumnReader,
            @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener,
            DataConversionService conversionService,
            boolean isDto) {
        this(entity, resultReader, joinPaths, null, jsonColumnReader, conversionService, loadListener, isDto);
    }

    /**
     * Constructor used to customize the join paths.
     *
     * @param entity            The entity
     * @param resultReader      The result reader
     * @param joinPaths         The join paths
     * @param startingPrefix    The starting prefix
     * @param jsonColumnReader  The json column reader
     * @param eventListener     The event listener used for trigger post load if configured
     * @param conversionService The conversion service
     * @param isDto             Whether reading/mapping DTO projection
     */
    private SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths,
            String startingPrefix,
            @Nullable SqlJsonColumnReader<RS> jsonColumnReader,
            DataConversionService conversionService,
            @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> eventListener,
            boolean isDto) {
        this.conversionService = conversionService;
        ArgumentUtils.requireNonNull("entity", entity);
        ArgumentUtils.requireNonNull("resultReader", resultReader);
        this.entity = entity;
        this.jsonColumnReader = jsonColumnReader;
        this.resultReader = resultReader;
        this.eventListener = eventListener;
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            this.hasJoins = true;
            this.fetchJoinPaths = CollectionUtils.newLinkedHashMap(joinPaths.size());
            for (JoinPath joinPath : joinPaths) {
                if (!joinPath.getJoinType().isFetch()) {
                    continue;
                }
                this.fetchJoinPaths.put(joinPath.getPath(), joinPath);
            }
        } else {
            this.fetchJoinPaths = Collections.emptyMap();
            this.hasJoins = false;
        }
        this.startingPrefix = startingPrefix;
        this.isDto = isDto;
    }

    @Override
    public DataConversionService getConversionService() {
        return conversionService;
    }

    /**
     * @return The entity to be materialized
     */
    @NonNull
    public RuntimePersistentEntity<R> getEntity() {
        return entity;
    }

    /**
     * @return The result reader instance.
     */
    @NonNull
    public ResultReader<RS, String> getResultReader() {
        return resultReader;
    }

    @NonNull
    @Override
    public R map(@NonNull RS rs, @NonNull Class<R> type) throws DataAccessException {
        return readEntity(rs);
    }

    /**
     * Read the entity from the result set.
     * @param rs The result set
     * @return The entity
     * @since 4.2.0
     */
    @NonNull
    public R readEntity(@NonNull RS rs) {
        R entityInstance = readEntity(rs, MappingContext.of(entity, startingPrefix), null, null);
        if (entityInstance == null) {
            throw new DataAccessException("Unable to map result to entity of type [" + entity.getIntrospection().getBeanType() + "]. Missing result data.");
        }
        return triggerPostLoad(entity, entityInstance);
    }

    @Nullable
    @Override
    public Object read(@NonNull RS resultSet, @NonNull String name) {
        RuntimePersistentProperty<R> property = entity.getPropertyByName(name);
        if (property == null) {
            throw new DataAccessException("DTO projection defines a property [" + name + "] that doesn't exist on root entity: " + entity.getName());
        }
        DataType dataType = property.getDataType();
        String columnName = property.getPersistedName();
        return resultReader.readDynamic(resultSet, columnName, dataType);
    }

    @Nullable
    @Override
    public Object read(@NonNull RS resultSet, @NonNull Argument<?> argument) {
        String name = argument.getName();
        RuntimePersistentProperty<R> property = entity.getPropertyByName(name);
        DataType dataType;
        String columnName;
        if (property == null) {
            dataType = argument.getAnnotationMetadata()
                    .enumValue(TypeDef.class, "type", DataType.class)
                    .orElseGet(() -> DataType.forType(argument.getType()));
            columnName = argument.getName();
        } else {
            dataType = property.getDataType();
            columnName = property.getPersistedName();
        }
        return resultReader.readDynamic(resultSet, columnName, dataType);
    }

    @Override
    public boolean hasNext(RS resultSet) {
        if (callNext) {
            return resultReader.next(resultSet);
        } else {
            callNext = true;
            return true;
        }
    }

    /**
     * Read one entity with a pushing mapper.
     *
     * @return The pushing mapper
     */
    public PushingMapper<RS, R> readOneMapper() {
        if (hasJoins) {
            return new PushingMapper<>() {

                final MappingContext<R> ctx = MappingContext.of(entity, startingPrefix);
                R entityInstance;

                @Override
                public void processRow(RS row) {
                    if (entityInstance == null) {
                        entityInstance = readEntity(row, ctx, null, null);
                    } else {
                        readChildren(row, entityInstance, null, ctx);
                    }
                }

                @Override
                public R getResult() {
                    if (entityInstance == null) {
                        return null;
                    }
                    if (!fetchJoinPaths.isEmpty()) {
                        return  (R) setChildrenAndTriggerPostLoad(entityInstance, ctx, null);
                    }
                    return triggerPostLoad(entity, entityInstance);
                }
            };
        }
        return new PushingMapper<>() {

            final MappingContext<R> ctx = MappingContext.of(entity, startingPrefix);
            R entityInstance;

            @Override
            public void processRow(RS row) {
                if (entityInstance == null) {
                    entityInstance = readEntity(row, ctx, null, null);
                } else {
                    throw new NonUniqueResultException();
                }
            }

            @Override
            public R getResult() {
                if (entityInstance == null) {
                    return null;
                }
                return triggerPostLoad(entity, entityInstance);
            }
        };
    }

    /**
     * Read multiple entities with a pushing mapper.
     *
     * @return The pushing mapper
     */
    public PushingMapper<RS, List<R>> readManyMapper() {
        if (hasJoins) {
            return new PushingMapper<>() {

                final Map<Object, MappingContext<R>> idEntities = CollectionUtils.newLinkedHashMap(20);
                final List<MappingContext<R>> allProcessed = new ArrayList<>(20);

                @Override
                public void processRow(RS row) {
                    MappingContext<R> ctx = MappingContext.of(entity, startingPrefix);
                    Object id = readEntityId(row, ctx);
                    if (id == null) {
                        throw new IllegalStateException("Entity needs to have an ID when JOINs are used!");
                    } else {
                        MappingContext<R> prevCtx = idEntities.get(id);
                        if (prevCtx != null) {
                            readChildren(row, prevCtx.entity, null, prevCtx);
                        } else {
                            ctx.entity = readEntity(row, ctx, null, id);
                            idEntities.put(id, ctx);
                            allProcessed.add(ctx);
                        }
                    }
                }

                @Override
                public List<R> getResult() {
                    List<R> values = new ArrayList<>(allProcessed.size());
                    boolean hasFetchJoins = !fetchJoinPaths.isEmpty();
                    for (MappingContext<R> ctx : allProcessed) {
                        if (hasFetchJoins) {
                            values.add(
                                (R) setChildrenAndTriggerPostLoad(ctx.entity, ctx, null)
                            );
                        } else {
                            values.add(
                                triggerPostLoad(ctx.persistentEntity, ctx.entity)
                            );
                        }
                    }
                    return values;
                }
            };
        }
        return new PushingMapper<>() {

            final List<R> allProcessed = new ArrayList<>(20);
            final MappingContext<R> ctx = MappingContext.of(entity, startingPrefix);

            @Override
            public void processRow(RS row) {
                allProcessed.add(
                    readEntity(row, ctx, null, null)
                );
            }

            @Override
            public List<R> getResult() {
                for (ListIterator<R> iterator = allProcessed.listIterator(); iterator.hasNext(); ) {
                    R entity = iterator.next();
                    R newEntity = triggerPostLoad(ctx.persistentEntity, entity);
                    if (entity != newEntity) {
                        iterator.set(newEntity);
                    }
                }
                return allProcessed;
            }
        };
    }

    private void readChildren(RS rs, Object instance, Object parent, MappingContext<R> ctx) {
        if (ctx.manyAssociations != null) {
            Object id = readEntityId(rs, ctx);
            if (id != null) {
                MappingContext associatedCtx = ctx.manyAssociations.get(id);
                if (associatedCtx == null) {
                    associatedCtx = ctx.copy();
                    R entity = (R) readEntity(rs, associatedCtx, parent, id);
                    Objects.requireNonNull(id);
                    ctx.associate(associatedCtx, id, entity);
                } else {
                    readChildren(rs, instance, parent, associatedCtx);
                }
            }
            return;
        }
        if (ctx.associations != null) {
            for (Map.Entry<Association, MappingContext> e : ctx.associations.entrySet()) {
                MappingContext associationCtx = e.getValue();
                RuntimeAssociation runtimeAssociation = (RuntimeAssociation) e.getKey();
                Object in = instance == null || !runtimeAssociation.getKind().isSingleEnded() ? null : runtimeAssociation.getProperty().get(instance);
                readChildren(rs, in, instance, associationCtx);
            }
        }
    }

    private Object setChildrenAndTriggerPostLoad(Object instance, MappingContext<?> ctx, Object parent) {
        if (ctx.manyAssociations != null) {
            List<Object> values = new ArrayList<>(ctx.manyAssociations.size());
            for (MappingContext associationCtx : ctx.manyAssociations.values()) {
                values.add(setChildrenAndTriggerPostLoad(associationCtx.entity, associationCtx, parent));
            }
            return values;
        } else if (ctx.associations != null) {
            for (Map.Entry<Association, MappingContext> e : ctx.associations.entrySet()) {
                MappingContext associationCtx = e.getValue();
                RuntimeAssociation<Object> runtimeAssociation = (RuntimeAssociation<Object>) e.getKey();
                BeanProperty<Object, Object> beanProperty = runtimeAssociation.getProperty();
                if (runtimeAssociation.getKind().isSingleEnded() && (associationCtx.manyAssociations == null || associationCtx.manyAssociations.isEmpty())) {
                    Object value = beanProperty.get(instance);
                    Object newValue = setChildrenAndTriggerPostLoad(value, associationCtx, instance);
                    if (newValue != value) {
                        instance = setProperty(beanProperty, instance, newValue);
                    }
                } else {
                    Object newValue = setChildrenAndTriggerPostLoad(null, associationCtx, instance);
                    newValue = resultReader.convertRequired(newValue == null ? new ArrayList<>() : newValue, beanProperty.getType());
                    instance = setProperty(beanProperty, instance, newValue);
                }
            }
        }
        if (instance != null && (ctx.association == null || ctx.jp != null)) {
            if (parent != null && ctx.association != null && ctx.association.isBidirectional()) {
                PersistentAssociationPath inverse = ctx.association.getInversePathSide().orElseThrow(IllegalStateException::new);
                Association association = inverse.getAssociation();
                if (association.getKind().isSingleEnded()) {
                    Object inverseInstance = inverse.getPropertyValue(instance);
                    if (inverseInstance != parent && ((RuntimeAssociation) inverse.getProperty()).getType().isInstance(parent)) {
                        instance = inverse.setPropertyValue(instance, parent);
                    }
                }
            }
            return triggerPostLoad(ctx.persistentEntity, instance);
        }
        return instance;
    }

    private <X, Y> X setProperty(BeanProperty<X, Y> beanProperty, X x, Y y) {
        if (beanProperty.isReadOnly()) {
            return beanProperty.withValue(x, y);
        }
        beanProperty.set(x, y);
        return x;
    }

    @Nullable
    private <K> K readEntity(RS rs, MappingContext<K> ctx, @Nullable Object parent, @Nullable Object resolveId) {
        RuntimePersistentEntity<K> persistentEntity = ctx.persistentEntity;
        BeanIntrospection<K> introspection = persistentEntity.getIntrospection();
        RuntimePersistentProperty<K>[] constructorArguments = persistentEntity.getConstructorArguments();
        try {
            RuntimePersistentProperty<K> identity = persistentEntity.getIdentity();
            final boolean isAssociation = ctx.association != null;
            final boolean isEmbedded = ctx.association instanceof Embedded;
            final boolean nullableEmbedded = isEmbedded && ctx.association.isOptional();

            Object id = resolveId == null ? readEntityId(rs, ctx) : resolveId;
            if (id == null && !isEmbedded && isAssociation) {
                return null;
            }

            K entity;
            if (ArrayUtils.isEmpty(constructorArguments)) {
                entity = introspection.instantiate();
            } else {
                int len = constructorArguments.length;
                Object[] args = new Object[len];
                for (int i = 0; i < len; i++) {
                    RuntimePersistentProperty<K> prop = constructorArguments[i];
                    if (prop != null) {
                        if (prop instanceof RuntimeAssociation<K> entityAssociation) {
                            if (prop instanceof Embedded embedded) {
                                args[i] = readEntity(rs, ctx.embedded(embedded), null, null);
                            } else {
                                final Relation.Kind kind = entityAssociation.getKind();
                                final boolean isInverse = parent != null && isAssociation && ctx.association.getOwner() == entityAssociation.getAssociatedEntity();
                                if (isInverse && kind.isSingleEnded() && mappedByMatchesOrEmpty(ctx.association, prop.getProperty())) {
                                    args[i] = parent;
                                } else {
                                    MappingContext<K> joinCtx = ctx.join(fetchJoinPaths, entityAssociation);
                                    Object resolvedId = null;
                                    if (!entityAssociation.isForeignKey()) {
                                        resolvedId = readEntityId(rs, ctx.path(entityAssociation));
                                    }
                                    if (kind.isSingleEnded()) {
                                        if (joinCtx.jp == null || resolvedId == null && !entityAssociation.isForeignKey()) {
                                            args[i] = buildIdOnlyEntity(rs, ctx.path(entityAssociation), resolvedId);
                                        } else {
                                            args[i] = readEntity(rs, joinCtx, null, resolvedId);
                                        }
                                    } else if (entityAssociation.getProperty().isReadOnly()) {
                                        // For constructor-only properties (records) always set empty collection and replace later
                                        args[i] = resultReader.convertRequired(new ArrayList<>(0), entityAssociation.getProperty().getType());
                                        if (joinCtx.jp != null) {
                                            MappingContext<K> associatedCtx = joinCtx.copy();
                                            if (resolvedId == null) {
                                                resolvedId = readEntityId(rs, associatedCtx);
                                            }
                                            Object associatedEntity = null;
                                            if (resolvedId != null || entityAssociation.isForeignKey()) {
                                                associatedEntity = readEntity(rs, associatedCtx, null, resolvedId);
                                            }
                                            if (associatedEntity != null) {
                                                joinCtx.associate(associatedCtx, resolvedId, associatedEntity);
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Object v = provideConstructorArgumentValue(rs, ctx, prop, identity, id);
                            if (v == null) {
                                if (!prop.isOptional() && !nullableEmbedded) {
                                    AnnotationMetadata entityAnnotationMetadata = ctx.persistentEntity.getAnnotationMetadata();
                                    if (entityAnnotationMetadata.hasAnnotation(Embeddable.class) || entityAnnotationMetadata.hasAnnotation(EmbeddedId.class)) {
                                        return null;
                                    }
                                    throw new DataAccessException("Null value read for non-null constructor argument [" + prop.getName() + "] of type: " + persistentEntity.getName());
                                } else {
                                    args[i] = null;
                                    continue;
                                }
                            }
                            args[i] = convert(prop, v);
                        }
                    } else {
                        throw new DataAccessException("Constructor argument [" + constructorArguments[i].getName() + "] must have an associated getter.");
                    }
                }
                if (nullableEmbedded && args.length > 0 && isAllNulls(args)) {
                    return null;
                } else {
                    entity = introspection.instantiate(args);
                }
            }

            if (id != null) {
                if (identity != null) {
                    BeanProperty<K, Object> idProperty = identity.getProperty();
                    entity = convertAndSetWithValue(entity, identity, idProperty, id);
                } else if (!persistentEntity.getIdentityProperties().isEmpty()) {
                    Map<String, Object> ids = (Map<String, Object>) id;
                    for (RuntimePersistentProperty<K> identityProperty : persistentEntity.getRuntimeIdentityProperties()) {
                        Object anId = ids.get(identityProperty.getName());
                        entity = convertAndSetWithValue(entity, identityProperty, identityProperty.getProperty(), anId);
                    }
                }
            }
            RuntimePersistentProperty<K> version = persistentEntity.getVersion();
            if (version != null) {
                Object v = readProperty(rs, ctx, version);
                if (v != null) {
                    entity = convertAndSetWithValue(entity, version, version.getProperty(), v);
                }
            }
            for (RuntimePersistentProperty<K> rpp : persistentEntity.getPersistentProperties()) {
                if (rpp.isReadOnly()) {
                    continue;
                } else if (rpp.isConstructorArgument()) {
                    if (rpp instanceof RuntimeAssociation<K> a) {
                        final Relation.Kind kind = a.getKind();
                        if (kind.isSingleEnded()) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                BeanProperty<K, Object> property = rpp.getProperty();
                if (rpp instanceof RuntimeAssociation<K> entityAssociation) {
                    if (rpp instanceof Embedded embedded) {
                        Object value = readEntity(rs, ctx.embedded(embedded), parent == null ? entity : parent, null);
                        entity = setProperty(property, entity, value);
                    } else {
                        final boolean isInverse = parent != null && entityAssociation.getKind().isSingleEnded() && isAssociation && ctx.association.getOwner() == entityAssociation.getAssociatedEntity();
                        // Before setting property value, check if mappedBy is not different from the property name
                        if (isInverse && mappedByMatchesOrEmpty(ctx.association, property)) {
                            entity = setProperty(property, entity, parent);
                        } else {
                            MappingContext<K> joinCtx = ctx.join(fetchJoinPaths, entityAssociation);
                            Object associatedId = null;
                            if (!entityAssociation.isForeignKey()) {
                                associatedId = readEntityId(rs, ctx.path(entityAssociation));
                                if (associatedId == null) {
                                    continue;
                                }
                            }
                            if (joinCtx.jp != null) {
                                if (entityAssociation.getKind().isSingleEnded()) {
                                    Object associatedEntity = readEntity(rs, joinCtx, entity, associatedId);
                                    entity = setProperty(property, entity, associatedEntity);
                                } else {
                                    MappingContext<K> associatedCtx = joinCtx.copy();
                                    if (associatedId == null) {
                                        associatedId = readEntityId(rs, associatedCtx);
                                    }
                                    Object associatedEntity = readEntity(rs, associatedCtx, entity, associatedId);
                                    if (associatedEntity != null) {
                                        Objects.requireNonNull(associatedId);
                                        joinCtx.associate(associatedCtx, associatedId, associatedEntity);
                                    } else if (joinCtx.manyAssociations == null) {
                                        joinCtx.manyAssociations = new LinkedHashMap<>();
                                    }
                                }
                            } else if (entityAssociation.getKind().isSingleEnded() && !entityAssociation.isForeignKey()) {
                                Object value = buildIdOnlyEntity(rs, ctx.path(entityAssociation), associatedId);
                                entity = setProperty(property, entity, value);
                            }
                        }
                    }
                } else {
                    Object v = readProperty(rs, ctx, rpp);
                    if (v != null) {
                        entity = convertAndSetWithValue(entity, rpp, property, v);
                    }
                }
            }
            return entity;
        } catch (InstantiationException e) {
            throw new DataAccessException("Error instantiating entity [" + persistentEntity.getName() + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if association mappedBy property is empty or matches with given property name.
     * @param association the association
     * @param property the bean property
     * @return true if mappedBy is not set or else if matches with given property name
     * @param <K> The field type
     */
    private <K> boolean mappedByMatchesOrEmpty(Association association, BeanProperty<K, Object> property) {
        String mappedBy = association.getAnnotationMetadata().stringValue(Relation.class, "mappedBy").orElse(null);
        if (mappedBy == null) {
            // If mappedBy not set then we don't have what to compare and assume association
            // is related to the property being set
            return true;
        }
        return mappedBy.equals(property.getName());
    }

    private <K> Object provideConstructorArgumentValue(@NonNull RS rs,
                                                       @NonNull MappingContext<K> ctx,
                                                       @NonNull RuntimePersistentProperty<K> property,
                                                       @Nullable RuntimePersistentProperty<K> identity,
                                                       @Nullable Object idValue) {
        if (idValue != null) {
            if (identity != null) {
                if (property.equals(identity)) {
                    return idValue;
                }
            } else {
                for (PersistentProperty identityProperty : ctx.persistentEntity.getIdentityProperties()) {
                    if (property.equals(identityProperty)) {
                        return ((Map<String, Object>) idValue).get(identityProperty.getName());
                    }
                }
            }
        }
        return readProperty(rs, ctx, property);
    }

    private boolean isAllNulls(Object[] args) {
        for (Object arg : args) {
            if (arg != null) {
                return false;
            }
        }
        return true;
    }

    private <K> Object readProperty(RS rs, MappingContext<K> ctx, RuntimePersistentProperty<K> prop) {
        String columnName = ctx.namingStrategy.mappedName(ctx.embeddedPath, prop);
        String columnAlias = prop.getAlias();
        if (StringUtils.isNotEmpty(columnAlias)) {
            columnName = columnAlias;
        } else if (ctx.prefix != null && !ctx.prefix.isEmpty()) {
            columnName = ctx.prefix + columnName;
        }
        DataType dataType = prop.getDataType();
        Object result;
        if (dataType == DataType.JSON && jsonColumnReader != null) {
            JsonDataType jsonDataType = prop.getJsonDataType();
            result = jsonColumnReader.readJsonColumn(resultReader, rs, columnName, jsonDataType, prop.getArgument());
        } else {
            result = resultReader.readDynamic(rs, columnName, dataType);
        }
        AttributeConverter<Object, Object> converter = prop.getConverter();
        if (converter != null) {
            return converter.convertToEntityValue(result, ConversionContext.of(prop.getArgument()));
        }
        return result;
    }

    private <K> K triggerPostLoad(RuntimePersistentEntity<?> persistentEntity, K entity) {
        K finalEntity;
        if (eventListener != null && persistentEntity.hasPostLoadEventListeners()) {
            finalEntity = (K) eventListener.apply((RuntimePersistentEntity<Object>) persistentEntity, entity);
        } else {
            finalEntity = entity;
        }
        return finalEntity;
    }

    @Nullable
    private <K> Object readEntityId(RS rs, MappingContext<K> ctx) {
        RuntimePersistentProperty<K> identity = ctx.persistentEntity.getIdentity();
        if (identity == null) {
            // DTO might not have ID mapped, and in this case to maintain relation
            // we set random UUID as id to be able to read and make relation
            if (isDto) {
                return UUID.randomUUID();
            }
        } else {
            if (identity instanceof Embedded embedded) {
                return readEntity(rs, ctx.embedded(embedded), null, null);
            }
            return readProperty(rs, ctx, identity);
        }
        List<RuntimePersistentProperty<K>> identityProperties = ctx.persistentEntity.getRuntimeIdentityProperties();
        if (!identityProperties.isEmpty()) {
            Map<String, Object> ids = new HashMap<>();
            for (RuntimePersistentProperty<K> identityProperty : identityProperties) {
                Object id = readProperty(rs, ctx, identityProperty);
                if (id != null) {
                    ids.put(identityProperty.getName(), id);
                }
            }
            if (ids.isEmpty()) {
                return null;
            } else {
                return ids;
            }
        }
        return null;
    }

    private <K> K convertAndSetWithValue(K entity, RuntimePersistentProperty<?> rpp, BeanProperty<K, Object> property, Object v) {
        return setProperty(property, entity, convert(rpp, v));
    }

    private Object convert(RuntimePersistentProperty<?> rpp, Object v) {
        Class<?> propertyType = rpp.getType();
        if (v instanceof Array array) {
            try {
                v = array.getArray();
            } catch (SQLException e) {
                throw new DataAccessException("Error getting an array value: " + e.getMessage(), e);
            }
        }
        if (propertyType.isInstance(v)) {
            return v;
        }
        return resultReader.convertRequired(v, rpp.getArgument());
    }

    private <K> K buildIdOnlyEntity(RS rs, MappingContext<K> ctx, Object resolvedId) {
        RuntimePersistentProperty<K> identity = ctx.persistentEntity.getIdentity();
        if (identity != null) {
            BeanIntrospection<K> associatedIntrospection = ctx.persistentEntity.getIntrospection();
            Argument<?>[] constructorArgs = associatedIntrospection.getConstructorArguments();
            if (constructorArgs.length == 0) {
                Object associated = associatedIntrospection.instantiate();
                if (resolvedId == null) {
                    resolvedId = readEntityId(rs, ctx);
                }
                BeanWrapper.getWrapper(associated).setProperty(identity.getName(), resolvedId);
                return (K) associated;
            } else {
                if (constructorArgs.length == 1) {
                    Argument<?> arg = constructorArgs[0];
                    if (arg.getName().equals(identity.getName()) && arg.getType() == identity.getType()) {
                        if (resolvedId == null) {
                            resolvedId = readEntityId(rs, ctx);
                        }
                        // instantiate only if id value is resolved
                        if (resolvedId != null) {
                            return associatedIntrospection.instantiate(resultReader.convertRequired(resolvedId, identity.getType()));
                        }
                    }
                }
            }
        }
        return null;
    }

    public RuntimePersistentEntity<R> getPersistentEntity() {
        return entity;
    }

    private static final class MappingContext<E> {

        private final RuntimePersistentEntity<E> rootPersistentEntity;
        private final RuntimePersistentEntity<E> persistentEntity;
        private final NamingStrategy namingStrategy;
        private final String prefix;
        private final JoinPath jp;
        private final List<Association> joinPath;
        private final List<Association> embeddedPath;
        private final Association association;

        private Map<Object, MappingContext> manyAssociations;
        private Map<Association, MappingContext> associations;

        private E entity;

        private MappingContext(RuntimePersistentEntity rootPersistentEntity,
                               RuntimePersistentEntity persistentEntity,
                               NamingStrategy namingStrategy,
                               String prefix,
                               JoinPath jp,
                               List<Association> joinPath,
                               List<Association> embeddedPath,
                               Association association) {
            this.rootPersistentEntity = rootPersistentEntity;
            this.persistentEntity = persistentEntity;
            this.namingStrategy = namingStrategy;
            this.prefix = prefix;
            this.jp = jp;
            this.joinPath = joinPath;
            this.embeddedPath = embeddedPath;
            this.association = association;
        }

        public static <K> MappingContext<K> of(RuntimePersistentEntity<K> persistentEntity, String prefix) {
            return new MappingContext<>(
                    persistentEntity,
                    persistentEntity,
                    persistentEntity.getNamingStrategy(),
                    prefix,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    null);
        }

        public <K> MappingContext<K> embedded(Embedded embedded) {
            if (associations == null) {
                associations = new LinkedHashMap<>();
            }
            return associations.computeIfAbsent(embedded, e -> embeddedAssociation(embedded));
        }

        public <K> MappingContext<K> path(Association association) {
            RuntimePersistentEntity<K> associatedEntity = (RuntimePersistentEntity) association.getAssociatedEntity();
            return new MappingContext<>(
                    rootPersistentEntity,
                    associatedEntity,
                    namingStrategy,
                    prefix,
                    jp,
                    joinPath,
                    associated(embeddedPath, association),
                    association
            );
        }

        public <K> MappingContext<K> join(Map<String, JoinPath> joinPaths, Association association) {
            if (associations == null) {
                associations = new LinkedHashMap<>();
            }
            return associations.computeIfAbsent(association, a -> joinAssociation(joinPaths, association));
        }

        public <K> MappingContext<K> associate(MappingContext<K> ctx, @NonNull Object associationId, @NonNull Object entity) {
            ctx.entity = (K) entity;
            if (manyAssociations == null) {
                manyAssociations = new LinkedHashMap<>();
            }
            manyAssociations.put(associationId, ctx);
            return ctx;
        }

        private <K> MappingContext<K> copy() {
            MappingContext ctx = new MappingContext<>(
                    rootPersistentEntity,
                    persistentEntity,
                    namingStrategy,
                    prefix,
                    jp,
                    joinPath,
                    embeddedPath,
                    association
            );
            return ctx;
        }

        private <K> MappingContext<K> joinAssociation(Map<String, JoinPath> joinPaths, Association association) {
            JoinPath jp = findJoinPath(joinPaths, association);
            RuntimePersistentEntity<K> associatedEntity = (RuntimePersistentEntity<K>) association.getAssociatedEntity();
            return new MappingContext<>(
                    rootPersistentEntity,
                    associatedEntity,
                    associatedEntity.getNamingStrategy(),
                    jp == null ? prefix : jp.getAlias().orElse(prefix),
                    jp,
                    associated(this.joinPath, association),
                    Collections.emptyList(), // Reset path,
                    association
            );
        }

        private <K> MappingContext<K> embeddedAssociation(Embedded embedded) {
            RuntimePersistentEntity<K> associatedEntity = (RuntimePersistentEntity) embedded.getAssociatedEntity();
            return new MappingContext<>(
                    rootPersistentEntity,
                    associatedEntity,
                    associatedEntity.findNamingStrategy().orElse(namingStrategy),
                    prefix,
                    jp,
                    joinPath,
                    associated(embeddedPath, embedded),
                    embedded
            );
        }

        private JoinPath findJoinPath(Map<String, JoinPath> joinPaths, Association association) {
            JoinPath jp = null;
            if (!joinPaths.isEmpty()) {
                String path = asPath(joinPath, embeddedPath, association);
                jp = joinPaths.get(path);
                if (jp == null) {
                    path = asPath(joinPath, association);
                    jp = joinPaths.get(path);
                    if (jp == null) {
                        RuntimePersistentProperty<E> identity = rootPersistentEntity.getIdentity();
                        if (identity instanceof Embedded) {
                            path = identity.getName() + "." + path;
                        }
                        jp = joinPaths.get(path);
                    }
                }
            }
            if (jp == null) {
                return null;
            }
            String alias = jp.getAlias().orElse(null);
            if (alias == null) {
                alias = association.getAliasName();
                if (!embeddedPath.isEmpty()) {
                    StringBuilder sb = prefix == null ? new StringBuilder() : new StringBuilder(prefix);
                    for (Association embedded : embeddedPath) {
                        sb.append(embedded.getName());
                        sb.append('_');
                    }
                    sb.append(alias);
                    alias = sb.toString();
                } else {
                    alias = prefix == null ? alias : prefix + alias;
                }
            }
            return new JoinPath(jp.getPath(), jp.getAssociationPath(), jp.getJoinType(), alias);
        }

        private String asPath(List<Association> joinPath, List<Association> embeddedPath, PersistentProperty property) {
            if (joinPath.isEmpty() && embeddedPath.isEmpty()) {
                return property.getName();
            }
            StringJoiner joiner = new StringJoiner(".");
            for (Association association : joinPath) {
                joiner.add(association.getName());
            }
            for (Association association : embeddedPath) {
                joiner.add(association.getName());
            }
            joiner.add(property.getName());
            return joiner.toString();
        }

        private String asPath(List<Association> associations, PersistentProperty property) {
            if (associations.isEmpty()) {
                return property.getName();
            }
            StringJoiner joiner = new StringJoiner(".");
            for (Association association : associations) {
                joiner.add(association.getName());
            }
            joiner.add(property.getName());
            return joiner.toString();
        }

        private static List<Association> associated(List<Association> associations, Association association) {
            List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
            newAssociations.addAll(associations);
            newAssociations.add(association);
            return newAssociations;
        }

    }

    /**
     * The pushing mapper helper interface.
     *
     * @param <RS> The row type
     * @param <R>  The result type
     */
    public interface PushingMapper<RS, R> {

        /**
         * Process row.
         *
         * @param row The row
         */
        void processRow(@NonNull RS row);

        /**
         * The result created by pushed rows.
         *
         * @return the result
         */
        @Nullable
        R getResult();

    }

}
