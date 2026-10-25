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
import io.micronaut.data.annotation.Relation;

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
 * <p>Saving the declaring node writes the relationship and, by default, saves the related node as well.
 * The {@link #cascade()} member limits what a save does to the related node.</p>
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

    /**
     * What saving the declaring node does to the related node, before the relationship is merged.
     *
     * <ul>
     *     <li>{@link Relation.Cascade#ALL}: a related node without an identifier is created and one with an
     *     identifier is updated, so its properties are written again and its {@code @Version} advances.</li>
     *     <li>{@link Relation.Cascade#PERSIST}: a related node without an identifier is created; one with an
     *     identifier is linked without being updated.</li>
     *     <li>{@link Relation.Cascade#UPDATE}: a related node with an identifier is updated; one without an
     *     identifier cannot be linked and the save fails.</li>
     *     <li>{@link Relation.Cascade#NONE}: the related node must already exist with an identifier, and only
     *     the relationship is merged.</li>
     * </ul>
     *
     * @return The cascade behaviour, {@link Relation.Cascade#ALL} by default
     * @since 5.3.0
     */
    Relation.Cascade cascade() default Relation.Cascade.ALL;
}
