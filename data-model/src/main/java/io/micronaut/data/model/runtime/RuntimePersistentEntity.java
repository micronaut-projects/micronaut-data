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
package io.micronaut.data.model.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Runtime implementation of {@link PersistentEntity} that uses pre-computed {@link io.micronaut.core.annotation.Introspected} bean data and is completely stateless.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The type
 */
public class RuntimePersistentEntity<T> extends AbstractPersistentEntity implements PersistentEntity {

    private final BeanIntrospection<T> introspection;
    private final RuntimePersistentProperty<T> identity;
    private final Map<String, RuntimePersistentProperty<T>> persistentProperties;
    private final RuntimePersistentProperty<T>[] constructorArguments;
    private final String aliasName;
    private RuntimePersistentProperty<T> version;
    private Boolean hasAutoPopulatedProperties;

    /**
     * Default constructor.
     * @param type The type
     */
    public RuntimePersistentEntity(@NonNull Class<T> type) {
        this(BeanIntrospection.getIntrospection(type));
    }

    /**
     * Default constructor.
     * @param introspection The introspection
     */
    public RuntimePersistentEntity(@NonNull BeanIntrospection<T> introspection) {
        super(introspection);
        ArgumentUtils.requireNonNull("introspection", introspection);
        this.introspection = introspection;
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        Set<String> constructorArgumentNames = Arrays.stream(constructorArguments).map(Argument::getName).collect(Collectors.toSet());
        identity = introspection.getIndexedProperty(Id.class).map(bp -> {
            if (bp.enumValue(Relation.class, Relation.Kind.class).map(k -> k == Relation.Kind.EMBEDDED).orElse(false)) {
                return new RuntimeEmbedded<>(this, bp, constructorArgumentNames.contains(bp.getName()));
            } else {
                return new RuntimePersistentProperty<>(this, bp, constructorArgumentNames.contains(bp.getName()));
            }
        }).orElse(null);
        version = introspection.getIndexedProperty(Version.class).map(bp ->
                new RuntimePersistentProperty<>(this, bp, constructorArgumentNames.contains(bp.getName()))
        ).orElse(null);
        Collection<BeanProperty<T, Object>> beanProperties = introspection.getBeanProperties();
        this.persistentProperties = new LinkedHashMap<>(beanProperties.size());

        for (BeanProperty<T, Object> bp : beanProperties) {
            if (!bp.hasStereotype(Id.class, Version.class)) {
                RuntimePersistentProperty<T> prop;
                if (bp.hasAnnotation(Relation.class)) {
                    if (isEmbedded(bp)) {
                        prop = new RuntimeEmbedded<>(this, bp, constructorArgumentNames.contains(bp.getName()));
                    } else {
                        prop = new RuntimeAssociation<>(this, bp, constructorArgumentNames.contains(bp.getName()));
                    }
                } else {
                    prop = new RuntimePersistentProperty<>(this, bp, constructorArgumentNames.contains(bp.getName()));
                }
                persistentProperties.put(prop.getName(), prop);
            }
        }

        this.constructorArguments = new RuntimePersistentProperty[constructorArguments.length];
        for (int i = 0; i < constructorArguments.length; i++) {
            Argument<?> constructorArgument = constructorArguments[i];
            String argumentName = constructorArgument.getName();
            RuntimePersistentProperty<T> prop = getPropertyByName(argumentName);
            if (prop == null) {
                RuntimePersistentProperty<T> identity = getIdentity();
                if (identity != null && !identity.getName().equals(argumentName)) {
                    throw new MappingException("Constructor argument [" + argumentName + "] for type [" + getName() + "] must have an associated getter");
                }
            }
            this.constructorArguments[i] = prop;
        }
        this.aliasName = super.getAliasName();
    }

    /**
     * Does the entity have pre-persist event listeners.
     * @return True if it does
     */
    public boolean hasPrePersistEventListeners() {
        return false;
    }

    /**
     * Does the entity have pre-remove event listeners.
     * @return True if it does
     */
    public boolean hasPreRemoveEventListeners() {
        return false;
    }

    /**
     * Does the entity have pre-update event listeners.
     * @return True if it does
     */
    public boolean hasPreUpdateEventListeners() {
        return false;
    }

    /**
     * Does the entity have post-persist event listeners.
     * @return True if it does
     */
    public boolean hasPostPersistEventListeners() {
        return false;
    }

    /**
     * Does the entity have post-update event listeners.
     * @return True if it does
     */
    public boolean hasPostUpdateEventListeners() {
        return false;
    }

    /**
     * Does the entity have post-remove event listeners.
     * @return True if it does
     */
    public boolean hasPostRemoveEventListeners() {
        return false;
    }

    /**
     * Does the entity have post-load event listeners.
     * @return True if it does
     */
    public boolean hasPostLoadEventListeners() {
        return false;
    }

    @Override
    public String toString() {
        return getName();
    }

    @NonNull
    @Override
    public String getAliasName() {
        return aliasName;
    }

    /**
     * @return The underlying introspection.
     */
    public BeanIntrospection<T> getIntrospection() {
        return introspection;
    }

    @NonNull
    @Override
    public String getName() {
        return introspection.getBeanType().getName();
    }

    @Nullable
    @Override
    public PersistentProperty[] getCompositeIdentity() {
        return new PersistentProperty[0];
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getIdentity() {
        return identity;
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getVersion() {
        return version;
    }

    @NonNull
    @Override
    public Collection<RuntimePersistentProperty<T>> getPersistentProperties() {
        return persistentProperties.values();
    }

    @NonNull
    @Override
    public Collection<RuntimeAssociation<T>> getAssociations() {
        return persistentProperties
                .values()
                .stream()
                .filter(bp -> bp.getAnnotationMetadata().hasStereotype(Relation.class))
                .map(p -> ((RuntimeAssociation<T>) p))
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public Collection<Embedded> getEmbedded() {
        return persistentProperties.values().stream()
                .filter(pp -> pp instanceof Embedded)
                .map(pp -> (Embedded) pp)
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getPropertyByName(String name) {
        RuntimePersistentProperty<T> property = persistentProperties.get(name);
        if (property == null) {
            RuntimePersistentProperty<T> identity = getIdentity();
            if (identity != null && identity.getName().equals(name)) {
                return identity;
            }
        }
        return property;
    }

    @NonNull
    @Override
    public List<String> getPersistentPropertyNames() {
        return Arrays.asList(introspection.getPropertyNames());
    }

    @Override
    public boolean isOwningEntity(PersistentEntity owner) {
        return true;
    }

    @Nullable
    @Override
    public PersistentEntity getParentEntity() {
        return null;
    }

    private boolean isEmbedded(BeanProperty bp) {
        return bp.enumValue(Relation.class, Relation.Kind.class).orElse(null) == Relation.Kind.EMBEDDED;
    }

    /**
     * Obtain an entity for the given type.
     * @param type The type
     * @return The entity
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if the entity doesn't exist
     */
    protected RuntimePersistentEntity<T> getEntity(Class<T> type) {
        return PersistentEntity.of(type);
    }

    /**
     * @return The constructor arguments.
     */
    public RuntimePersistentProperty<T>[] getConstructorArguments() {
        return constructorArguments;
    }

    /**
     * @return Returns true if the entity has auto-populated properties.
     */
    public boolean hasAutoPopulatedProperties() {
        if (this.hasAutoPopulatedProperties == null) {
            final RuntimePersistentProperty<T> identity = getIdentity();
            boolean hasAutoPopulated = isAutoPopulatedProperty(identity);

            if (!hasAutoPopulated) {
                hasAutoPopulated = persistentProperties.values().stream()
                        .anyMatch(PersistentProperty::isAutoPopulated);
            }
            this.hasAutoPopulatedProperties = hasAutoPopulated;
        }
        return this.hasAutoPopulatedProperties;
    }

    private boolean isAutoPopulatedProperty(RuntimePersistentProperty<T> identity) {
        return identity != null && identity.isAutoPopulated();
    }
}
