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
 * The SQL table mapping information extracted from the {@link io.micronaut.data.model.PersistentEntity}.
 *
 * @param schema The schema name, not required
 * @param name The table name
 * @param type The table mapping type
 * @param primaryKeyColumns The list of primary key columns, can be null or empty
 * @param columns The list of columns. See {@link SqlColumnMapping}
 * @param sequences The list of table sequences, can be null or empty. See {@link SqlSequenceMapping}
 *
 * @author radovanradic
 * @since 4.13.0
 */
@Internal
public record SqlTableMapping(
    String schema,
    String name,
    TableType type,
    List<SqlColumnMapping> primaryKeyColumns,
    List<SqlColumnMapping> columns,
    List<SqlSequenceMapping> sequences
) {
    public SqlTableMapping(String schema, String name, TableType type, List<SqlColumnMapping> primaryKeyColumns, List<SqlColumnMapping> columns) {
        this(schema, name, type, primaryKeyColumns, columns, null);
    }

    /**
     * The SQL table mapping table type.
     */
    public enum TableType {
        /**
         * Table mapping created from the actual entity.
         */
        MAIN,
        /**
         * Table mapping created from the entity relations - join table.
         */
        JOIN
    }
}
