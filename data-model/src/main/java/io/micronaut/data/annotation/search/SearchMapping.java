/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.annotation.search;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field mapping of a property on a document store, as the JSON of the field's entry in the index
 * mapping, replacing the one generated from {@link SearchField}: a {@code text} field with a custom
 * analyzer and {@code fielddata}, a {@code dense_vector} with its own options. The SQL stores and MongoDB
 * ignore it.
 *
 * <p>The JSON is trusted configuration and is sent as written.</p>
 *
 * @since 5.4.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Experimental
public @interface SearchMapping {

    /**
     * @return The field mapping as JSON
     */
    String value();
}
