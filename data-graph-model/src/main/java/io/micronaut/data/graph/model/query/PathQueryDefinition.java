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

import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.BindingParameter;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Describes a query for paths or relationships between nodes of an entity.
 *
 * @param entity        The node entity at both ends
 * @param kind          The kind of query
 * @param result        What the query returns
 * @param relationships The associations whose relationship types may be traversed; empty for any type
 * @param direction     The direction to traverse
 * @param maxDepth      The maximum number of relationships, or -1 for no bound
 * @param maxDepthBound Whether the maximum is a method argument rather than {@code maxDepth}
 * @param start         The identifier of the start node, or null for any node
 * @param end           The identifier of the end node, or null for any node
 * @author graemerocher
 * @since 5.3.0
 */
public record PathQueryDefinition(PersistentEntity entity,
                                  Kind kind,
                                  Result result,
                                  List<Association> relationships,
                                  Direction direction,
                                  int maxDepth,
                                  boolean maxDepthBound,
                                  @Nullable BindingParameter start,
                                  @Nullable BindingParameter end) {

    /**
     * The token in a query that the runtime replaces with the path limit argument.
     */
    public static final String PATH_LIMIT_TOKEN = "$__pathLimit";

    /**
     * The kind of path query.
     */
    public enum Kind {
        /**
         * One shortest path.
         */
        SHORTEST,
        /**
         * All shortest paths.
         */
        ALL_SHORTEST,
        /**
         * All paths up to the maximum depth.
         */
        ALL,
        /**
         * The relationships between nodes rather than paths.
         */
        RELATIONSHIPS
    }

    /**
     * What a path query returns.
     */
    public enum Result {
        /**
         * The paths or relationships.
         */
        ELEMENTS,
        /**
         * The number of paths or relationships.
         */
        COUNT,
        /**
         * Whether any path or relationship exists.
         */
        EXISTS
    }
}
