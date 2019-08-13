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
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.TypeMapper;

import java.util.*;

/**
 * A {@link TypeMapper} that can take a {@link RuntimePersistentEntity} and a {@link ResultReader} and materialize an instance using
 * using column naming conventions mapped by the entity.
 *
 * @param <RS> The result set type
 * @param <R> The result type
 */
@Internal
public class SqlResultEntityTypeMapper<RS, R> implements TypeMapper<RS, R> {

    private final RuntimePersistentEntity<R> entity;
    private final ResultReader<RS, String> resultReader;
    private final Map<String, JoinPath> joinPaths;
    private final String startingPrefix;

    /**
     * Default constructor.
     * @param entity The entity
     * @param resultReader The result reader
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader) {
        this(entity, resultReader, Collections.emptySet());
    }

    /**
     * Default constructor.
     * @param prefix The prefix to startup from.
     * @param entity The entity
     * @param resultReader The result reader
     */
    public SqlResultEntityTypeMapper(
            String prefix,
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader) {
        this(entity, resultReader, Collections.emptySet(), prefix);
    }

    /**
     * Constructor used to customize the join paths.
     * @param entity The entity
     * @param resultReader The result reader
     * @param joinPaths The join paths
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<JoinPath> joinPaths) {
        this(entity, resultReader, joinPaths, "");
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
            String startingPrefix) {
        ArgumentUtils.requireNonNull("entity", entity);
        ArgumentUtils.requireNonNull("resultReader", resultReader);
        this.entity = entity;
        this.resultReader = resultReader;
        if (CollectionUtils.isNotEmpty(joinPaths)) {
            this.joinPaths = new HashMap<>(joinPaths.size());
            for (JoinPath joinPath : joinPaths) {
                this.joinPaths.put(joinPath.getPath(), joinPath);
            }
        } else {
            this.joinPaths = Collections.emptyMap();
        }
        this.startingPrefix = startingPrefix != null ? startingPrefix : "";
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
        return readEntity(startingPrefix, null, object, entity);
    }

    @Nullable
    @Override
    public Object read(@NonNull RS resultSet, @NonNull String name) {
        RuntimePersistentProperty<R> property = entity.getPropertyByName(name);
        if (property == null) {
            throw new DataAccessException("DTO projection defines a property [" + name + "] that doesn't exist on root entity: " + entity.getName());
        }

        return resultReader.readDynamic(
            resultSet,
            property.getPersistedName(),
            property.getDataType()
        );
    }

    private R readEntity(String prefix, String path, RS rs, RuntimePersistentEntity<R> persistentEntity) {
        BeanIntrospection<R> introspection = persistentEntity.getIntrospection();
        Argument<?>[] constructorArguments = persistentEntity.getConstructorArguments();
        try {
            R entity;
            Object id = null;
            RuntimePersistentProperty<R> identity = persistentEntity.getIdentity();
            if (identity != null) {
                if (identity instanceof Embedded) {
                    PersistentEntity embeddedEntity = ((Embedded) identity).getAssociatedEntity();
                    id = readEntity(identity.getPersistedName() + "_", path + identity.getName() + '.', rs, (RuntimePersistentEntity<R>) embeddedEntity);
                } else {
                    String persistedName = identity.getPersistedName();
                    id = resultReader.readDynamic(rs, prefix != null ? prefix + persistedName : persistedName, identity.getDataType());
                    if (id == null) {
                        throw new DataAccessException("Table contains null ID for entity: " + persistentEntity.getName());
                    }
                }
            }

            Set<String> processedViaConstructor;
            if (ArrayUtils.isEmpty(constructorArguments)) {
                entity = introspection.instantiate();
                processedViaConstructor = Collections.emptySet();
            } else {
                int len = constructorArguments.length;
                Object[] args = new Object[len];
                processedViaConstructor = new HashSet<>(len);
                for (int i = 0; i < len; i++) {
                    Argument<?> argument = constructorArguments[i];
                    String n = argument.getName();
                    RuntimePersistentProperty<R> prop = persistentEntity.getPropertyByName(n);
                    if (prop != null) {
                        processedViaConstructor.add(prop.getName());
                        if (prop instanceof Association) {
                            Object associated = readAssociation(prefix, path, rs, (Association) prop);
                            args[i] = associated;
                        } else {

                            Object v = resultReader.readDynamic(rs, prefix != null ? prefix + prop.getPersistedName() : prop.getPersistedName(), prop.getDataType());
                            if (v == null) {
                                if (!prop.isOptional()) {
                                    throw new DataAccessException("Null value read for non-null constructor argument [" + argument.getName() + "] of type: " + persistentEntity.getName());
                                } else {
                                    args[i] = null;
                                    continue;
                                }
                            }
                            Class<?> t = argument.getType();
                            if (!t.isInstance(v)) {
                                args[i] = resultReader.convertRequired(v, t);
                            } else {
                                args[i] = v;
                            }
                        }
                    } else {
                        throw new DataAccessException("Constructor [" + argument.getName() + "] must have a getter for type: " + persistentEntity.getName());
                    }
                }
                entity = introspection.instantiate(args);
            }
            Map<Association, List> toManyJoins = null;
            for (PersistentProperty persistentProperty : persistentEntity.getPersistentProperties()) {
                RuntimePersistentProperty rpp = (RuntimePersistentProperty) persistentProperty;
                    if (persistentProperty.isReadOnly() || processedViaConstructor.contains(persistentProperty.getName())) {
                    continue;
                }
                BeanProperty property = rpp.getProperty();
                if (persistentProperty instanceof Association) {
                    Association association = (Association) persistentProperty;
                    if (!association.isForeignKey()) {
                        Object associated = readAssociation(prefix, path, rs, association);
                        if (associated != null) {
                            property.set(entity, associated);
                        }
                    } else {
                        Relation.Kind kind = association.getKind();
                        boolean hasJoin = joinPaths.containsKey(path + association.getName());
                        if (hasJoin) {
                            if (kind == Relation.Kind.ONE_TO_ONE && association.isForeignKey()) {
                                Object associated = readAssociation(prefix, path, rs, association);
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
                    String persistedName = persistentProperty.getPersistedName();
                    Object v = resultReader.readDynamic(rs, prefix + persistedName, persistentProperty.getDataType());

                    if (rpp.getType().isInstance(v)) {
                        property.set(entity, v);
                    } else {
                        property.convertAndSet(entity, v);
                    }
                }
            }

            if (id != null) {
                Object currentId = id;
                if (CollectionUtils.isNotEmpty(toManyJoins)) {

                    while (currentId != null && currentId.equals(id)) {
                        for (Map.Entry<Association, List> entry : toManyJoins.entrySet()) {
                            Association association = entry.getKey();
                            Object associated = readAssociation(prefix, path, rs, association);
                            entry.getValue().add(associated);
                        }
                        currentId = nextId(identity, rs);
                    }

                    for (Map.Entry<Association, List> entry : toManyJoins.entrySet()) {
                        List value = entry.getValue();
                        RuntimePersistentProperty association = (RuntimePersistentProperty) entry.getKey();
                        BeanProperty property = association.getProperty();
                        if (property.getType().isInstance(value)) {
                            property.set(entity, value);
                        } else {
                            property.convertAndSet(entity, value);
                        }
                    }
                }
                BeanProperty<R, Object> property = (BeanProperty<R, Object>) identity.getProperty();
                if (!property.isReadOnly()) {
                    if (property.getType().isInstance(id)) {
                        property.set(
                                entity,
                                id
                        );
                    } else {
                        property.convertAndSet(entity, id);
                    }
                }
            }
            return entity;
        } catch (InstantiationException e) {
            throw new DataAccessException("Error instantiating entity [" + persistentEntity.getName() + "]: " + e.getMessage(), e);
        }
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

    @Nullable
    private Object readAssociation(String prefix, String path, RS resultSet, Association association) {
        RuntimePersistentEntity associatedEntity = (RuntimePersistentEntity) association.getAssociatedEntity();
        Object associated = null;
        String associationName = association.getName();
        if (association instanceof Embedded) {
            associated = readEntity(association.getPersistedName() + "_", path + associationName + '.', resultSet, associatedEntity);
        } else {
            String persistedName = association.getPersistedName();
            RuntimePersistentProperty identity = associatedEntity.getIdentity();
            String joinPath = path + associationName;
            if (joinPaths.containsKey(joinPath)) {
                JoinPath jp = joinPaths.get(joinPath);
                String newPrefix = jp.getAlias().orElseGet(() ->
                        prefix.length() == 0 ? "_" + association.getAliasName() : prefix + association.getAliasName()
                );
                associated = readEntity(newPrefix, path + associationName + '.', resultSet, associatedEntity);
            } else {

                BeanIntrospection associatedIntrospection = associatedEntity.getIntrospection();
                Argument[] constructorArgs = associatedIntrospection.getConstructorArguments();
                if (constructorArgs.length == 0) {
                    associated = associatedIntrospection.instantiate();
                    if (identity != null) {
                        Object v = resultReader.readDynamic(resultSet, prefix + persistedName, identity.getDataType());
                        BeanWrapper.getWrapper(associated).setProperty(
                                identity.getName(),
                                v
                        );
                    }
                } else {
                    if (constructorArgs.length == 1 && identity != null) {
                        Argument arg = constructorArgs[0];
                        if (arg.getName().equals(identity.getName()) && arg.getType() == identity.getType()) {
                            Object v = resultReader.readDynamic(resultSet, prefix + persistedName, identity.getDataType());
                            associated = associatedIntrospection.instantiate(resultReader.convertRequired(v, identity.getType()));
                        }
                    }
                }
            }
        }
        return associated;
    }
}
