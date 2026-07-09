/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.TypeDef;

/**
 * Utility methods for explicit {@link TypeDef} declarations on persistent properties.
 */
@Internal
public final class TypeDefUtils {

    private TypeDefUtils() {
    }

    /**
     * @param property The persistent property
     * @param dataType The required data type
     * @return Whether the property directly declares the required type definition
     */
    public static boolean hasDeclaredTypeDef(PersistentProperty property, DataType dataType) {
        return property.getAnnotationMetadata().hasDeclaredAnnotation(TypeDef.class)
            && property.getAnnotationMetadata().enumValue(TypeDef.class, "type", DataType.class)
                .filter(dataType::equals)
                .isPresent();
    }
}
