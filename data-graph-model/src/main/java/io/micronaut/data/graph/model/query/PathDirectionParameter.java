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
package io.micronaut.data.graph.model.query;

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Records the index of the {@link io.micronaut.data.graph.annotation.PathDirection} parameter of a path or
 * relationship finder, whose argument the runtime renders into the query in place of the
 * {@link PathQueryDefinition#PATH_LEFT_TOKEN} and {@link PathQueryDefinition#PATH_RIGHT_TOKEN} tokens.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface PathDirectionParameter {

    /**
     * @return The index of the parameter
     */
    int value();

    /**
     * A relationship finder without both endpoints matches each relationship twice when it is undirected, so
     * the runtime traverses outgoing relationships instead.
     *
     * @return Whether an undirected argument is rendered as outgoing
     */
    boolean outgoingIfUndirected() default false;
}
