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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final DataType dataType;
    private final ClassElement type;
    private final String converterClassName;
    private final String alias;

    /**
     * Default constructor.
     *
     * @param owner The owner
     * @param propertyElement The property element
     */
    SourcePersistentProperty(SourcePersistentEntity owner, PropertyElement propertyElement) {
        this.owner = owner;
        this.propertyElement = propertyElement;
        this.type = propertyElement.getGenericType();
        this.dataType = computeDataType(propertyElement);
        this.converterClassName = propertyElement.stringValue(MappedProperty.class, "converter").orElse(null);
        this.alias = getAnnotationMetadata().stringValue(MappedProperty.class, MappedProperty.ALIAS).orElse("");
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public boolean isOptional() {
        return propertyElement.isNullable();
    }

    private DataType computeDataType(PropertyElement propertyElement) {
        if (this instanceof Association) {
            return DataType.ENTITY;
        } else {
            AnnotationMetadata annotationMetadata = propertyElement.getAnnotationMetadata();
            return annotationMetadata.enumValue(MappedProperty.class, "type", DataType.class)
                    .orElseGet(() -> {
                        DataType dt = annotationMetadata.getValue(TypeDef.class, "type", DataType.class).orElse(null);
                        if (dt != null) {
                            return dt;
                        } else {
                            if (isEnum()) {
                                return DataType.STRING;
                            } else {
                                return TypeUtils.resolveDataType(type, Collections.emptyMap());
                            }
                        }
                    });
        }
    }

    @Override
    public boolean isEnum() {
        return type.isEnum();
    }

    @Override
    public List<EnumConstant> getEnumConstants() {
        if (type instanceof EnumElement enumElement) {
            List<EnumConstant> list = new ArrayList<>();
            int i = 0;
            for (EnumConstantElement e : enumElement.elements()) {
                int finalI = i;
                EnumConstant enumConstant = new EnumConstant() {
                    @Override
                    public String name() {
                        return e.getName();
                    }

                    @Override
                    public int ordinal() {
                        return finalI;
                    }
                };
                list.add(enumConstant);
                i++;
            }
            return list;
        }
        return List.of();
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
    public DataType getDataType() {
        return dataType;
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
        return t.isAssignable(type);
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
        return type;
    }

    @NonNull
    @Override
    public String getPersistedName() {
        return owner.getNamingStrategy().mappedName(this);
    }

    /**
     * Returns converter class name if present.
     * @return the converter's class name
     */
    @Nullable
    public String getConverterClassName() {
        return converterClassName;
    }

    @Override
    public String toString() {
        return getOwner().getName() + "(" + getTypeName() + " " + getName() + ")";
    }
}
