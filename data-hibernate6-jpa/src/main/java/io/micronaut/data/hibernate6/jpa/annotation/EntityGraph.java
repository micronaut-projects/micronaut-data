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
package io.micronaut.data.hibernate6.jpa.annotation;

import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Allows configuring JPA 2.1 entity graphs on query methods. Largely based on the same annotation as in Spring Data.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Documented
public @interface EntityGraph {

    /**
     * Same as {@link #name()}.
     * @return The name of the entity graph.
     */
    @AliasFor(member = "name")
    String value() default "";

    /**
     * Specifies the name of the entity graph. If none is specified one will be created at runtime.
     *
     * @return The name of the entity graph to use.
     */
    @AliasFor(member = "value")
    String name() default "";

    /**
     * @return The name of the hint to use.
     * @see <a href="https://download.oracle.com/otn-pub/jcp/persistence-2_1-fr-eval-spec/JavaPersistence.pdf">JPA 2.1
     *      Specification: 3.7.4.2 Load Graph Semantics</a>
     */
    String hint() default "jakarta.persistence.fetchgraph";

    /**
     * The attributes paths to include in the entity graph.
     * @return The attributes paths
     */
    String[] attributePaths() default {};
}
