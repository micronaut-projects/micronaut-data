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

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.AbstractPersistentEntity;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runtime implementation of {@link PersistentEntity} that uses pre-computed {@link io.micronaut.core.annotation.Introspected} bean data and is completely stateless.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The type
 */
public class RuntimePersistentEntity<T> extends AbstractPersistentEntity {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimePersistentEntity.class);

    private final BeanIntrospection<T> introspection;
    private final RuntimePersistentProperty<T>[] identity;

    // Subset of all introspections properties with the same order as in BeanIntrospection with nulls if excluded.
    private final RuntimePersistentProperty<T>[] allPersistentProperties;
    private final RuntimePersistentProperty<T>[] persistentProperties;

    private final RuntimePersistentProperty<T>[] constructorArguments;
    private final String aliasName;
    @Nullable
    private final RuntimePersistentProperty<T> version;
    @Nullable
    private Boolean hasAutoPopulatedProperties;
    @Nullable
    private List<String> allPersistentPropertiesNames;
    @Nullable
    private List<RuntimePersistentProperty<T>> persistentPropertiesValues;
    @Nullable
    private EnumSet<Relation.Cascade> cascadedTypes;

    /**
     * Default constructor.
     * @param type The type
     */
    public RuntimePersistentEntity(Class<T> type) {
        this(BeanIntrospection.getIntrospection(type));
    }

    /**
     * Default constructor.
     * @param introspection The introspection
     */
    public RuntimePersistentEntity(BeanIntrospection<T> introspection) {
        this(introspection, introspection.getBeanProperties());
    }

    /**
     * Default constructor.
     *
     * @param introspection The introspection
     * @param beanProperties The bean properties
     */
    public RuntimePersistentEntity(BeanIntrospection<T> introspection, Collection<BeanProperty<T, Object>> beanProperties) {
        super(introspection);
        ArgumentUtils.requireNonNull("introspection", introspection);
        this.introspection = introspection;
        Argument<?>[] creatorArguments = introspection.getConstructorArguments();
        // The introspection exposes a single creator, which may have been chosen for a purpose other than
        // persistence - for example a Jackson @JsonCreator that builds the bean from one JSON value. When that
        // creator cannot be mapped to the persisted properties, fall back to no-argument instantiation and
        // property setters. If there is no no-argument constructor either, fail here rather than later in
        // SqlResultEntityTypeMapper. See https://github.com/micronaut-projects/micronaut-data/issues/3752
        boolean creatorMapsToProperties = creatorMapsToProperties(creatorArguments, beanProperties);
        Set<String> constructorArgumentNames = creatorMapsToProperties
            ? Arrays.stream(creatorArguments).map(Argument::getName).collect(Collectors.toSet())
            : Set.of();
        RuntimePersistentProperty<T> version = null;
        List<RuntimePersistentProperty<T>> ids = new ArrayList<>(5);
        this.allPersistentProperties = new RuntimePersistentProperty[beanProperties.size()];
        this.persistentProperties = new RuntimePersistentProperty[beanProperties.size()];
        for (BeanProperty<T, Object> bp : beanProperties) {
            if (bp.hasStereotype(Transient.class)) {
                continue;
            }
            int propertyIndex = introspection.propertyIndexOf(bp.getName());
            if (bp.hasStereotype(Id.class)) {
                RuntimePersistentProperty<T> id;
                if (isEmbedded(bp)) {
                    id = new RuntimeEmbedded<>(this, bp, constructorArgumentNames.contains(bp.getName()));
                } else {
                    id = new RuntimePersistentProperty<>(this, bp, constructorArgumentNames.contains(bp.getName()));
                }
                ids.add(id);
                allPersistentProperties[propertyIndex] = id;
            } else if (bp.hasStereotype(Version.class)) {
                version = new RuntimePersistentProperty<>(this, bp, constructorArgumentNames.contains(bp.getName()));
                allPersistentProperties[propertyIndex] = version;
            } else {
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
                allPersistentProperties[propertyIndex] = prop;
                persistentProperties[propertyIndex] = prop;
            }
        }
        this.identity = ids.toArray(new RuntimePersistentProperty[0]);
        this.version = version;

        if (creatorMapsToProperties) {
            this.constructorArguments = new RuntimePersistentProperty[creatorArguments.length];
            for (int i = 0; i < creatorArguments.length; i++) {
                Argument<?> constructorArgument = creatorArguments[i];
                String argumentName = constructorArgument.getName();
                RuntimePersistentProperty<T> prop = getPropertyByName(argumentName);
                if (prop == null) {
                    throw new MappingException("Constructor argument [" + argumentName + "] for type [" + getName() + "] must have an associated getter");
                }
                this.constructorArguments[i] = prop;
            }
        } else {
            requireWritableProperties(creatorArguments);
            this.constructorArguments = new RuntimePersistentProperty[0];
        }
        this.aliasName = super.getAliasName();
    }

    /**
     * Can the creator exposed by the introspection be used to populate the persisted properties, meaning every one
     * of its arguments is a persisted property of this entity?
     *
     * @param creatorArguments The arguments of the creator exposed by the introspection
     * @param beanProperties   The bean properties this entity is built from
     * @return true if the creator can be used for persistence
     */
    private boolean creatorMapsToProperties(Argument<?>[] creatorArguments, Collection<BeanProperty<T, Object>> beanProperties) {
        if (creatorArguments.length == 0) {
            return true;
        }
        Set<String> propertyNames = new HashSet<>(beanProperties.size());
        for (BeanProperty<T, Object> bp : beanProperties) {
            if (!bp.hasStereotype(Transient.class)) {
                propertyNames.add(bp.getName());
            }
        }
        for (Argument<?> creatorArgument : creatorArguments) {
            if (!propertyNames.contains(creatorArgument.getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Falling back to no-argument instantiation is only possible when a no-argument constructor exists and every
     * persisted property can be set after the instance has been created. Constructor availability is checked through
     * reflection metadata only; user constructor code must not run during entity metadata initialization.
     * Failing here produces a better message than failing later while reading a result set.
     *
     * @param creatorArguments The arguments of the creator exposed by the introspection
     */
    private void requireWritableProperties(Argument<?>[] creatorArguments) {
        List<String> readOnly = new ArrayList<>(2);
        for (RuntimePersistentProperty<T> property : allPersistentProperties) {
            if (property != null && property.isReadOnly()) {
                readOnly.add(property.getName());
            }
        }
        if (!readOnly.isEmpty()) {
            throw new MappingException(unmappableCreatorMessage(creatorArguments,
                "and the properties " + readOnly + " cannot be set after construction."));
        }
        if (!hasNoArgumentConstructor()) {
            throw new MappingException(unmappableCreatorMessage(creatorArguments,
                "and no no-argument constructor exists."));
        }
    }

    private boolean hasNoArgumentConstructor() {
        for (Constructor<?> constructor : introspection.getBeanType().getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private String unmappableCreatorMessage(Argument<?>[] creatorArguments, String reason) {
        return "Type [" + getName() + "] is instantiated by the creator ["
            + Arrays.stream(creatorArguments).map(Argument::getName).collect(Collectors.joining(", "))
            + "], which doesn't map to the persisted properties, " + reason
            + " Micronaut Data needs either a creator whose arguments are all "
            + "persisted properties, or a no-argument constructor together with setters for every persisted property.";
    }

    @Override
    protected void logDebug(String message, Exception e) {
        LOG.debug(message, e);
    }

    private static EnumSet<Relation.Cascade> cascades(RuntimePersistentEntity<?> persistentEntity) {
        EnumSet<Relation.Cascade> cascades = EnumSet.noneOf(Relation.Cascade.class);
        for (RuntimeAssociation<?> association : persistentEntity.getAssociations()) {
            cascades.addAll(cascades(association));
        }
        cascades.remove(Relation.Cascade.NONE);
        if (cascades.remove(Relation.Cascade.ALL)) {
            EnumSet<Relation.Cascade> all = EnumSet.allOf(Relation.Cascade.class);
            all.remove(Relation.Cascade.ALL);
            all.remove(Relation.Cascade.NONE);
            return all;
        }
        return cascades;
    }

    private static EnumSet<Relation.Cascade> cascades(RuntimeAssociation<?> association) {
        if (association.getKind() == Relation.Kind.EMBEDDED) {
            return cascades(association.getAssociatedEntity());
        }
        return association.getCascadeTypes();
    }

    /**
     * Resolves a converter instance.
     * @param converterClass The converter class
     * @return converter instance
     */

    protected AttributeConverter<Object, Object> resolveConverter(Class<?> converterClass) {
        throw new MappingException("Converters not supported");
    }

    /**
     * Does cascade the persist to any of the associations.
     *
     * @return True if it does
     */
    public boolean cascadesPersist() {
        return getCascadedTypes().contains(Relation.Cascade.PERSIST);
    }

    /**
     * Does cascade the update to any of the associations.
     *
     * @return True if it does
     */
    public boolean cascadesUpdate() {
        return getCascadedTypes().contains(Relation.Cascade.UPDATE);
    }

    private EnumSet<Relation.Cascade> getCascadedTypes() {
        if (cascadedTypes == null) {
            cascadedTypes = cascades(this);
        }
        return cascadedTypes;
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

    @Override
    public String getName() {
        return introspection.getBeanType().getName();
    }

    @Override
    public boolean hasCompositeIdentity() {
        return identity.length > 1;
    }

    @Override
    public boolean hasIdentity() {
        return identity.length == 1;
    }

    @Override
    public boolean hasVersion() {
        return version != null;
    }

    @Override
    public RuntimePersistentProperty<T>[] getCompositeIdentity() {
        if (hasCompositeIdentity()) {
            return identity;
        }
        throw new IllegalStateException("Entity [" + getName() + "] doesn't have composite identity");
    }

    @Override
    public RuntimePersistentProperty<T> getIdentity() {
        if (hasIdentity()) {
            return identity[0];
        }
        if (hasCompositeIdentity()) {
            throw new IllegalStateException("Entity [" + getName() + "] has composite identity");
        }
        throw new IllegalStateException("Entity [" + getName() + "] doesn't have an identity");
    }

    @Override
    public RuntimePersistentProperty<T> getVersion() {
        if (hasVersion()) {
            return Objects.requireNonNull(version);
        }
        throw new IllegalStateException("Entity [" + getName() + "] doesn't have a version");
    }

    @Override
    public List<PersistentProperty> getIdentityProperties() {
        return List.of(identity);
    }

    /**
     * An alternative to {@link #getIdentityProperties()} but that returns {@link RuntimePersistentProperty}.
     * @return The identity properties
     */
    public List<RuntimePersistentProperty<T>> getRuntimeIdentityProperties() {
        return List.of(identity);
    }

    @Override
    public Collection<RuntimePersistentProperty<T>> getPersistentProperties() {
        if (persistentPropertiesValues == null) {
            persistentPropertiesValues = Arrays.stream(persistentProperties).filter(Objects::nonNull).toList();
        }
        return persistentPropertiesValues;
    }

    @Override
    public Collection<RuntimeAssociation<T>> getAssociations() {
        return (Collection<RuntimeAssociation<T>>) super.getAssociations();
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getPropertyByName(String name) {
        int propertyIndex = introspection.propertyIndexOf(name);
        if (propertyIndex == -1) {
            return null;
        }
        return allPersistentProperties[propertyIndex];
    }

    @Override
    @Nullable
    public RuntimePersistentProperty<T> getPropertyByNameIgnoreCase(String name) {
        for (RuntimePersistentProperty<T> property : allPersistentProperties) {
            if (property != null && property.getName().equalsIgnoreCase(name)) {
                return property;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getIdentityByName(String name) {
        return (RuntimePersistentProperty<T>) super.getIdentityByName(name);
    }

    @Override
    public List<String> getPersistentPropertyNames() {
        if (allPersistentPropertiesNames == null) {
            allPersistentPropertiesNames = Arrays.stream(allPersistentProperties)
                .filter(Objects::nonNull)
                .map(RuntimePersistentProperty::getName)
                .toList();
        }
        return allPersistentPropertiesNames;
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
            this.hasAutoPopulatedProperties = hasDirectAutoPopulated() || hasAutoPopulatedInEmbeddeds();
        }
        return this.hasAutoPopulatedProperties;
    }

    private boolean hasDirectAutoPopulated() {
        return Arrays.stream(allPersistentProperties)
            .filter(Objects::nonNull)
            .anyMatch(PersistentProperty::isAutoPopulated);
    }

    private boolean hasAutoPopulatedInEmbeddeds() {
        // Avoid RuntimeEntityRegistry lookups, keep track of visited to prevent cycles
        Set<Class<?>> visited = new HashSet<>();
        visited.add(getIntrospection().getBeanType());

        return getAssociations().stream()
            .filter(RuntimeAssociation::isEmbedded)
            .map(a -> a.getProperty().getType())
            .filter(visited::add)
            .anyMatch(type -> {
                BeanIntrospection<?> embeddedIntrospection = BeanIntrospection.getIntrospection(type);
                return hasAutoPopulatedInEmbedded(embeddedIntrospection, visited);
            });
    }

    /**
     * Recursively checks if the given embedded bean introspection has any auto-populated properties,
     * without going through the RuntimeEntityRegistry to avoid recursive map updates.
     */
    private static boolean hasAutoPopulatedInEmbedded(BeanIntrospection<?> introspection, Set<Class<?>> visited) {
        for (BeanProperty<?, ?> bp : introspection.getBeanProperties()) {
            if (bp.hasStereotype(Transient.class)) {
                continue;
            }
            // Check direct auto-populated (but not @GeneratedValue)
            var am = bp.getAnnotationMetadata();
            if (!am.hasAnnotation(GeneratedValue.class) && am.hasStereotype(AutoPopulated.class)) {
                return true;
            }
            // Recurse into nested embedded associations
            Relation.Kind kind = am.enumValue(Relation.class, Relation.Kind.class).orElse(null);
            if (kind == Relation.Kind.EMBEDDED) {
                Class<?> type = bp.getType();
                if (visited.add(type)) {
                    BeanIntrospection<?> ei = BeanIntrospection.getIntrospection(type);
                    if (hasAutoPopulatedInEmbedded(ei, visited)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
