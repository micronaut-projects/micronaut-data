package io.micronaut.data.processor.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An implementation of {@link PersistentEntity} that operates on the sources.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class SourcePersistentEntity implements PersistentEntity, TypedElement {

    private final ClassElement classElement;
    private final Map<String, PropertyElement> beanProperties;
    private final SourcePersistentProperty[] id;
    private final SourcePersistentProperty version;

    /**
     * Default constructor.
     * @param classElement The class element
     */
    public SourcePersistentEntity(@NonNull ClassElement classElement) {
        this.classElement = classElement;
        final List<PropertyElement> beanProperties = classElement.getBeanProperties();
        this.beanProperties = new LinkedHashMap<>(beanProperties.size());
        List<SourcePersistentProperty> id = new ArrayList<>(2);
        SourcePersistentProperty version = null;
        for (PropertyElement beanProperty : beanProperties) {
            if (beanProperty.isReadOnly() || beanProperty.hasStereotype(Transient.class)) {
                continue;
            }
            if (beanProperty.hasStereotype(Id.class)) {
                id.add(new SourcePersistentProperty(this, beanProperty));
            } else if (beanProperty.hasStereotype(Version.class)) {
                version = new SourcePersistentProperty(this, beanProperty);
            } else {
                this.beanProperties.put(beanProperty.getName(), beanProperty);
            }
        }

        this.version = version;
        this.id = id.toArray(new SourcePersistentProperty[0]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SourcePersistentEntity that = (SourcePersistentEntity) o;
        return classElement.getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return classElement.getName().hashCode();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return classElement.getAnnotationMetadata();
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
    public List<PersistentProperty> getPersistentProperties() {
        return beanProperties.values().stream().map(propertyElement ->
                new SourcePersistentProperty(this, propertyElement)
        )
        .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public List<Association> getAssociations() {
        return beanProperties.values().stream()
                .filter(bp -> bp.hasStereotype(Relation.class))
                .map(propertyElement -> new SourceAssociation(this, propertyElement))
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public List<Embedded> getEmbedded() {
        return beanProperties.values().stream()
                .filter(this::isEmbedded)
                .map(propertyElement -> new SourceEmbedded(this, propertyElement))
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
                        return new SourceEmbedded(this, prop);
                    } else {
                        return new SourceAssociation(this, prop);
                    }
                } else {
                    return new SourcePersistentProperty(this, prop);
                }
            }
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

    public ClassElement getClassElement() {
        return classElement;
    }

    private boolean isEmbedded(PropertyElement bp) {
        return bp.hasStereotype(Relation.class) && bp.getValue(Relation.class, "kind", Relation.Kind.class).orElse(null) == Relation.Kind.EMBEDDED;
    }

    @Nullable
    @Override
    public ClassElement getType() {
        return classElement;
    }
}
