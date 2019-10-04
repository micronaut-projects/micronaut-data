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
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.http.codec.MediaTypeCodec;

import java.util.*;

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
        return readEntity(startingPrefix, null, object, entity, false);
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
            boolean isEmbedded) {
        BeanIntrospection<R> introspection = persistentEntity.getIntrospection();
        RuntimePersistentProperty<R>[] constructorArguments = persistentEntity.getConstructorArguments();
        try {
            R entity;
            Object id = null;
            RuntimePersistentProperty<R> identity = persistentEntity.getIdentity();
            boolean hasPrefix = prefix != null;
            boolean hasPath = path != null;
            if (identity != null) {
                if (identity instanceof Embedded) {
                    PersistentEntity embeddedEntity = ((Embedded) identity).getAssociatedEntity();
                    id = readEntity(
                            identity.getPersistedName() + "_",
                             (hasPath ? path : "") + identity.getName() + '.',
                             rs,
                            (RuntimePersistentEntity<R>) embeddedEntity,
                            true);
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
                            Object associated = readAssociation(hasPrefix ? prefix : "", (hasPath ? path : ""), rs, (Association) prop, hasPrefix);
                            args[i] = associated;
                        } else {

                            String columnName = resolveColumnName(
                                    prop,
                                    prefix,
                                    isEmbedded,
                                    hasPrefix
                            );
                            Object v = resultReader.readDynamic(
                                    rs,
                                    columnName,
                                    prop.getDataType()
                            );
                            if (v == null) {
                                if (!prop.isOptional()) {
                                    throw new DataAccessException("Null value read for non-null constructor argument [" + prop.getName() + "] of type: " + persistentEntity.getName());
                                } else {
                                    args[i] = null;
                                    continue;
                                }
                            }
                            Class<?> t = prop.getType();
                            if (!t.isInstance(v)) {
                                args[i] = resultReader.convertRequired(v, t);
                            } else {
                                args[i] = v;
                            }
                        }
                    } else {
                        throw new DataAccessException("Constructor argument [" + constructorArguments[i].getName() + "] must have an associated getter.");
                    }
                }
                entity = introspection.instantiate(args);
            }
            Map<Association, List> toManyJoins = null;
            for (PersistentProperty persistentProperty : persistentEntity.getPersistentProperties()) {
                RuntimePersistentProperty rpp = (RuntimePersistentProperty) persistentProperty;
                    if (persistentProperty.isReadOnly() || persistentProperty.isConstructorArgument()) {
                    continue;
                }
                BeanProperty property = rpp.getProperty();
                if (persistentProperty instanceof Association) {
                    Association association = (Association) persistentProperty;
                    if (!association.isForeignKey()) {
                        Object associated = readAssociation(prefix, (hasPath ? path : ""), rs, association, hasPrefix);
                        if (associated != null) {
                            property.set(entity, associated);
                        }
                    } else {
                        Relation.Kind kind = association.getKind();
                        boolean hasJoin = joinPaths.containsKey((hasPath ? path : "") + association.getName());
                        if (hasJoin) {
                            if (kind == Relation.Kind.ONE_TO_ONE && association.isForeignKey()) {
                                Object associated = readAssociation(prefix, (hasPath ? path : ""), rs, association, hasPrefix);
                                if (associated != null) {
                                    property.set(entity, associated);
                                }
                            } else if (id != null && (kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY)) {
                                if (toManyJoins == null) {
                                    toManyJoins = new HashMap<>(3);
                                }
                                toManyJoins.put(association, new ArrayList());
                            }
                        }
                    }
                } else {
                    String columnName = resolveColumnName(
                            (RuntimePersistentProperty<R>) persistentProperty,
                            prefix,
                            isEmbedded,
                            hasPrefix
                    );
                    final DataType dataType = persistentProperty.getDataType();
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

                    while (currentId != null && currentId.equals(id)) {
                        for (Map.Entry<Association, List> entry : toManyJoins.entrySet()) {
                            Association association = entry.getKey();
                            Object associated = readAssociation(hasPrefix ? prefix : "", (hasPath ? path : ""), rs, association, hasPrefix);
                            if (associated != null) {
                                entry.getValue().add(associated);
                            }
                        }
                        currentId = nextId(identity, rs);
                    }

                    if (currentId != null) {
                        this.callNext = false;
                    }

                    for (Map.Entry<Association, List> entry : toManyJoins.entrySet()) {
                        List value = entry.getValue();
                        RuntimePersistentProperty association = (RuntimePersistentProperty) entry.getKey();
                        BeanProperty property = association.getProperty();
                        convertAndSet(entity, association, property, value, association.getDataType());
                    }
                }
                BeanProperty<R, Object> property = (BeanProperty<R, Object>) identity.getProperty();
                if (!property.isReadOnly()) {
                    convertAndSet(entity, identity, property, id, identity.getDataType());
                }
            }
            return entity;
        } catch (InstantiationException e) {
            throw new DataAccessException("Error instantiating entity [" + persistentEntity.getName() + "]: " + e.getMessage(), e);
        }
    }

    private String resolveColumnName(RuntimePersistentProperty<R> identity, String prefix, boolean isEmbedded, boolean hasPrefix) {
        String persistedName = identity.getPersistedName();
        String columnName;
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
     * @return The ID
     */
    Object nextId(@NonNull RuntimePersistentProperty<R> identity, @NonNull RS resultSet) {
        return resultReader.readNextDynamic(resultSet, identity.getPersistedName(), identity.getDataType());
    }

    private void convertAndSet(
            R entity,
            RuntimePersistentProperty rpp,
            BeanProperty property,
            Object v,
            DataType dataType) {
        Class<?> propertyType = rpp.getType();
        if (propertyType.isInstance(v)) {
            property.set(entity, v);
        } else {
            if (dataType == DataType.JSON && jsonCodec != null) {
                property.set(entity, jsonCodec.decode(property.asArgument(), v.toString()));
            } else {
                property.set(entity, resultReader.convertRequired(v, propertyType));
            }
        }
    }

    @Nullable
    private Object readAssociation(String prefix, String path, RS resultSet, Association association, boolean hasPrefix) {
        RuntimePersistentEntity associatedEntity = (RuntimePersistentEntity) association.getAssociatedEntity();
        Object associated = null;
        String associationName = association.getName();
        if (association instanceof Embedded) {
            associated = readEntity(
                    association.getPersistedName() + "_",
                    path + associationName + '.',
                    resultSet,
                    associatedEntity,
                    true);
        } else {
            String persistedName = association.getPersistedName();
            RuntimePersistentProperty identity = associatedEntity.getIdentity();
            String joinPath = path + associationName;
            if (joinPaths.containsKey(joinPath)) {
                JoinPath jp = joinPaths.get(joinPath);
                String newPrefix = jp.getAlias().orElseGet(() ->
                        !hasPrefix ? "_" + association.getAliasName() : prefix + association.getAliasName()
                );
                associated = readEntity(
                        newPrefix,
                        path + associationName + '.',
                        resultSet,
                        associatedEntity,
                        false
                );
            } else {

                BeanIntrospection associatedIntrospection = associatedEntity.getIntrospection();
                Argument[] constructorArgs = associatedIntrospection.getConstructorArguments();
                if (constructorArgs.length == 0) {
                    associated = associatedIntrospection.instantiate();
                    if (identity != null) {
                        Object v = resultReader.readDynamic(resultSet, hasPrefix ? prefix + persistedName : persistedName, identity.getDataType());
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
