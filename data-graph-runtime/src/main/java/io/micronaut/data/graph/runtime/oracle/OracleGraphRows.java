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
import io.micronaut.data.graph.model.query.builder.OracleGraphQueryBuilder;
import io.micronaut.data.graph.runtime.GraphEdge;
import io.micronaut.data.graph.runtime.GraphNode;
import io.micronaut.data.graph.runtime.GraphPathValue;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.age.Agtype;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private OracleGraphRows() {
    }

    /**
     * Expands each {@code ?} whose parameter is a collection into one {@code ?} per element.
     *
     * @param query      The query
     * @param parameters The parameters in order
     * @return The query and the flattened parameters
     */
    public static Expanded expand(String query, List<@Nullable Object> parameters) {
        StringBuilder sb = new StringBuilder(query.length());
        List<Object> flattened = new ArrayList<>(parameters.size());
        int index = 0;
        char quote = 0;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (quote != 0 || c == '\'' || c == '"') {
                // Inside a literal, or opening or closing one
                if (quote == 0) {
                    quote = c;
                } else if (c == quote) {
                    quote = 0;
                }
                sb.append(c);
            } else if (c == '?') {
                if (index >= parameters.size()) {
                    throw new IllegalStateException("The query has more parameters than values: " + query);
                }
                appendParameter(sb, flattened, parameters.get(index++));
            } else {
                sb.append(c);
            }
        }
        return new Expanded(sb.toString(), flattened);
    }

    private static void appendParameter(StringBuilder sb, List<Object> flattened, @Nullable Object value) {
        if (!(value instanceof Collection<?> collection)) {
            sb.append('?');
            flattened.add(value);
        } else if (collection.isEmpty()) {
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
    /**
     * Whether a column holds the schemaless properties of a node, which drivers read as JSON text.
     *
     * @param column The column label
     * @return Whether to read the column as a string
     */
    public static boolean isJsonText(String column) {
        return column.endsWith(SEPARATOR + OracleGraphQueryBuilder.DYNAMIC_ATTRIBUTES_COLUMN);
    }

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
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, @Nullable Object>> group : groups.entrySet()) {
            Map<String, @Nullable Object> fields = group.getValue();
            if (fields.containsKey(OracleGraphQueryBuilder.ID_COLUMN) || fields.containsKey(OracleGraphQueryBuilder.LABEL_COLUMN)) {
                nodes.put(group.getKey(), node(fields, rowIndex));
            }
        }
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
                GraphNode from = nodes.get("s");
                GraphNode to = nodes.get("t");
                keys.add(prefix);
                values.add(new GraphEdge(rowIndex, String.valueOf(fields.get(OracleGraphQueryBuilder.TYPE_COLUMN)),
                    from == null ? rowIndex : from.id(), to == null ? rowIndex : to.id(), Map.of()));
            }
        }
        return new GraphRecord(keys, values);
    }

    public static List<GraphRecord> decodePaths(List<String> columns, List<List<@Nullable Object>> rows) {
        Map<Object, List<Map<String, Map<String, @Nullable Object>>>> paths = new LinkedHashMap<>();
        for (List<@Nullable Object> row : rows) {
            Map<String, Map<String, @Nullable Object>> groups = group(columns, row);
            Object match = groups.getOrDefault("p", Map.of()).get("M_");
            paths.computeIfAbsent(match == null ? paths.size() : match, k -> new ArrayList<>()).add(groups);
        }
        List<GraphRecord> records = new ArrayList<>(paths.size());
        int rowIndex = 0;
        for (Map.Entry<Object, List<Map<String, Map<String, @Nullable Object>>>> path : paths.entrySet()) {
            List<GraphNode> nodes = new ArrayList<>();
            List<GraphEdge> edges = new ArrayList<>();
            List<Map<String, Map<String, @Nullable Object>>> steps = path.getValue();
            for (int i = 0; i < steps.size(); i++) {
                Map<String, Map<String, @Nullable Object>> step = steps.get(i);
                GraphNode start = node(step.getOrDefault(OracleGraphQueryBuilder.STEP_START, Map.of()), rowIndex);
                GraphNode end = node(step.getOrDefault(OracleGraphQueryBuilder.STEP_END, Map.of()), rowIndex);
                if (i == 0) {
                    nodes.add(start);
                }
                Object type = step.getOrDefault("x", Map.of()).get(OracleGraphQueryBuilder.TYPE_COLUMN);
                edges.add(new GraphEdge(path.getKey() + "/" + i, type == null ? "" : type.toString(), start.id(), end.id(), Map.of()));
                nodes.add(end);
                rowIndex++;
            }
            records.add(new GraphRecord(List.of("p"), List.of(new GraphPathValue(nodes, edges))));
        }
        return records;
    }

    private static GraphNode node(Map<String, @Nullable Object> fields, int rowIndex) {
        Object id = fields.get(OracleGraphQueryBuilder.ID_COLUMN);
        Object label = fields.get(OracleGraphQueryBuilder.LABEL_COLUMN);
        Map<String, Object> properties = new LinkedHashMap<>();
        fields.forEach((name, value) -> {
            if (!name.equals(OracleGraphQueryBuilder.ID_COLUMN) && !name.equals(OracleGraphQueryBuilder.LABEL_COLUMN)
                && !name.equals(OracleGraphQueryBuilder.DYNAMIC_ATTRIBUTES_COLUMN)) {
                properties.put(name, value);
            }
        });
        dynamicAttributes(fields.get(OracleGraphQueryBuilder.DYNAMIC_ATTRIBUTES_COLUMN)).forEach(properties::putIfAbsent);
        return new GraphNode(id == null ? rowIndex : id, label == null ? List.of() : List.of(label.toString()), properties);
    }

    /**
     * Reads the JSON object of the schemaless properties of a node. The driver returns the JSON column as text or as
     * an {@code oracle.sql.json.OracleJsonObject}, whose string form is its JSON text.
     */
    private static Map<String, Object> dynamicAttributes(@Nullable Object json) {
        if (json == null) {
            return Map.of();
        }
        String text = json instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : json.toString();
        // agtype text is a superset of JSON
        if (!(Agtype.parse(text) instanceof Map<?, ?> map)) {
            // The column holds row data, which may be personal, so only its length is reported
            throw new IllegalStateException("Expected a JSON object of schemaless properties, found a value of " + text.length() + " characters");
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            // A property set to null was removed
            if (value != null) {
                attributes.put(key.toString(), value);
            }
        });
        return attributes;
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
