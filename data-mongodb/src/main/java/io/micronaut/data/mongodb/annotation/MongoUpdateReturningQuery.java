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

import com.mongodb.client.model.ReturnDocument;
import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a custom MongoDB single-document update query that returns a document result.
 * <p>
 * This annotation marks the method as an update-returning operation and supports projection
 * and returned-document state selection.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface MongoUpdateReturningQuery {

    /**
     * The custom MongoDB update document.
     *
     * @return The update JSON
     */
    String update();

    /**
     * The custom MongoDB filter.
     *
     * @return The filter JSON
     */
    @AliasFor(member = "value", annotation = MongoFilter.class)
    String filter() default "";

    /**
     * The custom collation represented in JSON.
     *
     * @return The collation JSON
     */
    @AliasFor(member = "value", annotation = MongoCollation.class)
    String collation() default "";

    /**
     * The custom fields projection represented in JSON.
     *
     * @return The projection JSON
     */
    @AliasFor(member = "value", annotation = MongoProjection.class)
    String project() default "";

    /**
     * The custom sort represented in JSON.
     *
     * @return The sort JSON
     */
    @AliasFor(member = "value", annotation = MongoSort.class)
    String sort() default "";

    /**
     * The array filters.
     *
     * @return The array filters
     */
    @AliasFor(member = "arrayFilters", annotation = MongoUpdateOptions.class)
    String[] arrayFilters() default {};

    /**
     * Controls whether MongoDB returns the document state before or after the update.
     *
     * @return The requested returned document state
     */
    ReturnDocument returnDocument() default ReturnDocument.BEFORE;
}
