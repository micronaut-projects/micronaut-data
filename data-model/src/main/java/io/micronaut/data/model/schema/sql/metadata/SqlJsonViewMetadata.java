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
import java.util.Set;

/**
 * Oracle JSON relational duality view metadata extracted from the database.
 *
 * @param name The view name
 * @param rootTable The root table of the view
 * @param allowInsert Whether the view allows inserts
 * @param allowUpdate Whether the view allows updates
 * @param allowDelete Whether the view allows deletes
 * @param status The view status, can be null
 * @param tables The tables used by the view
 * @param fields The JSON fields of the view mapped to table columns
 * @since 5.3.0
 */
@Internal
public record SqlJsonViewMetadata(String name,
                                  String rootTable,
                                  boolean allowInsert,
                                  boolean allowUpdate,
                                  boolean allowDelete,
                                  @Nullable String status,
                                  Set<String> tables,
                                  List<Field> fields) {

    public SqlJsonViewMetadata {
        tables = Set.copyOf(tables);
        fields = List.copyOf(fields);
    }

    /**
     * A JSON field of the view.
     *
     * @param table The table storing the field
     * @param key The JSON key
     * @param column The column storing the field
     */
    public record Field(String table, String key, String column) {
    }
}
