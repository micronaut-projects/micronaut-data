/*
 * Copyright 2017-2025 original authors
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

import java.util.List;

/**
 * The SQL table definition. The information is extracted from the {@link io.micronaut.data.model.PersistentEntity}.
 *
 * @param schema The schema name, not required
 * @param name The table name
 * @param primaryKeyColumns The list of primary key columns, can be null or empty
 * @param columns The list of columns
 * @param sequences The list of table sequences, can be null or empty
 *
 * @author radovanradic
 * @since 4.13.0
 */
@Internal
public record SqlTableDefinition(
    String schema,
    String name,
    List<SqlColumnDefinition> primaryKeyColumns,
    List<SqlColumnDefinition> columns,
    List<SqlSequenceDefinition> sequences
) {
    public SqlTableDefinition(String schema, String name, List<SqlColumnDefinition> primaryKeyColumns, List<SqlColumnDefinition> columns) {
        this(schema, name, primaryKeyColumns, columns, null);
    }
}
