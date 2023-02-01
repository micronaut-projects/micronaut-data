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

import io.micronaut.data.annotation.repeatable.WhereSpecifications;

import java.lang.annotation.*;

/**
 * There {@code Where} annotation allows augmenting the {@code WHERE} statement of generated
 * queries with additional criterion.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Repeatable(WhereSpecifications.class)
@Inherited
public @interface Where {
    /**
     * The string value that represents the additional query criterion. For example: {@code enabled = true}
     *
     * <p>Note that if it may be required to specify the query alias in queries. For example: {@code book_.enabled = true}.
     * `@` can be used as a placeholder for the query's alias</p>
     *
     * <p>Parameterized variables can be specified using the dollar syntax: {@code book_.enabled = :enabled}. In
     * this case the parameter must be declared in the method signature a compilation error will occur.</p>
     *
     * <p>Use cases including soft-delete, multi-tenancy etc.</p>
     *
     * @return The additional query criterion.
     */
    String value();
}
