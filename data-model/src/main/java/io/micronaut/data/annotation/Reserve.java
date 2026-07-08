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
 * Declares an Oracle lock-free reservation delta update for a {@link Reservable} property.
 *
 * @author radovanradic
 * @since 5.1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Reserve {

    /**
     * @return The reservable property to update
     */
    String property();

    /**
     * @return The reservation delta operation
     */
    Operation operation();

    /**
     * The supported reservation delta operations.
     */
    enum Operation {
        /** Add the delta to the current value. */
        INCREMENT,
        /** Subtract the delta from the current value. */
        DECREMENT
    }
}
