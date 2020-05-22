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
package io.micronaut.data.annotation;

import io.micronaut.data.annotation.repeatable.TypeDefinitions;
import io.micronaut.data.model.DataType;

import java.lang.annotation.*;

/**
 * Type definitions allow associated existing types with a specify {@link DataType}. Can be applied
 * as a stereotype (meta-annotation) to other {@link Repository} and/or {@link MappedEntity} to provide
 * additional type information for custom types.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
@Documented
@Repeatable(TypeDefinitions.class)
public @interface TypeDef {
    /**
     * The data type.
     * @return The type
     */
    DataType type();

    /**
     * @return The classes for this data type.
     */
    Class[] classes() default {};

    /**
     * @return The class or parameter names for this data type.
     */
    String[] names() default {};
}
