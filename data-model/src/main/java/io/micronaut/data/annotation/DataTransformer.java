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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Generic version of allowing transformations to be applied when reading or writing
 * data to and from the a database. Inspired by Hibernate's <code>ColumnTransformer</code> concept.
 *
 * @author graemerocher
 * @since 1.0
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
public @interface DataTransformer {
    /**
     * @return An expression used to read a value of the database.
     */
    String read() default "";

    /**
     * An expression use to write a value to the database.
     *
     * @return The expression
     */
    String write() default "";
}
