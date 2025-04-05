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
package io.micronaut.data.model.schema.sql.metadata;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * SQL table metadata extracted from the underlying table in the database.
 */
@Internal
public final class SqlTableMetadata {

    private final String name;
    private final Map<String, SqlColumnMetadata> columns = new HashMap<>();

    /**
     * Constructs a new instance of SqlTableMetadata with the specified table name.
     *
     * @param name the name of the SQL table
     */
    public SqlTableMetadata(String name) {
        this.name = name;
    }

    /**
     * Adds a new column to the table metadata.
     *
     * @param column the column metadata to add
     */
    public void addColumn(SqlColumnMetadata column) {
        columns.put(column.name(), column);
    }

    /**
     * Returns the name of the SQL table represented by this metadata object.
     *
     * @return the name of the SQL table
     */
    public String getName() {
        return name;
    }

    /**
     * Retrieves the SQL column metadata associated with the specified column name.
     *
     * @param name the name of the column to retrieve
     * @return the SQL column metadata, or null if no such column exists
     */
    @Nullable
    public SqlColumnMetadata getColumn(String name) {
        return columns.get(name);
    }
}
