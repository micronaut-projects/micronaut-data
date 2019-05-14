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
package io.micronaut.data.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.data.annotation.GeneratedValue;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Models a persistent property. That is a property that is saved and retrieved from the database.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PersistentProperty extends AnnotationMetadataProvider {

    /**
     * The name of the property
     * @return The property name
     */
    @NonNull String getName();

    /**
     * The name with the first letter in upper case as per Java bean conventions
     * @return The capitilized name
     */
    default @NonNull String getCapitilizedName() {
        return NameUtils.capitalize(getName());
    }

    /**
     * The type of the property
     * @return The property type
     */
    @NonNull String getTypeName();


    /**
     * Obtains the owner of this persistent property
     *
     * @return The owner
     */
    @NonNull PersistentEntity getOwner();

    /**
     * Whether the property can be set to null
     *
     * @return True if it can
     */
    default boolean isNullable() {
        return getAnnotationMetadata().hasAnnotation(Nullable.class);
    }

    /**
     * Whether the property is read-only, for example for generated values.
     * @return True if it is read-only
     */
    default boolean isReadOnly() {
        return isGenerated();
    }

    /**
     * @return Whether this property is inherited
     */
    default boolean isInherited() {
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

    default boolean isAssignable(@NonNull Class<?> type) {
        return isAssignable(type.getName());
    }
}
