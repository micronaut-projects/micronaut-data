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
package io.micronaut.data.neo4j.operations;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.runtime.GraphEdge;
import io.micronaut.data.graph.runtime.GraphNode;
import io.micronaut.data.graph.runtime.GraphPathValue;
import io.micronaut.data.graph.runtime.GraphRecord;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.IsoDuration;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;

import java.time.Duration;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes values returned by the Neo4j driver into driver independent graph values.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class Neo4jValues {

    private Neo4jValues() {
    }

    static GraphRecord decode(Record record) {
        List<Object> values = new ArrayList<>(record.size());
        for (Value value : record.values()) {
            values.add(decode(value));
        }
        return new GraphRecord(record.keys(), values);
    }

    static @Nullable Object decode(@Nullable Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return decodeObject(value.asObject());
    }

    private static @Nullable Object decodeObject(@Nullable Object value) {
        if (value instanceof Node node) {
            return node(node);
        }
        if (value instanceof Relationship relationship) {
            return relationship(relationship);
        }
        if (value instanceof Path path) {
            List<GraphNode> nodes = new ArrayList<>();
            for (Node node : path.nodes()) {
                nodes.add(node(node));
            }
            List<GraphEdge> relationships = new ArrayList<>();
            for (Relationship relationship : path.relationships()) {
                relationships.add(relationship(relationship));
            }
            return new GraphPathValue(nodes, relationships);
        }
        if (value instanceof List<?> list) {
            List<Object> decoded = new ArrayList<>(list.size());
            for (Object item : list) {
                decoded.add(decodeObject(item));
            }
            return decoded;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> decoded = new LinkedHashMap<>();
            map.forEach((k, v) -> decoded.put(String.valueOf(k), decodeObject(v)));
            return decoded;
        }
        if (value instanceof IsoDuration duration) {
            return duration(duration);
        }
        return value;
    }

    /**
     * Adapts a Neo4j duration to the Java type that represents it exactly: a {@link Duration} when it only has
     * seconds and nanoseconds, a {@link Period} when it only has months and days. A duration with both, such as
     * {@code P1DT2H}, has no exact Java counterpart because a day is not a fixed number of seconds, and is rejected
     * rather than passed through as a driver type.
     */
    private static Object duration(IsoDuration duration) {
        boolean calendar = duration.months() != 0 || duration.days() != 0;
        boolean time = duration.seconds() != 0 || duration.nanoseconds() != 0;
        if (calendar && time) {
            throw new DataAccessException("Cannot read the Neo4j duration [" + duration + "]: it has both a calendar part (months or days) and a time part, which no java.time type represents exactly. Return it as a string with toString(), or return its components");
        }
        if (calendar) {
            return Period.of(0, Math.toIntExact(duration.months()), Math.toIntExact(duration.days()));
        }
        return Duration.ofSeconds(duration.seconds(), duration.nanoseconds());
    }

    private static GraphNode node(Node node) {
        List<String> labels = new ArrayList<>();
        node.labels().forEach(labels::add);
        return new GraphNode(node.elementId(), labels, node.asMap(Neo4jValues::decode));
    }

    private static GraphEdge relationship(Relationship relationship) {
        return new GraphEdge(relationship.elementId(), relationship.type(), relationship.startNodeElementId(),
            relationship.endNodeElementId(), relationship.asMap(Neo4jValues::decode));
    }
}
