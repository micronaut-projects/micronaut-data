/*
 * Copyright 2017-2025 original authors
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Annotates a repository method to request sorting of results.</p>
 *
 * <p>When multiple {@code OrderBy} annotations are specified on a
 * repository method, the precedence for sorting follows the order
 * in which the {@code OrderBy} annotations are specified,
 * and after that follows any sort criteria that are supplied
 * dynamically by {@link io.micronaut.data.model.Sort} parameters or by any {@link io.micronaut.data.model.Sort.Order} parameter.</p>
 *
 * <p>For example, the following sorts first by the
 * {@code lastName} attribute in ascending order,
 * and secondly, for entities with the same {@code lastName},
 * it then sorts by the {@code firstName} attribute,
 * also in ascending order. For entities with the same
 * {@code lastName} and {@code firstName}.</p>
 *
 * <pre>
 * &#64;OrderBy("lastName")
 * &#64;OrderBy("firstName")
 * &#64;OrderBy("id")
 * Person[] findByZipCode(int zipCode, Pageable pageable);
 * </pre>
 *
 * <p>The interpretation of ascending and descending order is determined
 * by the database, but, in general:
 * <ul>
 * <li>ascending order for numeric values is the natural order with
 *     smaller numbers before larger numbers,</li>
 * <li>ascending order for string values is lexicographic order with
 *     {@code A} before {@code Z}, and</li>
 * <li>ascending order for boolean values places {@code false} before
 *     {@code true}.</li>
 * </ul>
 *
 * <p>A repository method with an {@code @OrderBy} annotation must not
 * have:</p>
 * <ul>
 * <li>the <em>Query by Method Name</em> {@code OrderBy} keyword in its
 *     name, nor</li>
 * <li>a {@link Query @Query} annotation specifying a Jakarta Query or JPQL query
 *     with an {@code ORDER BY} clause.</li>
 * </ul>
 */
@Repeatable(OrderBy.List.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OrderBy {
    /**
     * <p>Indicate whether to use descending order
     * when sorting by this attribute.</p>
     *
     * <p>The default value of {@code false} means ascending sort.</p>
     *
     * @return whether to use descending (versus ascending) order.
     */
    boolean descending() default false;

    /**
     * <p>Indicates whether or not to request case insensitive ordering
     * from a database with case sensitive collation.
     * A database with case insensitive collation performs case insensitive
     * ordering regardless of the requested {@code ignoreCase} value.</p>
     *
     * <p>The default value is {@code false}.</p>
     *
     * @return whether or not to request case insensitive sorting for the property.
     */
    boolean ignoreCase() default false;

    /**
     * <p>Entity attribute name to sort by.</p>
     *
     * <p>For example,</p>
     *
     * <pre>
     * &#64;OrderBy("age")
     * Stream&lt;Person&gt; findByLastName(String lastName);
     * </pre>
     *
     * @return entity attribute name.
     */
    String value();

    /**
     * Enables multiple {@code OrderBy} annotations on the method.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        /**
         * Returns a list of annotations with the first taking precedence,
         * followed by the second, and so forth.
         *
         * @return list of annotations.
         */
        OrderBy[] value();
    }
}
