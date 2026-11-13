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
package io.micronaut.data.mongodb.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Searches a {@link io.micronaut.data.annotation.search.Searchable} entity with MongoDB Search
 * ({@code $search}, available on MongoDB Atlas and the Atlas local deployment) instead of the
 * {@code $text} operator. The search keywords, {@code search(SearchQuery)} and the criteria API
 * then render a {@code $search} aggregation stage over the search fields, which adds prefix and
 * fuzzy matching, field scoping, highlighting and minimum score restrictions.
 *
 * <p>When {@code micronaut.data.mongodb.create-collections} is enabled the search index is created
 * together with the collection, mapping every {@link io.micronaut.data.annotation.search.SearchField}
 * as a {@code string} field analyzed with the {@link #analyzer()}.</p>
 *
 * @author Graeme Rocher
 * @since 5.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
@Inherited
@Experimental
public @interface MongoSearchIndex {

    /**
     * The annotation name.
     */
    String ANNOTATION_NAME = MongoSearchIndex.class.getName();

    /**
     * The name member.
     */
    String MEMBER_NAME = "name";

    /**
     * The analyzer member.
     */
    String MEMBER_ANALYZER = "analyzer";

    /**
     * The dynamic member.
     */
    String MEMBER_DYNAMIC = "dynamic";

    /**
     * @return The name of the search index, {@code default} unless set
     */
    String name() default "default";

    /**
     * @return The Lucene analyzer of the indexed fields, derived from the entity language
     * ({@code lucene.english} for {@code english}) unless set
     */
    String analyzer() default "";

    /**
     * @return Whether the index maps every field dynamically in addition to the search fields
     */
    boolean dynamic() default false;
}
