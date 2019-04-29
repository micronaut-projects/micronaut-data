package io.micronaut.data.model.runtime;

import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;

import javax.annotation.Nullable;

class RuntimeAssociation extends RuntimePersistentProperty implements Association {

    public RuntimeAssociation(PersistentEntity owner, BeanProperty<?, ?> property) {
        super(owner, property);
    }

    @Nullable
    @Override
    public PersistentEntity getAssociatedEntity() {
        return PersistentEntity.of(getProperty().getType());
    }
}
