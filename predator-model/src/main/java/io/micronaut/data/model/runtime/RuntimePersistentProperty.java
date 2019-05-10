package io.micronaut.data.model.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;

/**
 * A runtime representation of {@link PersistentProperty}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
class RuntimePersistentProperty implements PersistentProperty {

    private final PersistentEntity owner;
    private final BeanProperty<?, ?> property;

    /**
     * Default constructor.
     * @param owner The owner
     * @param property The property
     */
    RuntimePersistentProperty(PersistentEntity owner, BeanProperty<?, ?> property) {
        this.owner = owner;
        this.property = property;
    }

    @NonNull
    @Override
    public String getName() {
        return property.getName();
    }

    @NonNull
    @Override
    public String getTypeName() {
        return property.getType().getName();
    }

    @NonNull
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
