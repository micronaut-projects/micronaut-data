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

import java.util.ArrayList;
import java.util.List;

/**
 * A path through a graph: an alternating sequence of nodes and relationships.
 *
 * <p>A path of length zero holds a single node. The relationships of a path keep the direction they
 * have in the graph, which can differ from the direction the path walks.</p>
 *
 * @param nodes         The nodes in path order
 * @param relationships The relationships in path order
 * @param <N>           The type of the nodes
 * @author graemerocher
 * @since 5.3.0
 */
public record GraphPath<N>(List<N> nodes, List<GraphRelationship<?, ?>> relationships) {

    /**
     * Creates a path.
     *
     * @param nodes         The nodes in path order
     * @param relationships The relationships in path order
     */
    public GraphPath {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("A path has at least one node");
        }
        if (relationships.size() != nodes.size() - 1) {
            throw new IllegalArgumentException("A path of " + nodes.size() + " nodes has " + (nodes.size() - 1) + " relationships, got " + relationships.size());
        }
        nodes = List.copyOf(nodes);
        relationships = List.copyOf(relationships);
    }

    /**
     * @return The first node
     */
    public N start() {
        return nodes.get(0);
    }

    /**
     * @return The last node
     */
    public N end() {
        return nodes.get(nodes.size() - 1);
    }

    /**
     * @return The number of relationships
     */
    public int length() {
        return relationships.size();
    }

    /**
     * @return The segments of the path in order
     */
    public List<PathSegment<N>> segments() {
        List<PathSegment<N>> segments = new ArrayList<>(relationships.size());
        for (int i = 0; i < relationships.size(); i++) {
            segments.add(new PathSegment<>(nodes.get(i), relationships.get(i), nodes.get(i + 1)));
        }
        return segments;
    }
}
