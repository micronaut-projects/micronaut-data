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

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiFunction;

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
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.naming.NamingStrategy;
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
    private final BiFunction<RuntimePersistentEntity<R>, R, R> eventListener;
    private boolean callNext = true;

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
        this(entity, resultReader, Collections.emptySet(), prefix, jsonCodec, null);
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
        this(entity, resultReader, joinPaths, null, jsonCodec, null);
    }

    /**
     * Constructor used to customize the join paths.
     * @param entity The entity
     * @param resultReader The result reader
     * @param joinPaths The join paths
     * @param jsonCodec The JSON codec
     * @param loadListener The event listener
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths,
            @Nullable MediaTypeCodec jsonCodec,
            @Nullable BiFunction<RuntimePersistentEntity<R>, R, R> loadListener) {
        this(entity, resultReader, joinPaths, null, jsonCodec, loadListener);
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
            @Nullable MediaTypeCodec jsonCodec,
            @Nullable BiFunction<RuntimePersistentEntity<R>, R, R> eventListener) {
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
        final R entity = readEntity(Collections.emptyList(), Collections.emptyList(), null, startingPrefix, object, this.entity, this.entity.getNamingStrategy(), null, null);
        if (entity == null) {
            throw new DataAccessException("Unable to map result to entity of type [" + type.getName() + "]. Missing result data.");
        }
        return entity;
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

    public Object readId(@NonNull RS object) {
        return readEntityId(Collections.emptyList(), Collections.emptyList(), entity.getIdentity(), null, object, entity.getNamingStrategy());
    }

    private @Nullable R readEntity(
            List<Association> joinPath,
            List<Association> propertyPath,
            Association association,
            String prefix,
            RS rs,
            RuntimePersistentEntity<R> persistentEntity,
            NamingStrategy namingStrategy,
            @Nullable Object parent,
            @Nullable Object resolveId) {
        BeanIntrospection<R> introspection = persistentEntity.getIntrospection();
        RuntimePersistentProperty<R>[] constructorArguments = persistentEntity.getConstructorArguments();
        try {
            R entity;
            RuntimePersistentProperty<R> identity = persistentEntity.getIdentity();
            final boolean isAssociation = association != null;
            final boolean isEmbedded = association instanceof Embedded;
            final boolean nullableEmbedded = isEmbedded && association.isOptional();

            Object id;
            if (resolveId == null) {
                id = readEntityId(joinPath, propertyPath, identity, prefix, rs, namingStrategy);
            } else {
                id = resolveId;
            }

            if (id == null && !isEmbedded && isAssociation) {
                return null;
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
                                    String columnName = resolveColumnName(namingStrategy, propertyPath, prop, prefix);

                                    resolvedId = resultReader.readDynamic(
                                            rs,
                                            columnName,
                                            prop.getDataType()
                                    );
                                }
                                if (kind.isSingleEnded()) {
                                    args[i] = readAssociation(
                                            joinPath,
                                            propertyPath,
                                            parent,
                                            prefix,
                                            rs,
                                            constructorAssociation,
                                            resolvedId,
                                            namingStrategy
                                    );
                                }
                            }

                        } else {
                            Object v;
                            if (resolveId != null && prop.equals(identity)) {
                                v = resolveId;
                            } else {
                                String columnName = resolveColumnName(namingStrategy, propertyPath, prop, prefix);
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

            if (id != null && identity != null) {
                @SuppressWarnings("unchecked")
                BeanProperty<R, Object> idProperty = (BeanProperty<R, Object>) identity.getProperty();
                if (!idProperty.isReadOnly()) {
                    id = convertAndSet(entity, identity, idProperty, id, identity.getDataType());
                }
            }

            Map<Association, List<Object>> toManyJoins = getJoins(joinPath, propertyPath, rs, entity, id, persistentEntity, prefix, namingStrategy);

            if (id != null) {
                readChildren(joinPath, propertyPath, rs, persistentEntity, entity, id, identity, toManyJoins, prefix);
            }

            R finalEntity;
            if (eventListener != null && persistentEntity.hasPostLoadEventListeners()) {
                finalEntity = eventListener.apply(persistentEntity, entity);
            } else {
                finalEntity = entity;
            }
            return finalEntity;
        } catch (InstantiationException e) {
            throw new DataAccessException("Error instantiating entity [" + persistentEntity.getName() + "]: " + e.getMessage(), e);
        }
    }

    private List<Association> associated(List<Association> associations, Association association) {
        List<Association> newAssociations = new ArrayList<>(associations.size() + 1);
        newAssociations.addAll(associations);
        newAssociations.add(association);
        return newAssociations;
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

    private Map<Association, List<Object>> getJoins(List<Association> joinPath,
                                                    List<Association> propertyPath,
                                                    RS rs, R entity, @Nullable Object id,
                                                    RuntimePersistentEntity<R> persistentEntity,
                                                    String prefix,
                                                    NamingStrategy namingStrategy) {
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
            @SuppressWarnings("unchecked")
            BeanProperty<R, Object> property = (BeanProperty<R, Object>) rpp.getProperty();
            if (rpp instanceof Association) {
                Association entityAssociation = (Association) rpp;
                List<Association> newPropertyPath = associated(propertyPath, entityAssociation);
                if (!entityAssociation.isForeignKey()) {
                    if (!(entityAssociation instanceof Embedded)) {
                        RuntimePersistentEntity associatedEntity = (RuntimePersistentEntity) entityAssociation.getAssociatedEntity();
                        RuntimePersistentProperty identity = associatedEntity.getIdentity();
                        Object resolvedId = readEntityId(joinPath, newPropertyPath, identity, prefix, rs, namingStrategy);
                        if (resolvedId != null) {
                            Object associated = readAssociation(
                                    joinPath,
                                    propertyPath,
                                    entity,
                                    prefix,
                                    rs,
                                    entityAssociation,
                                    resolvedId,
                                    namingStrategy
                            );
                            if (associated != null) {
                                property.set(entity, associated);
                            }
                        }
                    } else {
                        Object associated = readAssociation(
                                joinPath,
                                propertyPath,
                                entity,
                                prefix,
                                rs,
                                entityAssociation,
                                null,
                                namingStrategy
                        );
                        if (associated != null) {
                            property.set(entity, associated);
                        }
                    }

                } else {
                    boolean hasJoin = false;
                    if (!joinPaths.isEmpty()) {
                        hasJoin = joinPaths.containsKey(asPath(joinPath, entityAssociation));
                    }
                    if (hasJoin) {
                        Relation.Kind kind = entityAssociation.getKind();
                        if (kind == Relation.Kind.ONE_TO_ONE && entityAssociation.isForeignKey()) {
                            Object associated = readAssociation(
                                    joinPath,
                                    propertyPath,
                                    entity,
                                    prefix,
                                    rs,
                                    entityAssociation,
                                    null,
                                    namingStrategy
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
                String columnName = resolveColumnName(namingStrategy, propertyPath, rpp, prefix);
                DataType dataType = rpp.getDataType();

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
        return toManyJoins;
    }

    public void readChildren(RS rs, R object) {
        Object id = entity.getIdentity().getProperty().get(object);
        NamingStrategy namingStrategy = entity.getNamingStrategy();
        Map<Association, List<Object>> joins = getJoins(Collections.emptyList(), Collections.emptyList(), rs, object, id,
                entity,
                startingPrefix,
                namingStrategy);
        readChildren(Collections.emptyList(), Collections.emptyList(),
                rs, entity, object, id,
                entity.getIdentity(),
                joins,
                startingPrefix);
    }

    private void readChildren(List<Association> joinPath,
                              List<Association> propertyPath,
                              RS rs,
                              RuntimePersistentEntity<R> persistentEntity,
                              R entity, Object id,
                              RuntimePersistentProperty<R> identity,
                              Map<Association, List<Object>> toManyJoins,
                              String prefix) {
        if (id != null) {
            Object currentId = id;
            if (CollectionUtils.isNotEmpty(toManyJoins)) {

                while (id.equals(currentId)) {
                    for (Map.Entry<Association, List<Object>> entry : toManyJoins.entrySet()) {
                        Object associated = readAssociation(
                                joinPath,
                                Collections.emptyList(),
                                entity,
                                prefix,
                                rs,
                                entry.getKey(),
                                null,
                                persistentEntity.getNamingStrategy()
                        );
                        if (associated != null) {
                            entry.getValue().add(associated);
                        }
                    }

                    String columnName = resolveColumnName(persistentEntity.getNamingStrategy(), propertyPath, identity, prefix);

                    currentId = nextId(identity, rs, columnName);
                }

                if (currentId != null) {
                    this.callNext = false;
                }

                toManyJoins.forEach((key, value) -> {
                    @SuppressWarnings("unchecked")
                    RuntimePersistentProperty<R> joinAssociation = (RuntimePersistentProperty<R>) key;
                    @SuppressWarnings("unchecked")
                    BeanProperty<R, Object> property = (BeanProperty<R, Object>) joinAssociation.getProperty();
                    Object previousValue = property.get(entity);
                    if (previousValue instanceof Collection) {
                        Collection<Object> previousCollection = (Collection) previousValue;
                        List<Object> newCollection = new ArrayList<>(previousCollection.size() + value.size());
                        newCollection.addAll(previousCollection);
                        newCollection.addAll(value);
                        value = newCollection;
                    }
                    convertAndSet(entity, joinAssociation, property, value, joinAssociation.getDataType());
                });
            }
        }
    }

    private @Nullable Object readEntityId(List<Association> joinPath,
                                          List<Association> propertyPath,
                                          RuntimePersistentProperty identity,
                                          String prefix,
                                          RS rs,
                                          NamingStrategy namingStrategy) {
        if (identity == null) {
            return null;
        }
        if (identity instanceof Embedded) {
            //noinspection unchecked
            return readEntity(
                    joinPath,
                    associated(propertyPath, (Association) identity),
                    (Embedded) identity,
                    prefix,
                    rs,
                    (RuntimePersistentEntity<R>) ((Embedded) identity).getAssociatedEntity(),
                    namingStrategy,
                    null,
                    null
            );
        } else {

            String columnName = resolveColumnName(
                    namingStrategy,
                    propertyPath,
                    identity,
                    prefix
            );

            return resultReader.readDynamic(rs, columnName, identity.getDataType());
        }
    }

    private String resolveColumnName(NamingStrategy namingStrategy,
                                     List<Association> associations,
                                     RuntimePersistentProperty<R> property,
                                     String prefix) {
        String columnName = namingStrategy.mappedName(associations, property);
        if (prefix != null && prefix.length() != 0) {
            return prefix + columnName;
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
            if (id == null) {
                return null;
            }
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
        if (v instanceof Array) {
            try {
                v = ((Array) v).getArray();
            } catch (SQLException e) {
                throw new DataAccessException("Error getting an array value: " + e.getMessage(), e);
            }
        }
        if (propertyType.isInstance(v)) {
            r = v;
        } else {
            if (dataType == DataType.JSON && jsonCodec != null) {
                r = jsonCodec.decode(rpp.getArgument(), v.toString());
            } else {
                r = resultReader.convertRequired(v, rpp.getArgument());
            }
        }
        property.set(entity, r);

        return r;
    }

    @Nullable
    private Object readAssociation(
            List<Association> joinPath,
            List<Association> propertyPath,
            Object parent,
            String prefix,
            RS resultSet,
            @NonNull Association association,
            @Nullable Object resolvedId,
            NamingStrategy namingStrategy) {
        @SuppressWarnings("unchecked")
        RuntimePersistentEntity<R> associatedEntity = (RuntimePersistentEntity<R>) association.getAssociatedEntity();
        NamingStrategy embeddedNamingStrategy = associatedEntity.findNamingStrategy().orElse(namingStrategy);
        List<Association> newPropertyPath = associated(propertyPath, association);
        if (association instanceof Embedded) {
            return readEntity(
                    joinPath,
                    newPropertyPath,
                    association,
                    prefix,
                    resultSet,
                    associatedEntity,
                    embeddedNamingStrategy,
                    null,
                    null);
        }
        JoinPath jp = null;
        if (!joinPaths.isEmpty()) {
            jp = joinPaths.get(asPath(joinPath, association));
            if (jp == null) {
                jp = joinPaths.get(asPath(propertyPath, association));
            }
        }
        if (jp != null) {
            String newPrefix = jp.getAlias().orElseGet(() -> {
                    String alias = association.getAliasName();
                    if (!propertyPath.isEmpty()) {
                        StringBuilder sb = prefix == null ? new StringBuilder() : new StringBuilder(prefix);
                        for (Association embedded : propertyPath) {
                            sb.append(embedded.getName());
                            sb.append('_');
                        }
                        sb.append(alias);
                        return sb.toString();
                    }
                    return prefix == null ? alias : prefix +  alias;
            });
            return readEntity(
                    associated(joinPath, association),
                    Collections.emptyList(), // Reset path
                    association,
                    newPrefix,
                    resultSet,
                    associatedEntity,
                    associatedEntity.getNamingStrategy(),
                    parent,
                    resolvedId
            );
        }
        RuntimePersistentProperty<R> identity = associatedEntity.getIdentity();
        if (identity != null) {
            BeanIntrospection<R> associatedIntrospection = associatedEntity.getIntrospection();
            Argument<?>[] constructorArgs = associatedIntrospection.getConstructorArguments();
            if (constructorArgs.length == 0) {
                Object associated = associatedIntrospection.instantiate();
                Object identityValue = readEntityId(joinPath, newPropertyPath, identity, prefix, resultSet, namingStrategy);
                BeanWrapper.getWrapper(associated).setProperty(identity.getName(), identityValue);
                return associated;
            } else {
                if (constructorArgs.length == 1) {
                    Argument<?> arg = constructorArgs[0];
                    if (arg.getName().equals(identity.getName()) && arg.getType() == identity.getType()) {
                        Object identityValue = readEntityId(joinPath, newPropertyPath, identity, prefix, resultSet, namingStrategy);
                        return associatedIntrospection.instantiate(resultReader.convertRequired(identityValue, identity.getType()));
                    }
                }
            }
        }
        return null;
    }

    public RuntimePersistentEntity<R> getPersistentEntity() {
        return entity;
    }
}
