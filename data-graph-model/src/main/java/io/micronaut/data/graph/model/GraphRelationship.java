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
package io.micronaut.data.graph.model;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A relationship read from a graph, with the nodes at both ends.
 *
 * @param <F> The type of the start node
 * @param <T> The type of the end node
 * @author graemerocher
 * @since 5.3.0
 */
public interface GraphRelationship<F, T> {

    /**
     * @return The identifier the database assigned to the relationship
     */
    @Nullable
    Object id();

    /**
     * @return The relationship type
     */
    String type();

    /**
     * @return The node the relationship starts at
     */
    F from();

    /**
     * @return The node the relationship ends at
     */
    T to();

    /**
     * @return The properties stored on the relationship
     */
    Map<String, Object> properties();

    /**
     * Creates a relationship.
     *
     * @param id         The identifier
     * @param type       The type
     * @param from       The start node
     * @param to         The end node
     * @param properties The properties
     * @param <F>        The type of the start node
     * @param <T>        The type of the end node
     * @return The relationship
     */
    static <F, T> GraphRelationship<F, T> of(@Nullable Object id, String type, F from, T to, Map<String, Object> properties) {
        return new DefaultGraphRelationship<>(id, type, from, to, Map.copyOf(properties));
    }
}
