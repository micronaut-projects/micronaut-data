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
package io.micronaut.data.model.schema.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.JsonView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Oracle JSON relational duality view mapping extracted from a {@link JsonView} entity: the tree of the view tables
 * as the view is created, each table with its JSON key, relationship, allowed operations and JSON fields.
 *
 * @param schema The view schema, can be null
 * @param name The view name
 * @param root The root table of the view, the table of the {@link JsonView#entity()}
 *
 * @author radovanradic
 * @since 5.3.0
 */
@Internal
public record SqlJsonViewMapping(@Nullable String schema,
                                 String name,
                                 Table root) {

    /**
     * @return The root table name
     */
    public String rootTable() {
        return root.table();
    }

    /**
     * @return The names of all the tables of the view
     */
    public Set<String> tables() {
        Set<String> tables = new LinkedHashSet<>();
        collectTables(root, tables);
        return tables;
    }

    /**
     * @return All the JSON fields of the view
     */
    public List<Field> fields() {
        List<Field> fields = new ArrayList<>();
        collectFields(root, fields);
        return fields;
    }

    private static void collectTables(Table table, Set<String> tables) {
        tables.add(table.table());
        table.children().forEach(child -> collectTables(child, tables));
    }

    private static void collectFields(Table table, List<Field> fields) {
        fields.addAll(table.fields());
        table.children().forEach(child -> collectFields(child, fields));
    }

    /**
     * A table of the view, the root table or a sub view table.
     *
     * @param table The table name
     * @param key The JSON key of the sub view in its parent, null for the root table and for an unnested sub view
     * @param nested Whether the sub view is an array of objects (one-to-many), false for an object and the root table
     * @param operations The operations allowed on the table
     * @param fields The JSON fields stored in the table
     * @param children The sub view tables
     */
    public record Table(String table,
                        @Nullable String key,
                        boolean nested,
                        Set<JsonView.Operation> operations,
                        List<Field> fields,
                        List<Table> children) {

        public Table {
            operations = Set.copyOf(operations);
            fields = List.copyOf(fields);
            children = List.copyOf(children);
        }
    }

    /**
     * A JSON field of the view.
     *
     * @param table The table storing the field
     * @param key The JSON key
     * @param column The column storing the field, null if it's not known (embedded id fields)
     */
    public record Field(String table, String key, @Nullable String column) {
    }
}
