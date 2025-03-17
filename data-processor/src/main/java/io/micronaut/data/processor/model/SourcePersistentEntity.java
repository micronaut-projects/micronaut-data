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
package io.micronaut.data.processor.model;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.*;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.*;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;

import java.util.*;
import java.util.function.Function;

/**
 * An implementation of {@link PersistentEntity} that operates on the sources.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class SourcePersistentEntity extends AbstractPersistentEntity implements PersistentEntity, TypedElement {

    private final ClassElement classElement;
    private final SourcePersistentProperty[] ids;
    private final SourcePersistentProperty version;
    private final Map<String, SourcePersistentProperty> persistentProperties;
    private final Map<String, SourcePersistentProperty> allPersistentProperties;

    private List<String> allPersistentPropertiesNames;
    private List<SourcePersistentProperty> persistentPropertiesValues;

    /**
     * Default constructor.
     * @param classElement The class element
     * @param entityResolver The entity resolver to resolve any additional entities such as associations
     */
    public SourcePersistentEntity(
            @NonNull ClassElement classElement,
            @NonNull Function<ClassElement, SourcePersistentEntity> entityResolver) {
        super(classElement);
        this.classElement = classElement;
        final List<PropertyElement> beanProperties = classElement.getBeanProperties();
        this.allPersistentProperties = new LinkedHashMap<>(beanProperties.size());
        this.persistentProperties = new LinkedHashMap<>(beanProperties.size());
        List<SourcePersistentProperty> ids = new ArrayList<>(2);
        SourcePersistentProperty version = null;
        for (PropertyElement propertyElement : beanProperties) {
            if (propertyElement.getName().equals("metaClass")) {
                continue;
            }
            if (propertyElement.hasStereotype(Transient.class)) {
                continue;
            }
            if (propertyElement.hasStereotype(Id.class)) {
                SourcePersistentProperty id;
                if (isEmbedded(propertyElement)) {
                    id = new SourceEmbedded(this, propertyElement, entityResolver);
                } else {
                    id = new SourcePersistentProperty(this, propertyElement);
                }
                ids.add(id);
                allPersistentProperties.put(id.getName(), id);
            } else if (propertyElement.hasStereotype(Version.class)) {
                version = new SourcePersistentProperty(this, propertyElement);
                if (hasAnnotation(JsonView.class)) {
                    throw new MappingException("@JsonView mapped entities do not support @Version fields.");
                }
                allPersistentProperties.put(version.getName(), version);
            } else {
                SourcePersistentProperty prop;
                if (propertyElement.hasAnnotation(Relation.class)) {
                    if (isEmbedded(propertyElement)) {
                        if (!propertyElement.getType().hasStereotype(Embeddable.class)) {
                            throw new MappingException("Type [" + propertyElement.getType().getName() + "] of property [" + propertyElement.getName() + "] of entity [" + getName() + "] is missing @Embeddable annotation. @Embedded fields can only be applied to types annotated with @Embeddable");
                        }
                        prop = new SourceEmbedded(this, propertyElement, entityResolver);
                    } else {
                        prop = new SourceAssociation(this, propertyElement, entityResolver);
                    }
                } else {
                    prop = new SourcePersistentProperty(this, propertyElement);
                    if (prop.getDataType() == DataType.ENTITY) {
                        prop = new SourceAssociation(this, propertyElement, entityResolver);
                    }
                }
                allPersistentProperties.put(prop.getName(), prop);
                persistentProperties.put(prop.getName(), prop);
            }
        }
        this.ids = ids.stream().toArray(SourcePersistentProperty[]::new);
        this.version = version;
    }

    @NonNull
    @Override
    public String getName() {
        return classElement.getName();
    }

    @Override
    public String getSimpleName() {
        return classElement.getSimpleName();
    }

    @Override
    public boolean isProtected() {
        return classElement.isProtected();
    }

    @Override
    public boolean isPublic() {
        return classElement.isPublic();
    }

    @Override
    public Object getNativeType() {
        return classElement.getNativeType();
    }

    @Override
    public boolean hasCompositeIdentity() {
        return ids.length > 1;
    }

    @Override
    public boolean hasIdentity() {
        return ids.length == 1;
    }

    @Nullable
    @Override
    public SourcePersistentProperty[] getCompositeIdentity() {
        return ids.length > 1 ? ids : null;
    }

    @Nullable
    @Override
    public SourcePersistentProperty getIdentity() {
        return ids.length == 1 ? ids[0] : null;
    }

    @Override
    public List<PersistentProperty> getIdentityProperties() {
        return List.of(ids);
    }

    @Nullable
    @Override
    public SourcePersistentProperty getVersion() {
        return version;
    }

    @NonNull
    @Override
    public List<SourcePersistentProperty> getPersistentProperties() {
        if (persistentPropertiesValues == null) {
            persistentPropertiesValues = Collections.unmodifiableList(new ArrayList<>(persistentProperties.values()));
        }
        return persistentPropertiesValues;
    }

    @Nullable
    @Override
    public SourcePersistentProperty getPropertyByName(String name) {
        if (StringUtils.isNotEmpty(name)) {
            return allPersistentProperties.get(name);
        }
        return null;
    }

    @Nullable
    @Override
    public SourcePersistentProperty getIdentityByName(String name) {
        return (SourcePersistentProperty) super.getIdentityByName(name);
    }

    /**
     * Obtains a PersistentProperty representing id or version property by name.
     *
     * @param name The name of the id or version property
     * @return The PersistentProperty used as id or version or null if it doesn't exist
     */
    public SourcePersistentProperty getIdOrVersionPropertyByName(String name) {
        if (ArrayUtils.isNotEmpty(ids)) {
            SourcePersistentProperty persistentProp = Arrays.stream(ids)
                    .filter(p -> p.getName().equals(name))
                    .findFirst()
                    .orElse(null);

            if (persistentProp != null) {
                return persistentProp;
            }
        }

        if (version != null && version.getName().equals(name)) {
            return version;
        }

        return null;
    }

    @NonNull
    @Override
    public List<String> getPersistentPropertyNames() {
        if (allPersistentPropertiesNames == null) {
            allPersistentPropertiesNames = Collections.unmodifiableList(new ArrayList<>(allPersistentProperties.keySet()));
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

    /**
     * @return The class element
     */
    public ClassElement getClassElement() {
        return classElement;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        return classElement;
    }

    private boolean isEmbedded(PropertyElement bp) {
        return bp.enumValue(Relation.class, Relation.Kind.class).orElse(null) == Relation.Kind.EMBEDDED;
    }

    @Override
    public String toString() {
        return getName();
    }
}
