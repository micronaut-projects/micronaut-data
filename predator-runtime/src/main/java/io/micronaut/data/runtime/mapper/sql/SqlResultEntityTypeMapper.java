package io.micronaut.data.runtime.mapper.sql;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.runtime.mapper.ResultReader;
import io.micronaut.data.runtime.mapper.TypeMapper;

import java.util.Collections;
import java.util.Set;

/**
 * A {@link TypeMapper} that can take a {@link RuntimePersistentEntity} and a {@link ResultReader} and materialize an instance using
 * using column naming conventions mapped by the entity.
 *
 * @param <RS> The result set type
 * @param <R> The result type
 */
public class SqlResultEntityTypeMapper<RS, R> implements TypeMapper<RS, R> {

    private final RuntimePersistentEntity<R> entity;
    private final ResultReader<RS, String> resultReader;
    private final Set<String> joinPaths;

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
     * Constructor used to customize the join paths.
     * @param entity The entity
     * @param resultReader The result reader
     * @param joinPaths The join paths
     */
    public SqlResultEntityTypeMapper(
            @NonNull RuntimePersistentEntity<R> entity,
            @NonNull ResultReader<RS, String> resultReader,
            @Nullable Set<String> joinPaths) {
        ArgumentUtils.requireNonNull("entity", entity);
        ArgumentUtils.requireNonNull("resultReader", resultReader);
        this.entity = entity;
        this.resultReader = resultReader;
        this.joinPaths = joinPaths != null ? joinPaths : Collections.emptySet();
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
        return readEntity("", "", object, entity);
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
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        try {
            R entity;
            if (ArrayUtils.isEmpty(constructorArguments)) {
                entity = introspection.instantiate();
            } else {
                Object[] args = new Object[constructorArguments.length];
                for (int i = 0; i < constructorArguments.length; i++) {
                    Argument<?> argument = constructorArguments[i];
                    String n = argument.getName();
                    RuntimePersistentProperty<R> prop = persistentEntity.getPropertyByName(n);
                    if (prop != null) {
                        if (prop instanceof Association) {
                            Object associated = readAssociation(prefix, path, rs, (Association) prop);
                            args[i] = associated;
                        } else {

                            Object v = resultReader.readDynamic(rs, prefix + prop.getPersistedName(), prop.getDataType());
                            if (v == null && !prop.isOptional()) {
                                throw new DataAccessException("Null value read for non-null constructor argument [" + argument.getName() + "] of type: " + persistentEntity.getName());
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
            for (PersistentProperty persistentProperty : persistentEntity.getPersistentProperties()) {
                RuntimePersistentProperty rpp = (RuntimePersistentProperty) persistentProperty;
                if (persistentProperty.isReadOnly()) {
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
            RuntimePersistentProperty<R> identity = persistentEntity.getIdentity();
            if (identity != null) {
                String persistedName = identity.getPersistedName();
                Object v = resultReader.readDynamic(rs, prefix + persistedName, identity.getDataType());
                BeanProperty<R, Object> property = (BeanProperty<R, Object>) identity.getProperty();
                if (v == null) {
                    throw new DataAccessException("Table contains null ID for entity: " + entity);
                }
                if (property.getType().isInstance(v)) {
                    property.set(
                            entity,
                            v
                    );
                } else {
                    property.convertAndSet(entity, v);
                }
            }
            return entity;
        } catch (InstantiationException e) {
            throw new DataAccessException("Error instantiating entity [" + persistentEntity.getName() + "]: " + e.getMessage(), e);
        }
    }

    @Nullable
    private Object readAssociation(String prefix, String path, RS resultSet, Association association) {
        Object associated = null;
        String persistedName = association.getPersistedName();
        RuntimePersistentEntity associatedEntity = (RuntimePersistentEntity) association.getAssociatedEntity();
        if (associatedEntity == null) {
            throw new DataAccessException("Associated entity not present for association [" + association.getName() + "] of entity: " + association.getOwner().getName());
        }
        RuntimePersistentProperty identity = associatedEntity.getIdentity();
        String associationName = association.getName();
        if (joinPaths.contains(path + associationName)) {
            String newPrefix = prefix.length() == 0 ? "_" + association.getAliasName() : prefix + association.getAliasName();
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
        return associated;
    }
}
