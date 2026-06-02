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
package io.micronaut.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the spatial reference system identifier to use when persisting geometry values.
 *
 * @since 5.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD})
@Documented
public @interface Srid {

    /**
     * @return The spatial reference system identifier.
     */
    int value();

    /**
     * @return The coordinate reference system type.
     * @since 5.0.4
     */
    CrsType type() default CrsType.PROJECTED;

    /**
     * The coordinate reference system type.
     *
     * @since 5.0.4
     */
    enum CrsType {
        /**
         * Geographic coordinate reference system.
         */
        GEOGRAPHIC,
        /**
         * Projected coordinate reference system.
         */
        PROJECTED
    }
}
