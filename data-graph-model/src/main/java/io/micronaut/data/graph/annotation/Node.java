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
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.NamingStrategy;
import io.micronaut.data.model.naming.NamingStrategies;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a class to a node in a property graph.
 *
 * <p>The node carries one or more labels. When neither {@link #value()} nor {@link #labels()} is set,
 * the label is the simple name of the class. Properties keep their Java names unless renamed
 * with {@link io.micronaut.data.annotation.MappedProperty}.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Documented
@MappedEntity
@NamingStrategy(NamingStrategies.Raw.class)
public @interface Node {

    /**
     * @return The primary label of the node. Defaults to the simple name of the class.
     */
    @AliasFor(annotation = MappedEntity.class, member = "value")
    String value() default "";

    /**
     * @return All labels of the node. When set, the first label is the primary label used to match the node.
     */
    String[] labels() default {};
}
