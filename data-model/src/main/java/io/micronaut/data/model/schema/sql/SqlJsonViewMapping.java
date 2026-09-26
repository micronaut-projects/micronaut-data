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

import java.util.List;
import java.util.Set;

/**
 * The Oracle JSON relational duality view mapping extracted from a {@link JsonView} entity.
 *
 * @param schema The view schema, can be null
 * @param name The view name
 * @param rootTable The root table, the table of the {@link JsonView#entity()}
 * @param operations The operations allowed by the view
 * @param tables The tables used by the view and its sub views
 * @param fields The JSON fields mapped to table columns
 *
 * @author radovanradic
 * @since 5.3.0
 */
@Internal
public record SqlJsonViewMapping(@Nullable String schema,
                                 String name,
                                 String rootTable,
                                 Set<JsonView.Operation> operations,
                                 Set<String> tables,
                                 List<Field> fields) {

    public SqlJsonViewMapping {
        operations = Set.copyOf(operations);
        tables = Set.copyOf(tables);
        fields = List.copyOf(fields);
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
