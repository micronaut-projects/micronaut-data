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
 * Declares that an entity participates in full-text search.
 *
 * <p>The properties to index are marked with {@link SearchField}. How the index is physically
 * created (a PostgreSQL {@code tsvector} and GIN index, an Oracle Text index, a MongoDB text index,
 * an Elasticsearch mapping) is decided by the backend module; this annotation only carries the
 * portable facts.</p>
 *
 * @since 5.4.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Inherited
@Experimental
public @interface Searchable {

    /**
     * The annotation name.
     */
    String NAME = Searchable.class.getName();

    /**
     * The {@code language} member.
     */
    String LANGUAGE = "language";

    /**
     * Optional logical name of the search index. If empty, a name is derived from the entity name.
     *
     * @return The index name
     */
    String name() default "";

    /**
     * The default language used for stemming and stop words. Each backend maps the language
     * to its own configuration (a PostgreSQL text search configuration, a MongoDB
     * {@code default_language}, an Elasticsearch analyzer). Defaults to {@code english}.
     *
     * @return The language
     */
    String language() default "english";
}
