/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.model;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;

/**
 * Source code level implementation of {@link Association}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
class SourceAssociation extends SourcePersistentProperty implements Association {

    /**
     * Default constructor.
     * @param owner The owner
     * @param propertyElement The property element
     */
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
