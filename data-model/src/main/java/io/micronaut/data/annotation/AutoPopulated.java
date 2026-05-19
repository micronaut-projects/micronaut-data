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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta annotation to identity annotations that are auto-populated by the Micronaut Data.
 *
 * @see DateCreated
 * @see DateUpdated
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface AutoPopulated {
    /**
     * The annotation name.
     */
    String NAME = AutoPopulated.class.getName();

    String UPDATABLE = "updatable";

    /**
     * The metadata key for {@link #skipIfPresent()}.
     */
    String SKIP_IF_PRESENT = "skipIfPresent";

    /**
     * @return Whether the property can be updated following an insert
     */
    boolean updatable() default true;

    /**
     * Controls whether auto-population should skip if a non-null value is already present.
     *
     * Default is false to preserve the existing behavior of always generating a value.
     *
     * @return {@code true} if auto-population should be skipped if a non-null value is present, {@code false} otherwise
     * @since 5.0
     */
    boolean skipIfPresent() default false;
}
