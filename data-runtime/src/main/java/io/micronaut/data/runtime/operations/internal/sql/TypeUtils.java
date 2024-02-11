/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

/**
 * The type utils for DTO and persistent entity type.
 */
@Internal
@SuppressWarnings({"java:S1872"})
final class TypeUtils {

    private TypeUtils() {
    }

    /**
     * Return true if the left type is compatible or can be assigned to the right type.
     * @param leftType The left type
     * @param rightType The right type
     * @return True if they are
     */
    static boolean areTypesCompatible(Class<?> leftType, Class<?> rightType) {
        String rightTypeName = rightType.getName();
        if (leftType.getName().equals(rightTypeName)) {
            return true;
        } else if (leftType.isInstance(rightTypeName)) {
            return true;
        } else {
            if (isNumber(leftType) && isNumber(rightType)) {
                return true;
            } else {
                return isBoolean(leftType) && isBoolean(rightType);
            }
        }
    }

    /**
     * Checks whether DTO type and given persistent entity type are compatible.
     * It means DTO types will have corresponding compatible fields in the persistent entity.
     *
     * @param dtoType The DTO type
     * @param entityType The persistent entity type
     * @return true if types are compatible
     */
    static boolean isDtoCompatibleWithEntity(@NonNull Class<?> dtoType, @NonNull Class<?> entityType) {
        // If DTO type not introspected it is going to fail and throw error to the user
        // that type must be @Introspected
        BeanIntrospection<?> dto = BeanIntrospection.getIntrospection(dtoType);
        RuntimePersistentEntity<?> entity = new RuntimePersistentEntity<>(entityType);
        for (BeanProperty dtoProperty : dto.getBeanProperties()) {
            String propertyName = dtoProperty.getName();
            // ignore Groovy meta class
            if ("metaClass".equals(propertyName) || dtoProperty.getType().getName().equals("groovy.lang.MetaClass")) {
                continue;
            }
            RuntimePersistentProperty<?> pp = entity.getPropertyByName(propertyName);

            if (pp == null) {
                pp = entity.getIdOrVersionPropertyByName(propertyName);
            }

            if (pp == null) {
                // Property is not present in entity
                return false;
            }

            Class<?> dtoPropertyType = dtoProperty.getType();
            if (dtoPropertyType.getName().equals("java.lang.Object") || dtoPropertyType.getName().equals("java.lang.String")) {
                // Convert anything to a string or an object
                continue;
            }
            boolean compatibleTypes = areTypesCompatible(dtoPropertyType, pp.getType());
            // Check if these are compatible non-simple field types (kind of nested DTOs)
            if (!compatibleTypes && !isDtoCompatibleWithEntity(dtoPropertyType, pp.getType())) {
                // DTO Property is not compatible with equivalent property of type declared in entity
                return false;
            }
        }
        return true;
    }

    private static boolean isNumber(@Nullable Class<?> type) {
        if (type == null) {
            return false;
        }
        if (type.isPrimitive()) {
            return ClassUtils.getPrimitiveType(type.getName()).map(aClass ->
                Number.class.isAssignableFrom(ReflectionUtils.getWrapperType(aClass))
            ).orElse(false);
        } else {
            return type.isInstance(Number.class);
        }
    }

    private static boolean isBoolean(@Nullable Class<?> type) {
        return type != null &&
            (type.isInstance(Boolean.class) || (type.isPrimitive() && type.getName().equals("boolean")));
    }
}
