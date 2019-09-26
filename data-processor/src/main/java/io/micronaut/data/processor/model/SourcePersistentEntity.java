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
package io.micronaut.data.processor.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationValue;
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
import java.util.stream.Collectors;

/**
 * An implementation of {@link PersistentEntity} that operates on the sources.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class SourcePersistentEntity extends AbstractPersistentEntity implements PersistentEntity, TypedElement {

    private final ClassElement classElement;
    private final Map<String, PropertyElement> beanProperties;
    private final SourcePersistentProperty[] id;
    private final SourcePersistentProperty version;
    private final Function<ClassElement, SourcePersistentEntity> entityResolver;
    private final List<SourcePersistentProperty> persistentProperties;

    /**
     * Default constructor.
     * @param classElement The class element
     * @param entityResolver The entity resolver to resolve any additional entities such as associations
     */
    public SourcePersistentEntity(
            @NonNull ClassElement classElement,
            @NonNull Function<ClassElement, SourcePersistentEntity> entityResolver) {
        super(classElement);
        this.entityResolver = entityResolver;
        this.classElement = classElement;
        final List<PropertyElement> beanProperties = classElement.getBeanProperties();
        this.beanProperties = new LinkedHashMap<>(beanProperties.size());
        List<SourcePersistentProperty> id = new ArrayList<>(2);
        SourcePersistentProperty version = null;
        for (PropertyElement beanProperty : beanProperties) {
            if (beanProperty.getName().equals("metaClass")) {
                continue;
            }
            if (beanProperty.hasStereotype(Transient.class)) {
                continue;
            }
            if (beanProperty.hasStereotype(Id.class)) {
                if (beanProperty.enumValue(Relation.class, Relation.Kind.class).map(k -> k == Relation.Kind.EMBEDDED).orElse(false)) {
                    id.add(new SourceEmbedded(this, beanProperty, entityResolver));
                } else {
                    id.add(new SourcePersistentProperty(this, beanProperty));
                }
            } else if (beanProperty.hasStereotype(Version.class)) {
                version = new SourcePersistentProperty(this, beanProperty);
            } else {
                this.beanProperties.put(beanProperty.getName(), beanProperty);
            }
        }

        this.version = version;
        this.id = id.toArray(new SourcePersistentProperty[0]);
        this.persistentProperties = this.beanProperties.values().stream().map(propertyElement -> {
            Optional<AnnotationValue<Relation>> relation = propertyElement.findAnnotation(Relation.class);
            if (relation.isPresent()) {
                Relation.Kind kind = relation.flatMap(av -> av.enumValue(Relation.Kind.class)).orElse(null);
                if (kind == Relation.Kind.EMBEDDED) {
                    if (!propertyElement.getType().hasStereotype(Embeddable.class)) {
                        throw new MappingException("Type [" + propertyElement.getType().getName()  + "] of property [" + propertyElement.getName() + "] of entity [" + getName() + "] is missing @Embeddable annotation. @Embedded fields can only be applied to types annotated with @Embeddable");
                    }
                    return new SourceEmbedded(this, propertyElement, entityResolver);
                } else {
                    return new SourceAssociation(this, propertyElement, entityResolver);
                }
            } else {
                SourcePersistentProperty pp = new SourcePersistentProperty(this, propertyElement);
                if (pp.getDataType() == DataType.ENTITY) {
                    return new SourceAssociation(this, propertyElement, entityResolver);
                }
                return pp;
            }
        })
        .collect(Collectors.toList());
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

    @Nullable
    @Override
    public PersistentProperty[] getCompositeIdentity() {
        return id;
    }

    @Nullable
    @Override
    public SourcePersistentProperty getIdentity() {
        if (ArrayUtils.isNotEmpty(id)) {
            return id[0];
        }
        return null;
    }

    @Nullable
    @Override
    public SourcePersistentProperty getVersion() {
        return version;
    }

    @NonNull
    @Override
    public List<SourcePersistentProperty> getPersistentProperties() {
        return persistentProperties;
    }

    @NonNull
    @Override
    public List<Association> getAssociations() {
        return persistentProperties.stream()
                .filter(bp -> bp instanceof Association)
                .map(bp -> (Association) bp)
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public List<Embedded> getEmbedded() {
        return persistentProperties.stream()
                .filter(p -> p instanceof Embedded)
                .map(p -> (Embedded) p)
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public SourcePersistentProperty getPropertyByName(String name) {
        if (StringUtils.isNotEmpty(name)) {
            final PropertyElement prop = beanProperties.get(name);
            if (prop != null) {
                if (prop.hasStereotype(Relation.class)) {
                    if (isEmbedded(prop)) {
                        return new SourceEmbedded(this, prop, entityResolver);
                    } else {
                        return new SourceAssociation(this, prop, entityResolver);
                    }
                } else {
                    return new SourcePersistentProperty(this, prop);
                }
            }

            if (ArrayUtils.isNotEmpty(id)) {
                SourcePersistentProperty persistentProp = Arrays.stream(id)
                        .filter(p -> p.getName().equals(name))
                        .findFirst()
                        .orElse(null);

                if (persistentProp != null) {
                    return persistentProp;
                }
            }

            if (version!=null && version.getName().equals(name))
                return version;
        }
        return null;
    }

    @NonNull
    @Override
    public List<String> getPersistentPropertyNames() {
        return Collections.unmodifiableList(new ArrayList<>(beanProperties.keySet()));
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
