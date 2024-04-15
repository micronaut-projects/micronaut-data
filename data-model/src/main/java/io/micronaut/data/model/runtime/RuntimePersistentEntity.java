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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.*;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(RuntimePersistentEntity.class);

    private final BeanIntrospection<T> introspection;
    private final RuntimePersistentProperty<T>[] identity;

    // Subset of all introspections properties with the same order as in BeanIntrospection with nulls if excluded.
    private final RuntimePersistentProperty<T>[] allPersistentProperties;
    private final RuntimePersistentProperty<T>[] persistentProperties;

    private final RuntimePersistentProperty<T>[] constructorArguments;
    private final String aliasName;
    private final RuntimePersistentProperty<T> version;
    private Boolean hasAutoPopulatedProperties;

    private List<String> allPersistentPropertiesNames;
    private List<RuntimePersistentProperty<T>> persistentPropertiesValues;

    private EnumSet<Relation.Cascade> cascadedTypes;

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
        this(introspection, introspection.getBeanProperties());
    }

    /**
     * Default constructor.
     *
     * @param introspection The introspection
     * @param beanProperties The bean properties
     */
    public RuntimePersistentEntity(@NonNull BeanIntrospection<T> introspection, Collection<BeanProperty<T, Object>> beanProperties) {
        super(introspection);
        ArgumentUtils.requireNonNull("introspection", introspection);
        this.introspection = introspection;
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        Set<String> constructorArgumentNames = Arrays.stream(constructorArguments).map(Argument::getName).collect(Collectors.toSet());
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

        this.constructorArguments = new RuntimePersistentProperty[constructorArguments.length];
        for (int i = 0; i < constructorArguments.length; i++) {
            Argument<?> constructorArgument = constructorArguments[i];
            String argumentName = constructorArgument.getName();
            RuntimePersistentProperty<T> prop = getPropertyByName(argumentName);
            if (prop == null) {
                throw new MappingException("Constructor argument [" + argumentName + "] for type [" + getName() + "] must have an associated getter");
            }
            this.constructorArguments[i] = prop;
        }
        this.aliasName = super.getAliasName();
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
    @NonNull
    protected AttributeConverter<Object, Object> resolveConverter(@NonNull Class<?> converterClass) {
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

    @Override
    public boolean hasCompositeIdentity() {
        return identity.length > 1;
    }

    @Override
    public boolean hasIdentity() {
        return identity.length == 1;
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T>[] getCompositeIdentity() {
        return identity.length > 1 ? identity : null;
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getIdentity() {
        return identity.length == 1 ? identity[0] : null;
    }

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getVersion() {
        return version;
    }

    @NonNull
    @Override
    public Collection<RuntimePersistentProperty<T>> getPersistentProperties() {
        if (persistentPropertiesValues == null) {
            persistentPropertiesValues = Collections.unmodifiableList(Arrays.stream(persistentProperties).filter(Objects::nonNull).collect(Collectors.toList()));
        }
        return persistentPropertiesValues;
    }

    @NonNull
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

    @Nullable
    @Override
    public RuntimePersistentProperty<T> getIdentityByName(String name) {
        return (RuntimePersistentProperty<T>) super.getIdentityByName(name);
    }

    @NonNull
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
            this.hasAutoPopulatedProperties = Arrays.stream(allPersistentProperties)
                    .filter(Objects::nonNull)
                    .anyMatch(PersistentProperty::isAutoPopulated);
        }
        return this.hasAutoPopulatedProperties;
    }

}
