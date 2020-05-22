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
package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.TypeDef;


/**
 * Models a persistent property. That is a property that is saved and retrieved from the database.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PersistentProperty extends PersistentElement {

    /**
     * The name of the property.
     * @return The property name
     */
    @NonNull String getName();

    /**
     * The name with the first letter in upper case as per Java bean conventions.
     * @return The capitilized name
     */
    default @NonNull String getCapitilizedName() {
        return NameUtils.capitalize(getName());
    }

    /**
     * The type of the property.
     * @return The property type
     */
    @NonNull String getTypeName();

    /**
     * Obtains the owner of this persistent property.
     *
     * @return The owner
     */
    @NonNull PersistentEntity getOwner();

    /**
     * Whether the property can be set to null.
     *
     * @return True if it can
     */
    default boolean isOptional() {
        return isNullableMetadata(getAnnotationMetadata());
    }

    /**
     * Whether a property is required to be specified. This returns
     * false if the property is both not nullable and not generated.
     *
     * @see #isOptional()
     * @see #isGenerated()
     * @return True if the property is required
     */
    default boolean isRequired() {
       return !isOptional() &&
               !isGenerated() &&
               !getAnnotationMetadata().hasStereotype(AutoPopulated.class);
    }

    /**
     * Whether the property is read-only, for example for generated values.
     * @return True if it is read-only
     */
    default boolean isReadOnly() {
        return isGenerated();
    }

    /**
     * @return Is the property also a constructor argument.
     */
    default boolean isConstructorArgument() {
        return false;
    }

    /**
     * Whether the property is generated.
     *
     * @return True if is generated
     */
    default boolean isGenerated() {
        return getAnnotationMetadata().hasAnnotation(GeneratedValue.class);
    }

    /**
     * Is the property assignable to the given type name.
     * @param type The type name
     * @return True if it is
     */
    boolean isAssignable(@NonNull String type);

    /**
     * Is the property assignable to the given type.
     * @param type The type
     * @return True it is
     */
    default boolean isAssignable(@NonNull Class<?> type) {
        return isAssignable(type.getName());
    }

    /**
     * @return The data type
     */
    default DataType getDataType() {
        if (this instanceof Association) {
            return DataType.ENTITY;
        } else {
            AnnotationMetadata annotationMetadata = getAnnotationMetadata();
            return annotationMetadata.enumValue(MappedProperty.class, "type", DataType.class)
                    .orElseGet(() -> {
                        DataType dt = annotationMetadata.getValue(TypeDef.class, "type", DataType.class).orElse(null);
                        if (dt != null) {
                            return dt;
                        } else {
                            if (isEnum()) {
                                return DataType.STRING;
                            } else {
                                return DataType.OBJECT;
                            }
                        }
                    });
        }
    }

    /**
     * @return Returns whether the property is an enum.
     */
    default boolean isEnum() {
        return false;
    }

    /**
     * Return whether the metadata indicates the instance is nullable.
     * @param metadata The metadata
     * @return True if it is nullable
     */
    static boolean isNullableMetadata(@NonNull AnnotationMetadata metadata) {
        return metadata
                .getDeclaredAnnotationNames()
                .stream()
                .anyMatch(n -> NameUtils.getSimpleName(n).equalsIgnoreCase("nullable"));
    }

}
