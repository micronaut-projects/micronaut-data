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

/**
 * One relationship of a path together with the nodes it connects, in path order.
 *
 * @param start        The node the segment starts at
 * @param relationship The relationship
 * @param end          The node the segment ends at
 * @param <N>          The type of the nodes
 * @author graemerocher
 * @since 5.3.0
 */
public record PathSegment<N>(N start, GraphRelationship<?, ?> relationship, N end) {
}
