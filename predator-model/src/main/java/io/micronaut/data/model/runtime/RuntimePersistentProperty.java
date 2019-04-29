package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;

import javax.annotation.Nonnull;

class RuntimePersistentProperty implements PersistentProperty {

    private final PersistentEntity owner;
    private final BeanProperty<?, ?> property;

    RuntimePersistentProperty(PersistentEntity owner, BeanProperty<?, ?> property) {
        this.owner = owner;
        this.property = property;
    }

    @Nonnull
    @Override
    public String getName() {
        return property.getName();
    }

    @Nonnull
    @Override
    public String getTypeName() {
        return property.getType().getName();
    }

    @Nonnull
    @Override
    public PersistentEntity getOwner() {
        return owner;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return property.getAnnotationMetadata();
    }

    public BeanProperty<?, ?> getProperty() {
        return property;
    }
}
