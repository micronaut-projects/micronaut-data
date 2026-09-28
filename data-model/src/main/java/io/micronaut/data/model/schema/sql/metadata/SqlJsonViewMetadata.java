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
package io.micronaut.data.model.schema.sql.metadata;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Oracle JSON relational duality view metadata extracted from the database dictionary views
 * {@code ALL_JSON_DUALITY_VIEWS}, {@code ALL_JSON_DUALITY_VIEW_TABS}, {@code ALL_JSON_DUALITY_VIEW_TAB_COLS}
 * and {@code ALL_JSON_DUALITY_VIEW_LINKS}.
 *
 * @param name The view name
 * @param status The view status, can be null
 * @param tables The tables of the view, a table used more than once is listed for each use
 * @param fields The JSON fields of the view mapped to table columns
 * @param links The links of the sub view tables to their parent tables
 * @since 5.3.0
 */
@Internal
public record SqlJsonViewMetadata(String name,
                                  @Nullable String status,
                                  List<Table> tables,
                                  List<Field> fields,
                                  List<Link> links) {

    public SqlJsonViewMetadata {
        tables = List.copyOf(tables);
        fields = List.copyOf(fields);
        links = List.copyOf(links);
    }

    /**
     * A table of the view.
     *
     * @param number The table number in the view, unique in the view
     * @param parentNumber The number of the parent table, null for the root table
     * @param name The table name
     * @param relationship The relationship to the parent table, {@code singleton} (an object) or {@code nested} (an array), null for the root table
     * @param allowInsert Whether the table allows inserts
     * @param allowUpdate Whether the table allows updates
     * @param allowDelete Whether the table allows deletes
     */
    public record Table(int number,
                        @Nullable Integer parentNumber,
                        String name,
                        @Nullable String relationship,
                        boolean allowInsert,
                        boolean allowUpdate,
                        boolean allowDelete) {
    }

    /**
     * A JSON field of the view.
     *
     * @param tableNumber The number of the table storing the field
     * @param key The JSON key
     * @param column The column storing the field
     */
    public record Field(int tableNumber, String key, String column) {
    }

    /**
     * A link of a sub view table to its parent table.
     *
     * @param parentTable The parent table name
     * @param childTable The child table name
     * @param key The JSON key of the child in the parent, null for an unnested child
     */
    public record Link(String parentTable, String childTable, @Nullable String key) {
    }
}
