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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.MappingException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;

import java.util.function.Function;

/**
 * Source code level implementation of {@link Association}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
class SourceAssociation extends SourcePersistentProperty implements Association {

    private final Function<ClassElement, SourcePersistentEntity> entityResolver;
    private final Relation.Kind kind;

    /**
     * Default constructor.
     * @param owner The owner
     * @param propertyElement The property element
     * @param entityResolver The entity resolver
     */
    SourceAssociation(SourcePersistentEntity owner, PropertyElement propertyElement, @NonNull Function<ClassElement, SourcePersistentEntity> entityResolver) {
        super(owner, propertyElement);
        this.entityResolver = entityResolver;
        this.kind = Association.super.getKind();
    }

    @NonNull
    @Override
    public Relation.Kind getKind() {
        return kind;
    }

    @Override
    public boolean isRequired() {
        return !isForeignKey() && super.isRequired();
    }

    @Override
    @NonNull
    public PersistentEntity getAssociatedEntity() {
        ClassElement type = getType();
        switch (getKind()) {
            case ONE_TO_MANY:
            case MANY_TO_MANY:
                ClassElement classElement = type.getFirstTypeArgument().orElse(null);
                if (classElement  != null) {
                    return entityResolver.apply(classElement);
                } else {
                    throw new MappingException("Collection association [" + getName() + "] of entity [" + getOwner().getName() + "] is not a collection type with a generic type argument that specifies another entity type to associate");
                }
            default:
                return entityResolver.apply(type);
        }
    }

}
