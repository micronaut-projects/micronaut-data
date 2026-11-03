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

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a vector search method as approximate, so the database may answer the top-K query from a
 * vector index instead of comparing every row.
 *
 * <p>On Oracle the row limit of the query is rendered as {@code FETCH APPROX FIRST n ROWS ONLY},
 * optionally {@code WITH TARGET ACCURACY p}. The limit comes from the method name, such as
 * {@code searchTop5By...}, or from a {@code Limit} or {@code Pageable} argument. A query with an
 * offset keeps the exact row limit. Other dialects ignore the annotation; PostgreSQL, for example,
 * uses a pgvector index for an ordered, limited query without extra syntax.</p>
 *
 * @since 5.3.0
 */
@Documented
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface ApproximateSearch {

    /**
     * The target accuracy, as a percentage from 1 to 100, or {@code 0} to use the accuracy of the
     * vector index.
     *
     * @return The target accuracy
     */
    int targetAccuracy() default 0;
}
