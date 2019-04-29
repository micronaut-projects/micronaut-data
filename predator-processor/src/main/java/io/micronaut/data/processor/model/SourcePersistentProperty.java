package io.micronaut.data.processor.model;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;

import javax.annotation.Nonnull;

@Internal
class SourcePersistentProperty implements PersistentProperty {

    private final SourcePersistentEntity owner;
    private final PropertyElement propertyElement;

    SourcePersistentProperty(SourcePersistentEntity owner, PropertyElement propertyElement) {
        this.owner = owner;
        this.propertyElement = propertyElement;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return propertyElement.getAnnotationMetadata();
    }

    @Nonnull
    @Override
    public String getName() {
        return propertyElement.getName();
    }

    @Nonnull
    @Override
    public String getTypeName() {
        final ClassElement type = propertyElement.getType();
        if (type == null) {
            return Object.class.getName();
        }
        return type.getName();
    }

    @Nonnull
    @Override
    public PersistentEntity getOwner() {
        return owner;
    }

    public PropertyElement getPropertyElement() {
        return propertyElement;
    }
}
