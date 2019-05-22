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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;

import java.util.Objects;

/**
 * Source code level implementation of {@link PersistentProperty}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public class SourcePersistentProperty implements PersistentProperty, TypedElement {

    private final SourcePersistentEntity owner;
    private final PropertyElement propertyElement;

    /**
     * Default constructor.
     *
     * @param owner The owner
     * @param propertyElement The property element
     */
    SourcePersistentProperty(SourcePersistentEntity owner, PropertyElement propertyElement) {
        this.owner = owner;
        this.propertyElement = propertyElement;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SourcePersistentProperty that = (SourcePersistentProperty) o;
        return owner.equals(that.owner) &&
                propertyElement.getName().equals(that.propertyElement.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, propertyElement.getName());
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return propertyElement.getAnnotationMetadata();
    }

    @NonNull
    @Override
    public String getName() {
        return propertyElement.getName();
    }

    @Override
    public boolean isProtected() {
        return propertyElement.isProtected();
    }

    @Override
    public boolean isPublic() {
        return propertyElement.isPublic();
    }

    @Override
    public Object getNativeType() {
        return propertyElement.getNativeType();
    }

    @NonNull
    @Override
    public String getTypeName() {
        final ClassElement type = propertyElement.getType();
        if (type == null) {
            return Object.class.getName();
        }
        return type.getName();
    }

    @NonNull
    @Override
    public PersistentEntity getOwner() {
        return owner;
    }

    @Override
    public boolean isAssignable(@NonNull String type) {
        ClassElement t = getType();
        return t != null && t.isAssignable(type);
    }

    /**
     * @return The property element.
     */
    public @NonNull PropertyElement getPropertyElement() {
        return propertyElement;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        return propertyElement.getType();
    }
}
