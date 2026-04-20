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

import java.lang.annotation.*;
import java.time.temporal.ChronoUnit;

/**
 * Can be applied to date type to indicate the property should be populated when it was last updated.
 *
 * @author graemerocher
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Documented
@AutoPopulated
public @interface DateUpdated {
    /**
     * The date created name.
     */
    String NAME = DateUpdated.class.getName();

    /**
     * The metadata key for {@link #skipIfPresent()}.
     */
    String SKIP_IF_PRESENT = "skipIfPresent";

    /**
     * When true, an existing non-null value will be preserved and not overwritten during auto-population on insert.
     * This is intended for insert-time use cases such as backfills or imports where a caller already has a last-updated value.
     * On later updates, {@code @DateUpdated} keeps its normal semantics and is always refreshed by Micronaut Data
     * regardless of this flag or the current value.
     * Default is {@code false} to preserve existing behavior.
     *
     * @return true if the existing value should be preserved, false otherwise
     * @since 5.0
     */
    boolean skipIfPresent() default false;

    /**
     * Allows to truncate the auto set date value.
     *
     * @return the truncated to constant
     * @since 3.4.2
     */
    ChronoUnit truncatedTo() default ChronoUnit.FOREVER;
}
