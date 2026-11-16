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
package io.micronaut.data.graph.runtime.oracle;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.graph.model.query.builder.LiteralMasker;
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;
import io.micronaut.data.graph.runtime.GraphEdge;
import io.micronaut.data.graph.runtime.GraphNode;
import io.micronaut.data.graph.runtime.GraphPathValue;
import io.micronaut.data.graph.runtime.GraphRecord;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Turns the rows of Oracle property graph queries into graph records, rebuilding nodes, relationships and paths
 * from columns aliased {@code "<variable>__<property>"}.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class OracleGraphRows {

    private static final String SEPARATOR = OracleGraphQueryBuilder.SEPARATOR;
    private static final Pattern NOT_IN = Pattern.compile("(?i)\\bNOT\\s+IN\\s*\\(\\s*$");

    private OracleGraphRows() {
    }

    /**
     * Binds the parameters of a query by position. A {@code ?} takes the next value in order and a {@code $name}
     * takes the value of that name, which is how queries passed to the repository operations write parameters. A
     * parameter that is a collection is expanded into one {@code ?} per element.
     *
     * @param query      The query
     * @param parameters The parameters
     * @return The query and the flattened parameters
     */
    public static Expanded expand(String query, Map<String, @Nullable Object> parameters) {
        String masked = LiteralMasker.mask(query, true);
        StringBuilder sb = new StringBuilder(query.length());
        List<Object> flattened = new ArrayList<>(parameters.size());
        Iterator<@Nullable Object> values = parameters.values().iterator();
        int i = 0;
        while (i < query.length()) {
            char c = masked.charAt(i);
            int nameEnd = c == '$' ? parameterNameEnd(masked, i) : -1;
            if (c == '?') {
                if (!values.hasNext()) {
                    throw new IllegalStateException("The query has more parameters than values: " + query);
                }
                appendParameter(sb, flattened, values.next());
                i++;
            } else if (nameEnd > 0) {
                String name = masked.substring(i + 1, nameEnd);
                if (!parameters.containsKey(name)) {
                    throw new IllegalStateException("The query has the parameter $" + name + " without a value: " + query);
                }
                appendParameter(sb, flattened, parameters.get(name));
                i = nameEnd;
            } else {
                sb.append(query.charAt(i));
                i++;
            }
        }
        return new Expanded(sb.toString(), flattened);
    }

    /**
     * @return The offset after the name of the parameter opening at the given offset, or -1 if the dollar is part of a
     *         name such as {@code v$session}
     */
    private static int parameterNameEnd(String query, int dollar) {
        if (dollar > 0 && isNameCharacter(query.charAt(dollar - 1))) {
            return -1;
        }
        int end = dollar + 1;
        while (end < query.length() && isNameCharacter(query.charAt(end))) {
            end++;
        }
        return end > dollar + 1 && !Character.isDigit(query.charAt(dollar + 1)) ? end : -1;
    }

    private static boolean isNameCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static void appendParameter(StringBuilder sb, List<Object> flattened, @Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            sb.append('?');
            flattened.add(value);
        } else if (collection.isEmpty()) {
            if (NOT_IN.matcher(sb).find()) {
                // NOT IN (NULL) is never true, where a NOT IN of nothing should match every row
                throw new IllegalArgumentException("An empty collection cannot be bound to NOT IN on Oracle property graphs: " + sb);
            }
            sb.append("NULL");
        } else {
            for (int j = 0; j < collection.size(); j++) {
                sb.append(j == 0 ? "?" : ", ?");
            }
            flattened.addAll(collection);
        }
    }

    /**
     * Decodes the rows of a query.
     *
     * @param columns The column names
     * @param rows    The rows
     * @return The records
     */
    public static List<GraphRecord> decode(List<String> columns, List<List<@Nullable Object>> rows) {
        if (columns.contains(OracleGraphQueryBuilder.PATH_MATCH_COLUMN)) {
            return decodePaths(columns, rows);
        }
        List<GraphRecord> records = new ArrayList<>(rows.size());
        for (int r = 0; r < rows.size(); r++) {
            records.add(decodeRow(columns, rows.get(r), r));
        }
        return records;
    }

    public static GraphRecord decodeRow(List<String> columns, List<@Nullable Object> row, int rowIndex) {
        Map<String, Map<String, @Nullable Object>> groups = group(columns, row);
        List<String> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        Map<String, GraphNode> nodes = nodes(groups, rowIndex);
        for (Map.Entry<String, Map<String, @Nullable Object>> group : groups.entrySet()) {
            String prefix = group.getKey();
            Map<String, @Nullable Object> fields = group.getValue();
            if (prefix.isEmpty()) {
                fields.forEach((column, value) -> {
                    keys.add(column);
                    values.add(value);
                });
            } else if (nodes.containsKey(prefix)) {
                keys.add(switch (prefix) {
                    case "s" -> "f";
                    default -> prefix;
                });
                values.add(nodes.get(prefix));
            } else if (fields.containsKey(OracleGraphQueryBuilder.TYPE_COLUMN)) {
                keys.add(prefix);
                values.add(edge(fields, nodes, rowIndex));
            }
        }
        return new GraphRecord(keys, values);
    }

    private static Map<String, GraphNode> nodes(Map<String, Map<String, @Nullable Object>> groups, int rowIndex) {
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, @Nullable Object>> group : groups.entrySet()) {
            Map<String, @Nullable Object> fields = group.getValue();
            if (!fields.containsKey(OracleGraphQueryBuilder.TYPE_COLUMN)
                && (fields.containsKey(OracleGraphQueryBuilder.ID_COLUMN) || fields.containsKey(OracleGraphQueryBuilder.LABEL_COLUMN))) {
                nodes.put(group.getKey(), node(fields, rowIndex));
            }
        }
        for (Map<String, @Nullable Object> fields : groups.values()) {
            if (fields.get(OracleGraphQueryBuilder.FORWARD_COLUMN) instanceof Number forward && forward.intValue() == 0) {
                // An undirected edge matched from its destination: s and t stand for the source and destination below
                GraphNode start = nodes.get("s");
                nodes.put("s", nodes.get("t"));
                nodes.put("t", start);
            }
        }
        return nodes;
    }

    private static GraphEdge edge(Map<String, @Nullable Object> fields, Map<String, GraphNode> nodes, int rowIndex) {
        GraphNode from = nodes.get("s");
        GraphNode to = nodes.get("t");
        Object id = fields.get(OracleGraphQueryBuilder.ID_COLUMN);
        Map<String, Object> properties = new LinkedHashMap<>(fields);
        properties.remove(OracleGraphQueryBuilder.ID_COLUMN);
        properties.remove(OracleGraphQueryBuilder.TYPE_COLUMN);
        properties.remove(OracleGraphQueryBuilder.FORWARD_COLUMN);
        // A relationship has no null properties in Cypher, and GraphRelationship does not accept them
        properties.values().removeIf(Objects::isNull);
        return new GraphEdge(id == null ? rowIndex : id, String.valueOf(fields.get(OracleGraphQueryBuilder.TYPE_COLUMN)),
            from == null ? rowIndex : from.id(), to == null ? rowIndex : to.id(), properties);
    }

    public static List<GraphRecord> decodePaths(List<String> columns, List<List<@Nullable Object>> rows) {
        Map<Object, List<Map<String, Map<String, @Nullable Object>>>> paths = new LinkedHashMap<>();
        for (List<@Nullable Object> row : rows) {
            Map<String, Map<String, @Nullable Object>> groups = group(columns, row);
            Object match = groups.getOrDefault("p", Map.of()).get("M_");
            paths.computeIfAbsent(match == null ? paths.size() : match, k -> new ArrayList<>()).add(groups);
        }
        List<GraphPathValue> values = new ArrayList<>(paths.size());
        int[] nextId = {0};
        for (Map.Entry<Object, List<Map<String, Map<String, @Nullable Object>>>> path : paths.entrySet()) {
            values.add(pathValue(path.getKey(), path.getValue(), nextId));
        }
        // Shortest first, as Cypher orders the paths by their length; a stable sort keeps the order of the matches
        values.sort(Comparator.comparingInt(value -> value.relationships().size()));
        List<GraphRecord> records = new ArrayList<>(values.size());
        for (GraphPathValue value : values) {
            records.add(new GraphRecord(List.of("p"), List.of(value)));
        }
        return records;
    }

    private static GraphPathValue pathValue(Object key, List<Map<String, Map<String, @Nullable Object>>> steps, int[] nextId) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        // The vertices of the path without an identifier column, told apart by their label and properties
        Map<Map<String, @Nullable Object>, Integer> anonymous = new LinkedHashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Map<String, @Nullable Object>> step = steps.get(i);
            // Each step starts where the previous one ended
            GraphNode start = i == 0
                ? pathNode(step.getOrDefault(OracleGraphQueryBuilder.STEP_START, Map.of()), anonymous, nextId)
                : nodes.get(nodes.size() - 1);
            GraphNode end = pathNode(step.getOrDefault(OracleGraphQueryBuilder.STEP_END, Map.of()), anonymous, nextId);
            if (i == 0) {
                nodes.add(start);
            }
            Object type = step.getOrDefault("x", Map.of()).get(OracleGraphQueryBuilder.TYPE_COLUMN);
            edges.add(new GraphEdge(key + "/" + i, type == null ? "" : type.toString(), start.id(), end.id(), Map.of()));
            nodes.add(end);
        }
        return new GraphPathValue(nodes, edges);
    }

    /**
     * A vertex of a path. A vertex without an identifier column, or whose identifier is null, is given a synthetic
     * identifier: the same one when it appears again in the path with the same label and properties, so that a
     * cycle closes on one node while the other vertices stay distinct.
     */
    private static GraphNode pathNode(Map<String, @Nullable Object> fields, Map<Map<String, @Nullable Object>, Integer> anonymous, int[] nextId) {
        if (fields.get(OracleGraphQueryBuilder.ID_COLUMN) != null) {
            return node(fields, 0);
        }
        return node(fields, anonymous.computeIfAbsent(fields, key -> nextId[0]++));
    }

    private static GraphNode node(Map<String, @Nullable Object> fields, int rowIndex) {
        Object id = fields.get(OracleGraphQueryBuilder.ID_COLUMN);
        Object label = fields.get(OracleGraphQueryBuilder.LABEL_COLUMN);
        Map<String, Object> properties = new LinkedHashMap<>();
        fields.forEach((name, value) -> {
            if (!name.equals(OracleGraphQueryBuilder.ID_COLUMN) && !name.equals(OracleGraphQueryBuilder.LABEL_COLUMN)) {
                properties.put(name, value);
            }
        });
        return new GraphNode(id == null ? rowIndex : id, label == null ? List.of() : List.of(label.toString()), properties);
    }

    private static Map<String, Map<String, @Nullable Object>> group(List<String> columns, List<@Nullable Object> row) {
        Map<String, Map<String, @Nullable Object>> groups = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            int separator = column.indexOf(SEPARATOR);
            String prefix = separator > 0 ? column.substring(0, separator) : "";
            String name = separator > 0 ? column.substring(separator + SEPARATOR.length()) : column;
            groups.computeIfAbsent(prefix, k -> new LinkedHashMap<>()).put(name, row.get(i));
        }
        return groups;
    }

    /**
     * A query whose collection parameters were expanded.
     *
     * @param query      The query
     * @param parameters The parameters
     */
    public record Expanded(String query, List<@Nullable Object> parameters) {
    }
}
