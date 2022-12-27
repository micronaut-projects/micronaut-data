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
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentAssociationPath;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.http.codec.MediaTypeCodec;

import javax.validation.constraints.NotNull;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiFunction;

/**
 * A {@link io.micronaut.data.runtime.mapper.TypeMapper} that can take a {@link RuntimePersistentEntity} and a {@link ResultReader} and materialize an instance using
 * using column naming conventions mapped by the entity.
 *
 * @param <RS> The result set type
 * @param <R>  The result type
 */
@Internal
public final class SqlResultEntityTypeMapper<RS, R> implements SqlTypeMapper<RS, R> {

    private final RuntimePersistentEntity<R> entity;
    private final ResultReader<RS, String> resultReader;
    private final Map<String, JoinPath> joinPaths;
    private final String startingPrefix;
    private final MediaTypeCodec jsonCodec;
    private final DataConversionService conversionService;
    private final BiFunction<RuntimePersistentEntity<Object>, Object, Object> eventListener;
    private boolean callNext = true;

    /**
     * Default constructor.
     *
     * @param prefix            The prefix to startup from.
     * @param entity            The entity
     * @param resultReader      The result reader
     * @param jsonCodec         The JSON codec
     * @param conversionService The conversion service
     */
    public SqlResultEntityTypeMapper(
            String prefix,
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable MediaTypeCodec jsonCodec, DataConversionService conversionService) {
        this(entity, resultReader, Collections.emptySet(), prefix, jsonCodec, conversionService, null);
    }

    /**
     * Constructor used to customize the join paths.
     *
     * @param entity            The entity
     * @param resultReader      The result reader
     * @param joinPaths         The join paths
     * @param jsonCodec         The JSON codec
     * @param conversionService The conversion service
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths,
            @Nullable MediaTypeCodec jsonCodec, DataConversionService conversionService) {
        this(entity, resultReader, joinPaths, null, jsonCodec, conversionService, null);
    }

    /**
     * Constructor used to customize the join paths.
     *
     * @param entity            The entity
     * @param resultReader      The result reader
     * @param joinPaths         The join paths
     * @param jsonCodec         The JSON codec
     * @param loadListener      The event listener
     * @param conversionService The conversion service
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths,
            @Nullable MediaTypeCodec jsonCodec,
            @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> loadListener, DataConversionService conversionService) {
        this(entity, resultReader, joinPaths, null, jsonCodec, conversionService, loadListener);
    }

    /**
     * Constructor used to customize the join paths.
     *
     * @param entity            The entity
     * @param resultReader      The result reader
     * @param joinPaths         The join paths
     * @param conversionService The conversion service
     */
    private SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths,
            String startingPrefix,
            @Nullable MediaTypeCodec jsonCodec,
            DataConversionService conversionService, @Nullable BiFunction<RuntimePersistentEntity<Object>, Object, Object> eventListener) {
        this.conversionService = conversionService;
        ArgumentUtils.requireNonNull("entity", entity);
        ArgumentUtils.requireNonNull("resultReader", resultReader);
        this.entity = entity;
        this.jsonCodec = jsonCodec;
        this.resultReader = resultReader;
        this.eventListener = eventListener;
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            this.joinPaths = new HashMap<>(joinPaths.size());
            for (JoinPath joinPath : joinPaths) {
                this.joinPaths.put(joinPath.getPath(), joinPath);
            }
        } else {
            this.joinPaths = Collections.emptyMap();
        }
        this.startingPrefix = startingPrefix;
    }

    @Override
    public DataConversionService getConversionService() {
        return conversionService;
    }

    /**
     * @return The entity to be materialized
     */
    public @NonNull
    RuntimePersistentEntity<R> getEntity() {
        return entity;
    }

    /**
     * @return The result reader instance.
     */
    public @NonNull
    ResultReader<RS, String> getResultReader() {
        return resultReader;
    }

    @NonNull
    @Override
    public R map(@NonNull RS rs, @NonNull Class<R> type) throws DataAccessException {
        R entityInstance = readEntity(rs, MappingContext.of(entity, startingPrefix), null, null);
        if (entityInstance == null) {
            throw new DataAccessException("Unable to map result to entity of type [" + type.getName() + "]. Missing result data.");
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
        RuntimePersistentProperty<R> property = entity.getPropertyByName(argument.getName());
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
            try {
                return true;
            } finally {
                callNext = true;
            }
        }
    }

    /**
     * Read one entity with a pushing mapper.
     *
     * @return The pushing mapper
     */
    public PushingMapper<RS, R> readOneWithJoins() {
        return new PushingMapper<RS, R>() {

            final MappingContext<R> ctx = MappingContext.of(entity, startingPrefix);
            R entityInstance;

            @Override
            public void processRow(RS row) {
                if (entityInstance == null) {
                    Object id = readEntityId(row, ctx);
                    entityInstance = readEntity(row, ctx, null, id);
                } else {
                    readChildren(row, entityInstance, null, ctx);
                }
            }

            @Override
            public R getResult() {
                if (entityInstance == null) {
                    return null;
                }
                if (!joinPaths.isEmpty()) {
                    entityInstance = (R) setChildrenAndTriggerPostLoad(entityInstance, ctx, null);
                } else {
                    return triggerPostLoad(entity, entityInstance);
                }
                return entityInstance;
            }
        };
    }

    /**
     * Read multiple entities with a pushing mapper.
     *
     * @return The pushing mapper
     */
    public PushingMapper<RS, List<R>> readAllWithJoins() {
        return new PushingMapper<RS, List<R>>() {

            final Map<Object, MappingContext<R>> processed = new LinkedHashMap<>();

            @Override
            public void processRow(RS row) {
                MappingContext<R> ctx = MappingContext.of(entity, startingPrefix);
                Object id = readEntityId(row, ctx);
                if (id == null) {
                    throw new IllegalStateException("Entity doesn't have an id!");
                }
                MappingContext<R> prevCtx = processed.get(id);
                if (prevCtx != null) {
                    readChildren(row, prevCtx.entity, null, prevCtx);
                } else {
                    ctx.entity = readEntity(row, ctx, null, id);
                    processed.put(id, ctx);
                }
            }

            @Override
            public List<R> getResult() {
                List<R> values = new ArrayList<>(processed.size());
                for (Map.Entry<Object, MappingContext<R>> e : processed.entrySet()) {
                    MappingContext<R> ctx = e.getValue();
                    R entityInstance = (R) setChildrenAndTriggerPostLoad(ctx.entity, ctx, null);
                    values.add(entityInstance);
                }
                return values;
            }
        };
    }

    private void readChildren(RS rs, Object instance, Object parent, MappingContext<R> ctx) {
        if (ctx.manyAssociations != null) {
            Object id = readEntityId(rs, ctx);
            MappingContext associatedCtx = ctx.manyAssociations.get(id);
            if (associatedCtx == null) {
                associatedCtx = ctx.copy();
                R entity = (R) readEntity(rs, associatedCtx, parent, id);
                Objects.requireNonNull(id);
                ctx.associate(associatedCtx, id, entity);
            } else {
                readChildren(rs, instance, parent, associatedCtx);
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
                RuntimeAssociation runtimeAssociation = (RuntimeAssociation) e.getKey();
                BeanProperty beanProperty = runtimeAssociation.getProperty();
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
                    if (inverseInstance != parent) {
                        instance = inverse.setPropertyValue(instance, parent);
                    }
                }
            }
            triggerPostLoad(ctx.persistentEntity, instance);
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
                        if (prop instanceof Association) {
                            RuntimeAssociation entityAssociation = (RuntimeAssociation) prop;
                            if (prop instanceof Embedded) {
                                args[i] = readEntity(rs, ctx.embedded((Embedded) prop), null, null);
                            } else {
                                final Relation.Kind kind = entityAssociation.getKind();
                                final boolean isInverse = parent != null && isAssociation && ctx.association.getOwner() == entityAssociation.getAssociatedEntity();
                                if (isInverse && kind.isSingleEnded()) {
                                    args[i] = parent;
                                } else {
                                    MappingContext<K> joinCtx = ctx.join(joinPaths, entityAssociation);
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
                            Object v;
                            if (resolveId != null && prop.equals(identity)) {
                                v = resolveId;
                            } else {
                                v = readProperty(rs, ctx, prop);
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
                            }
                            args[i] = convert(prop, v);
                        }
                    } else {
                        throw new DataAccessException("Constructor argument [" + constructorArguments[i].getName() + "] must have an associated getter.");
                    }
                }
                if (nullableEmbedded && args.length > 0 && Arrays.stream(args).allMatch(Objects::isNull)) {
                    return null;
                } else {
                    entity = introspection.instantiate(args);
                }
            }

            if (id != null && identity != null) {
                @SuppressWarnings("unchecked")
                BeanProperty<K, Object> idProperty = (BeanProperty<K, Object>) identity.getProperty();
                entity = (K) convertAndSetWithValue(entity, identity, idProperty, id);
            }
            RuntimePersistentProperty<K> version = persistentEntity.getVersion();
            if (version != null) {
                Object v = readProperty(rs, ctx, version);
                if (v != null) {
                    entity = (K) convertAndSetWithValue(entity, version, version.getProperty(), v);
                }
            }
            for (RuntimePersistentProperty<K> rpp : persistentEntity.getPersistentProperties()) {
                if (rpp.isReadOnly()) {
                    continue;
                } else if (rpp.isConstructorArgument()) {
                    if (rpp instanceof Association) {
                        Association a = (Association) rpp;
                        final Relation.Kind kind = a.getKind();
                        if (kind.isSingleEnded()) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                @SuppressWarnings("unchecked")
                BeanProperty<K, Object> property = (BeanProperty<K, Object>) rpp.getProperty();
                if (rpp instanceof Association) {
                    Association entityAssociation = (Association) rpp;
                    if (rpp instanceof Embedded) {
                        Object value = readEntity(rs, ctx.embedded((Embedded) rpp), parent == null ? entity : parent, null);
                        entity = setProperty(property, entity, value);
                    } else {
                        final boolean isInverse = parent != null && entityAssociation.getKind().isSingleEnded() && isAssociation && ctx.association.getOwner() == entityAssociation.getAssociatedEntity();
                        if (isInverse) {
                            entity = setProperty(property, entity, parent);
                        } else {
                            MappingContext<K> joinCtx = ctx.join(joinPaths, entityAssociation);
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
                        entity = (K) convertAndSetWithValue(entity, rpp, property, v);
                    }
                }
            }
            return entity;
        } catch (InstantiationException e) {
            throw new DataAccessException("Error instantiating entity [" + persistentEntity.getName() + "]: " + e.getMessage(), e);
        }
    }

    private <K> Object readProperty(RS rs, MappingContext<K> ctx, RuntimePersistentProperty<K> prop) {
        String columnName = ctx.namingStrategy.mappedName(ctx.embeddedPath, prop);
        String columnAlias = prop.getAnnotationMetadata().stringValue(MappedProperty.class, MappedProperty.ALIAS).orElse("");
        if (StringUtils.isNotEmpty(columnAlias)) {
            columnName = columnAlias;
        } else if (ctx.prefix != null && ctx.prefix.length() != 0) {
            columnName = ctx.prefix + columnName;
        }
        Object result = resultReader.readDynamic(rs, columnName, prop.getDataType());
        AttributeConverter<Object, Object> converter = prop.getConverter();
        if (converter != null) {
            return converter.convertToEntityValue(result, ConversionContext.of((Argument) prop.getArgument()));
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

    private @Nullable
    <K> Object readEntityId(RS rs, MappingContext<K> ctx) {
        RuntimePersistentProperty<K> identity = ctx.persistentEntity.getIdentity();
        if (identity == null) {
            return null;
        }
        if (identity instanceof Embedded) {
            return readEntity(rs, ctx.embedded((Embedded) identity), null, null);
        }
        return readProperty(rs, ctx, identity);
    }

    private Object convertAndSetWithValue(Object entity, RuntimePersistentProperty<?> rpp, BeanProperty property, Object v) {
        return setProperty(property, entity, convert(rpp, v));
    }

    private Object convert(RuntimePersistentProperty<?> rpp, Object v) {
        Class<?> propertyType = rpp.getType();
        if (v instanceof Array) {
            try {
                v = ((Array) v).getArray();
            } catch (SQLException e) {
                throw new DataAccessException("Error getting an array value: " + e.getMessage(), e);
            }
        }
        if (propertyType.isInstance(v)) {
            return v;
        }
        if (jsonCodec != null && rpp.getDataType() == DataType.JSON) {
            try {
                return jsonCodec.decode(rpp.getArgument(), v.toString());
            } catch (Exception e) {
                // Ignore and try basic convert
            }
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
                        return associatedIntrospection.instantiate(resultReader.convertRequired(resolvedId, identity.getType()));
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

        public <K> MappingContext<K> associate(MappingContext<K> ctx, @NotNull Object associationId, @NotNull Object entity) {
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
        void processRow(RS row);

        /**
         * The result created by pushed rows.
         *
         * @return the result
         */
        R getResult();

    }

}
