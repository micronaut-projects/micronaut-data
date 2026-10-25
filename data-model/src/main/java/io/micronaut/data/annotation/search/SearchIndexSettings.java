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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The index settings of a {@link Searchable} entity on a document store, as the JSON of the create-index
 * request's {@code settings}: shards, replicas, custom analyzers. The settings are merged over the store's
 * defaults when schema generation or {@code SearchIndexOperations} creates the index; the SQL stores and
 * MongoDB ignore them.
 *
 * <p>The JSON is trusted configuration and is sent as written.</p>
 *
 * @since 5.4.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Inherited
@Experimental
public @interface SearchIndexSettings {

    /**
     * @return The settings as JSON
     */
    String value();
}
