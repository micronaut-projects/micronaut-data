package io.micronaut.data.processor.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
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
        ClassElement type = getType();
        if (type == null) {
            return null;
        }
        switch (getKind()) {
            case ONE_TO_MANY:
            case MANY_TO_MANY:
                ClassElement classElement = type.getFirstTypeArgument().orElse(null);
                if (classElement  != null) {
                    return new SourcePersistentEntity(classElement);
                }
            default:
                return new SourcePersistentEntity(type);
        }
    }

}
