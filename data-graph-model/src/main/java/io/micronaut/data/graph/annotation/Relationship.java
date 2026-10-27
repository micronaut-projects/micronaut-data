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
package io.micronaut.data.graph.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.data.annotation.InferredRelation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a property to a relationship between two nodes.
 *
 * <p>The property type is either another {@link Node} or a class annotated with
 * {@link RelationshipProperties} that holds the properties of the relationship and its {@link TargetNode}.
 * A collection type maps to many relationships, any other type to a single one.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Documented
@InferredRelation
public @interface Relationship {

    /**
     * @return The relationship type. Defaults to the property name in upper snake case, for example {@code ACTED_IN}.
     */
    @AliasFor(member = "type")
    String value() default "";

    /**
     * @return The relationship type. Defaults to the property name in upper snake case, for example {@code ACTED_IN}.
     */
    @AliasFor(member = "value")
    String type() default "";

    /**
     * @return The direction of the relationship relative to the declaring node.
     */
    Direction direction() default Direction.OUTGOING;
}
