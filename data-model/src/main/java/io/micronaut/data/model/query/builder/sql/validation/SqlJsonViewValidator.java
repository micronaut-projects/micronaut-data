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
package io.micronaut.data.model.query.builder.sql.validation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.model.schema.sql.SqlJsonViewMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlJsonViewMetadata;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates Oracle JSON relational duality views against the {@link JsonView} entities.
 * <p>
 * The tree of the view tables is compared to the tree of the view entity and its sub views: each sub view is matched
 * by its table and JSON key in the parent. A missing or invalid view, a different root table, a missing sub view,
 * a sub view that is an object instead of an array (or the opposite), and a JSON field not found or stored in a different
 * column are errors. Different allowed operations of a table, and view fields and sub views not mapped by the entity are warnings.
 *
 * @author radovanradic
 * @since 5.3.0
 */
@Internal
public final class SqlJsonViewValidator {

    private static final String VALID_STATUS = "VALID";
    private static final String NESTED = "nested";
    private static final String SINGLETON = "singleton";

    private SqlJsonViewValidator() {
    }

    /**
     * Validates the JSON view mapping against the view metadata.
     *
     * @param mapping The view mapping derived from the {@link JsonView} entity
     * @param metadata The view metadata from the database, null if the view doesn't exist
     * @param result The validation result collecting the problems found
     */
    public static void validate(SqlJsonViewMapping mapping, @Nullable SqlJsonViewMetadata metadata, SchemaValidationResult result) {
        String viewName = mapping.schema() == null ? mapping.name() : mapping.schema() + "." + mapping.name();
        if (metadata == null) {
            result.addError("Expected JSON view [" + viewName + "] not found");
            return;
        }
        if (metadata.status() != null && !VALID_STATUS.equalsIgnoreCase(metadata.status())) {
            result.addError("JSON view [" + viewName + "] has status [" + metadata.status() + "]");
        }
        SqlJsonViewMetadata.Table root = metadata.tables().stream().filter(table -> table.parentNumber() == null).findFirst().orElse(null);
        if (root == null || !mapping.rootTable().equalsIgnoreCase(root.name())) {
            result.addError("JSON view [" + viewName + "] has root table [" + (root == null ? null : root.name())
                + "] but the view entity is mapped to table [" + mapping.rootTable() + "]");
            return;
        }
        new ViewValidation(viewName, metadata, result).validateTable(mapping.root(), root, "");
    }

    /**
     * The validation of a view, the view tables are indexed by their parent table.
     */
    private static final class ViewValidation {

        private final String viewName;
        private final SchemaValidationResult result;
        private final Map<Integer, List<SqlJsonViewMetadata.Table>> childrenByParent = new HashMap<>();
        private final Map<Integer, Map<String, String>> columnsByTable = new HashMap<>();
        private final List<SqlJsonViewMetadata.Link> links;

        private ViewValidation(String viewName, SqlJsonViewMetadata metadata, SchemaValidationResult result) {
            this.viewName = viewName;
            this.result = result;
            this.links = metadata.links();
            for (SqlJsonViewMetadata.Table table : metadata.tables()) {
                Integer parentNumber = table.parentNumber();
                if (parentNumber != null) {
                    childrenByParent.computeIfAbsent(parentNumber, number -> new ArrayList<>()).add(table);
                }
            }
            for (SqlJsonViewMetadata.Field field : metadata.fields()) {
                columnsByTable.computeIfAbsent(field.tableNumber(), number -> new LinkedHashMap<>()).put(field.key(), field.column());
            }
        }

        private void validateTable(SqlJsonViewMapping.Table mappedTable, SqlJsonViewMetadata.Table table, String path) {
            String location = path.isEmpty() ? "" : " sub view [" + path + "]";
            Set<JsonView.Operation> operations = operations(table);
            Set<JsonView.Operation> mappedOperations = EnumSet.noneOf(JsonView.Operation.class);
            mappedOperations.addAll(mappedTable.operations());
            if (!operations.equals(mappedOperations)) {
                result.addWarning("JSON view [" + viewName + "]" + location + " allows operations " + operations
                    + " but the view entity declares " + mappedOperations);
            }
            validateFields(mappedTable, table, path.isEmpty() ? "" : " in sub view [" + path + "]");
            validateSubViews(mappedTable, table, path);
        }

        private void validateFields(SqlJsonViewMapping.Table mappedTable, SqlJsonViewMetadata.Table table, String location) {
            Map<String, String> columns = columnsByTable.getOrDefault(table.number(), Map.of());
            Set<String> mappedKeys = new HashSet<>();
            for (SqlJsonViewMapping.Field field : mappedTable.fields()) {
                mappedKeys.add(field.key());
                // The JSON keys are case-sensitive
                String column = columns.get(field.key());
                if (column == null) {
                    result.addError("JSON view [" + viewName + "] field [" + field.key() + "] of table [" + field.table() + "] not found" + location);
                } else if (field.column() != null && !field.column().equalsIgnoreCase(column)) {
                    result.addError("JSON view [" + viewName + "] field [" + field.key() + "] of table [" + field.table() + "]" + location
                        + " is stored in column [" + column + "] but the view entity maps it to column [" + field.column() + "]");
                }
            }
            columns.keySet().stream().filter(key -> !mappedKeys.contains(key)).forEach(key ->
                result.addWarning("JSON view [" + viewName + "] field [" + key + "] of table [" + mappedTable.table() + "]" + location
                    + " is not mapped by the view entity"));
        }

        private void validateSubViews(SqlJsonViewMapping.Table mappedTable, SqlJsonViewMetadata.Table table, String path) {
            List<SqlJsonViewMetadata.Table> children = childrenByParent.getOrDefault(table.number(), List.of());
            Set<Integer> matched = new HashSet<>();
            for (SqlJsonViewMapping.Table mappedChild : mappedTable.children()) {
                String childPath = childPath(path, mappedChild.key(), mappedChild.table());
                SqlJsonViewMetadata.Table child = findChild(table, children, matched, mappedChild);
                if (child == null) {
                    result.addError("JSON view [" + viewName + "] sub view [" + childPath + "] of table [" + mappedChild.table() + "] not found");
                    continue;
                }
                matched.add(child.number());
                String expectedRelationship = mappedChild.nested() ? NESTED : SINGLETON;
                if (child.relationship() != null && !expectedRelationship.equalsIgnoreCase(child.relationship())) {
                    result.addError("JSON view [" + viewName + "] sub view [" + childPath + "] of table [" + mappedChild.table() + "] is "
                        + describe(child.relationship()) + " but the view entity maps it as " + describe(expectedRelationship));
                }
                validateTable(mappedChild, child, childPath);
            }
            for (SqlJsonViewMetadata.Table child : children) {
                if (!matched.contains(child.number())) {
                    List<String> keys = keys(table.name(), child.name());
                    String childPath = childPath(path, keys.size() == 1 ? keys.getFirst() : null, child.name());
                    result.addWarning("JSON view [" + viewName + "] sub view [" + childPath + "] of table [" + child.name() + "] is not mapped by the view entity");
                }
            }
        }

        /**
         * Finds the view table of the mapped sub view: a child table with the same name linked to the parent with the same JSON key.
         * The links only have the table names, so when the same table is a child more than once, the one with the same JSON field keys is preferred.
         */
        private SqlJsonViewMetadata.@Nullable Table findChild(SqlJsonViewMetadata.Table table,
                                                              List<SqlJsonViewMetadata.Table> children,
                                                              Set<Integer> matched,
                                                              SqlJsonViewMapping.Table mappedChild) {
            List<SqlJsonViewMetadata.Table> candidates = children.stream()
                .filter(child -> !matched.contains(child.number()) && child.name().equalsIgnoreCase(mappedChild.table())
                    && keys(table.name(), child.name()).contains(mappedChild.key()))
                .toList();
            if (candidates.size() <= 1) {
                return candidates.isEmpty() ? null : candidates.getFirst();
            }
            Set<String> mappedKeys = new HashSet<>();
            mappedChild.fields().forEach(field -> mappedKeys.add(field.key()));
            return candidates.stream()
                .filter(candidate -> columnsByTable.getOrDefault(candidate.number(), Map.of()).keySet().equals(mappedKeys))
                .findFirst()
                .orElse(candidates.getFirst());
        }

        /**
         * @return The JSON keys of the child table in the parent table, a null key for an unnested child
         */
        private List<@Nullable String> keys(String parentTable, String childTable) {
            List<@Nullable String> keys = new ArrayList<>();
            for (SqlJsonViewMetadata.Link link : links) {
                if (link.parentTable().equalsIgnoreCase(parentTable) && link.childTable().equalsIgnoreCase(childTable)
                    && keys.stream().noneMatch(key -> Objects.equals(key, link.key()))) {
                    keys.add(link.key());
                }
            }
            return keys;
        }

        private static String childPath(String path, @Nullable String key, String table) {
            String segment = key == null ? "unnested " + table : key;
            return path.isEmpty() ? segment : path + "." + segment;
        }

        private static String describe(String relationship) {
            return NESTED.equalsIgnoreCase(relationship) ? "an array" : "an object";
        }

        private static Set<JsonView.Operation> operations(SqlJsonViewMetadata.Table table) {
            Set<JsonView.Operation> operations = EnumSet.noneOf(JsonView.Operation.class);
            if (table.allowInsert()) {
                operations.add(JsonView.Operation.INSERT);
            }
            if (table.allowUpdate()) {
                operations.add(JsonView.Operation.UPDATE);
            }
            if (table.allowDelete()) {
                operations.add(JsonView.Operation.DELETE);
            }
            return operations;
        }
    }
}
