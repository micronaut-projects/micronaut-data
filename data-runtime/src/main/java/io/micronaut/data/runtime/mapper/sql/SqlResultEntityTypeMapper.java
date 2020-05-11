/*
 * Copyright 2017-2019 original authors
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.http.codec.MediaTypeCodec;

/**
 * A {@link io.micronaut.data.runtime.mapper.TypeMapper} that can take a {@link RuntimePersistentEntity} and a {@link ResultReader} and materialize an instance using
 * using column naming conventions mapped by the entity.
 *
 * @param <RS> The result set type
 * @param <R> The result type
 */
@Internal
public final class SqlResultEntityTypeMapper<RS, R> implements SqlTypeMapper<RS, R> {

    private final RuntimePersistentEntity<R> entity;
    private final ResultReader<RS, String> resultReader;
    private final Map<String, JoinPath> joinPaths;
    private final String startingPrefix;
    private final MediaTypeCodec jsonCodec;
    private boolean callNext = true;

    /**
     * Default constructor.
     * @param entity The entity
     * @param resultReader The result reader
     * @param jsonCodec The json codec
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable MediaTypeCodec jsonCodec) {
        this(entity, resultReader, Collections.emptySet(), jsonCodec);
    }

    /**
     * Default constructor.
     * @param prefix The prefix to startup from.
     * @param entity The entity
     * @param resultReader The result reader
     * @param jsonCodec The JSON codec
     */
    public SqlResultEntityTypeMapper(
            String prefix,
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable MediaTypeCodec jsonCodec) {
        this(entity, resultReader, Collections.emptySet(), prefix, jsonCodec);
    }

    /**
     * Constructor used to customize the join paths.
     * @param entity The entity
     * @param resultReader The result reader
     * @param joinPaths The join paths
     * @param jsonCodec The JSON codec
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths,
            @Nullable MediaTypeCodec jsonCodec) {
        this(entity, resultReader, joinPaths, null, jsonCodec);
    }

    /**
     * Constructor used to customize the join paths.
     * @param entity The entity
     * @param resultReader The result reader
     * @param joinPaths The join paths
     */
    private SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths,
            String startingPrefix,
            @Nullable MediaTypeCodec jsonCodec) {
        ArgumentUtils.requireNonNull("entity", entity);
        ArgumentUtils.requireNonNull("resultReader", resultReader);
        this.entity = entity;
        this.jsonCodec = jsonCodec;
        this.resultReader = resultReader;
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

    /**
     * @return The entity to be materialized
     */
    public @NonNull RuntimePersistentEntity<R> getEntity() {
        return entity;
    }

    /**
     * @return The result reader instance.
     */
    public @NonNull ResultReader<RS, String> getResultReader() {
        return resultReader;
    }

    @NonNull
    @Override
    public R map(@NonNull RS object, @NonNull Class<R> type) throws DataAccessException {
        return readEntity(startingPrefix, null, object, entity, false, null, null, null);
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
        return resultReader.readDynamic(
            resultSet,
            columnName,
            dataType
        );
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

    private R readEntity(
            String prefix,
            String path,
            RS rs,
            RuntimePersistentEntity<R> persistentEntity,
            boolean isEmbedded,
            @Nullable Association association,
            @Nullable Object parent,
            @Nullable Object resolveId) {
        BeanIntrospection<R> introspection = persistentEntity.getIntrospection();
        RuntimePersistentProperty<R>[] constructorArguments = persistentEntity.getConstructorArguments();
        try {
            R entity;
            Object id = resolveId;
            RuntimePersistentProperty<R> identity = persistentEntity.getIdentity();
            boolean hasPrefix = prefix != null;
            boolean hasPath = path != null;
            final boolean isAssociation = association != null;
            final boolean nullableEmbedded = association instanceof Embedded && association.isOptional();
            if (id == null && identity != null) {
                if (identity instanceof Embedded) {
                    PersistentEntity embeddedEntity = ((Embedded) identity).getAssociatedEntity();
                    id = readEntity(
                            identity.getPersistedName() + "_",
                             (hasPath ? path : "") + identity.getName() + '.',
                             rs,
                            (RuntimePersistentEntity<R>) embeddedEntity,
                            true,
                            null,
                            null,
                            null);
                } else {
                    String columnName = resolveColumnName(
                            identity,
                            prefix,
                            isEmbedded,
                            hasPrefix
                    );

                    id = resultReader.readDynamic(rs, columnName, DataType.OBJECT);
                    if (id == null) {
                        return null;
                    }
                }
            }

            if (ArrayUtils.isEmpty(constructorArguments)) {
                entity = introspection.instantiate();
            } else {
                int len = constructorArguments.length;
                Object[] args = new Object[len];
                for (int i = 0; i < len; i++) {
                    RuntimePersistentProperty<R> prop = constructorArguments[i];
                    if (prop != null) {
                        if (prop instanceof Association) {
                            final Association constructorAssociation = (Association) prop;
                            final boolean isInverse = parent != null && isAssociation &&
                                    association.getOwner() == constructorAssociation.getAssociatedEntity();
                            final Relation.Kind kind = constructorAssociation.getKind();
                            if (isInverse && kind.isSingleEnded()) {
                                args[i] = parent;
                            } else {
                                Object resolvedId = null;
                                if (!constructorAssociation.isForeignKey() && !(constructorAssociation instanceof Embedded)) {
                                    String columnName = resolveColumnName(
                                            prop,
                                            prefix,
                                            isEmbedded,
                                            hasPrefix
                                    );
                                    resolvedId = resultReader.readDynamic(
                                            rs,
                                            columnName,
                                            prop.getDataType()
                                    );
                                }
                                if (kind.isSingleEnded()) {
                                    args[i] = readAssociation(
                                            parent,
                                            hasPrefix ? prefix : "",
                                            (hasPath ? path : ""), rs,
                                            constructorAssociation,
                                            resolvedId,
                                            hasPrefix
                                    );
                                }
                            }

                        } else {
                            Object v;
                            if (resolveId != null && prop.equals(identity)) {
                                v = resolveId;
                            } else {
                                String columnName = resolveColumnName(
                                        prop,
                                        prefix,
                                        isEmbedded,
                                        hasPrefix
                                );
                                v = resultReader.readDynamic(
                                        rs,
                                        columnName,
                                        prop.getDataType()
                                );
                                if (v == null) {
                                    if (!prop.isOptional() && !nullableEmbedded) {
                                        throw new DataAccessException("Null value read for non-null constructor argument [" + prop.getName() + "] of type: " + persistentEntity.getName());
                                    } else {
                                        args[i] = null;
                                        continue;
                                    }
                                }
                            }

                            Class<?> t = prop.getType();
                            args[i] = t.isInstance(v) ? v : resultReader.convertRequired(v, t);
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

            if (identity != null && id != null) {
                BeanProperty<R, Object> idProperty = (BeanProperty<R, Object>) identity.getProperty();
                if (!idProperty.isReadOnly()) {
                    id = convertAndSet(entity, identity, idProperty, id, identity.getDataType());
                }
            }
            Map<Association, List<Object>> toManyJoins = null;
            for (RuntimePersistentProperty<R> rpp : persistentEntity.getPersistentProperties()) {
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
                BeanProperty<R, Object> property = (BeanProperty<R, Object>) rpp.getProperty();
                if (rpp instanceof Association) {
                    Association entityAssociation = (Association) rpp;
                    if (!entityAssociation.isForeignKey()) {
                        if (!(entityAssociation instanceof Embedded)) {

                            String columnName = resolveColumnName(
                                    rpp,
                                    prefix,
                                    isEmbedded,
                                    hasPrefix
                            );
                            Object resolvedId = resultReader.readDynamic(
                                    rs,
                                    columnName,
                                    rpp.getDataType()
                            );
                            if (resolvedId != null) {
                                Object associated = readAssociation(
                                        entity,
                                        prefix,
                                        (hasPath ? path : ""),
                                        rs,
                                        entityAssociation,
                                        resolvedId,
                                        hasPrefix
                                );
                                if (associated != null) {
                                    property.set(entity, associated);
                                }
                            }
                        } else {
                            Object associated = readAssociation(
                                    entity,
                                    prefix,
                                    (hasPath ? path : ""),
                                    rs,
                                    entityAssociation,
                                    null,
                                    hasPrefix
                            );
                            if (associated != null) {
                                property.set(entity, associated);
                            }
                        }

                    } else {
                        boolean hasJoin = joinPaths.containsKey((hasPath ? path : "") + entityAssociation.getName());
                        if (hasJoin) {
                            Relation.Kind kind = entityAssociation.getKind();
                            if (kind == Relation.Kind.ONE_TO_ONE && entityAssociation.isForeignKey()) {
                                Object associated = readAssociation(
                                        entity,
                                        prefix, (hasPath ? path : ""),
                                        rs,
                                        entityAssociation,
                                        null,
                                        hasPrefix
                                );
                                if (associated != null) {
                                    property.set(entity, associated);
                                }
                            } else if (id != null && (kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY)) {
                                if (toManyJoins == null) {
                                    toManyJoins = new HashMap<>(3);
                                }
                                toManyJoins.put(entityAssociation, new ArrayList<>());
                            }
                        }
                    }
                } else {
                    String columnName = resolveColumnName(
                            rpp,
                            prefix,
                            isEmbedded,
                            hasPrefix
                    );
                    final DataType dataType = rpp.getDataType();
                    Object v = resultReader.readDynamic(
                            rs,
                            columnName,
                            dataType
                    );

                    if (v != null) {
                        convertAndSet(entity, rpp, property, v, dataType);
                    }
                }
            }

            if (id != null) {
                Object currentId = id;
                if (CollectionUtils.isNotEmpty(toManyJoins)) {

                    while (id.equals(currentId)) {
                        for (Map.Entry<Association, List<Object>> entry : toManyJoins.entrySet()) {
                            Object associated = readAssociation(
                                    entity,
                                    hasPrefix ? prefix : "", (hasPath ? path : ""),
                                    rs,
                                    entry.getKey(),
                                    null,
                                    hasPrefix
                            );
                            if (associated != null) {
                                entry.getValue().add(associated);
                            }
                        }

                        String columnName = resolveColumnName(
                                identity,
                                prefix,
                                isEmbedded,
                                hasPrefix
                        );

                        currentId = nextId(identity, rs, columnName);
                    }

                    if (currentId != null) {
                        this.callNext = false;
                    }

                    toManyJoins.forEach((key, value) -> {
                        RuntimePersistentProperty<R> joinAssociation = (RuntimePersistentProperty<R>) key;
                        BeanProperty<R, Object> property = (BeanProperty<R, Object>) joinAssociation.getProperty();
                        convertAndSet(entity, joinAssociation, property, value, joinAssociation.getDataType());
                    });
                }
            }
            return entity;
        } catch (InstantiationException e) {
            throw new DataAccessException("Error instantiating entity [" + persistentEntity.getName() + "]: " + e.getMessage(), e);
        }
    }

    private String resolveColumnName(RuntimePersistentProperty<R> identity, String prefix, boolean isEmbedded, boolean hasPrefix) {
        final String persistedName = identity.getPersistedName();
        final String columnName;
        if (hasPrefix) {
            if (isEmbedded && identity.getAnnotationMetadata().stringValue(MappedProperty.class).isPresent()) {
                columnName = persistedName;
            } else {
                columnName = prefix + persistedName;
            }
        } else {
            columnName = persistedName;
        }
        return columnName;
    }

    /**
     * Resolve the ID of the next row.
     * @param identity The identity
     * @param resultSet The result set
     * @param columnName The column name
     * @return The ID
     */
    Object nextId(@NonNull RuntimePersistentProperty<R> identity, @NonNull RS resultSet, @NonNull String columnName) {
        if (hasNext(resultSet)) {
            final Object id = resultReader.readDynamic(resultSet, columnName, identity.getDataType());
            final Class<?> isType = identity.getType();
            return isType.isInstance(id) ? id : resultReader.convertRequired(id, isType);
        }
        return null;
    }

    private Object convertAndSet(
            R entity,
            RuntimePersistentProperty<R> rpp,
            BeanProperty<R, Object> property,
            Object v,
            DataType dataType) {
        Class<?> propertyType = rpp.getType();
        final Object r;
        if (propertyType.isInstance(v)) {
            r = v;
        } else {
            if (dataType == DataType.JSON && jsonCodec != null) {
                r = jsonCodec.decode(property.asArgument(), v.toString());
            } else {
                r = resultReader.convertRequired(v, propertyType);
            }
        }
        property.set(entity, r);

        return r;
    }

    @Nullable
    private Object readAssociation(
            Object parent,
            String prefix,
            String path,
            RS resultSet,
            @NonNull Association association,
            @Nullable Object resolvedId,
            boolean hasPrefix) {
        RuntimePersistentEntity<R> associatedEntity = (RuntimePersistentEntity<R>) association.getAssociatedEntity();
        Object associated = null;
        String associationName = association.getName();
        if (association instanceof Embedded) {
            associated = readEntity(
                    association.getPersistedName() + "_",
                    path + associationName + '.',
                    resultSet,
                    associatedEntity,
                    true,
                    association,
                    null,
                    null);
        } else {
            String persistedName = association.getPersistedName();
            RuntimePersistentProperty<R> identity = associatedEntity.getIdentity();
            String joinPath = path + associationName;
            if (joinPaths.containsKey(joinPath)) {
                JoinPath jp = joinPaths.get(joinPath);
                String newPrefix = jp.getAlias().orElse(
                        !hasPrefix ? association.getAliasName() : prefix + association.getAliasName()
                );
                associated = readEntity(
                        newPrefix,
                        path + associationName + '.',
                        resultSet,
                        associatedEntity,
                        false,
                        association,
                        parent,
                        resolvedId
                );
            } else {

                BeanIntrospection<R> associatedIntrospection = associatedEntity.getIntrospection();
                Argument[] constructorArgs = associatedIntrospection.getConstructorArguments();
                if (constructorArgs.length == 0) {
                    associated = associatedIntrospection.instantiate();
                    if (identity != null) {
                        String columnToRead = hasPrefix ? prefix + persistedName : persistedName;

                        Object v = resultReader.readDynamic(
                                resultSet,
                                columnToRead,
                                identity.getDataType()
                        );
                        BeanWrapper.getWrapper(associated).setProperty(
                                identity.getName(),
                                v
                        );
                    }
                } else {
                    if (constructorArgs.length == 1 && identity != null) {
                        Argument arg = constructorArgs[0];
                        if (arg.getName().equals(identity.getName()) && arg.getType() == identity.getType()) {
                            Object v = resultReader.readDynamic(resultSet, hasPrefix ? prefix + persistedName : persistedName, identity.getDataType());
                            associated = associatedIntrospection.instantiate(resultReader.convertRequired(v, identity.getType()));
                        }
                    }
                }
            }
        }
        return associated;
    }
}
