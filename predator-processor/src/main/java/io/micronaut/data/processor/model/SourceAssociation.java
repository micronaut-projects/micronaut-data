package io.micronaut.data.processor.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;

@Internal
class SourceAssociation extends SourcePersistentProperty implements Association {
    SourceAssociation(SourcePersistentEntity owner, PropertyElement propertyElement) {
        super(owner, propertyElement);
    }

    @Override
    public PersistentEntity getAssociatedEntity() {
        ClassElement type = getPropertyElement().getType();
        if (type != null) {
            return new SourcePersistentEntity(type);
        }
        return null;
    }

}
