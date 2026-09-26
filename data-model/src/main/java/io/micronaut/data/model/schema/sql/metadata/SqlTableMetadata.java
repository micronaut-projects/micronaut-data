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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SQL table metadata extracted from the underlying table in the database.
 */
@Internal
public final class SqlTableMetadata {

    private final @Nullable String catalog;
    private final @Nullable String schema;
    private final String name;
    private final Map<String, SqlColumnMetadata> columns = new LinkedHashMap<>();
    private @Nullable List<String> primaryKeyColumns;
    private @Nullable List<SqlIndexMetadata> indexes;
    private final Map<String, String> columnTypeDefinitions = new LinkedHashMap<>();

    /**
     * Constructs a new instance of SqlTableMetadata with the specified table name.
     *
     * @param catalog the catalog where table belongs, can be null
     * @param schema the schema where table belongs. can be null
     * @param name the name of the SQL table
     */
    public SqlTableMetadata(@Nullable String catalog, @Nullable String schema, String name) {
        this.catalog = catalog;
        this.schema = schema;
        this.name = name;
    }

    /**
     * Adds a new column to the table metadata.
     *
     * @param column the column metadata to add
     */
    public void addColumn(SqlColumnMetadata column) {
        columns.put(column.name().toLowerCase(Locale.ENGLISH), column);
    }

    /**
     * @return the catalog where table belongs, can be null
     * @since 5.3.0
     */
    public @Nullable String getCatalog() {
        return catalog;
    }

    /**
     * @return the schema where table belongs, can be null
     * @since 5.3.0
     */
    public @Nullable String getSchema() {
        return schema;
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

    /**
     * @return all table columns
     * @since 5.3.0
     */
    public Collection<SqlColumnMetadata> getColumns() {
        return Collections.unmodifiableCollection(columns.values());
    }

    /**
     * Sets the full type definition of the column, including the type arguments which are not reported by the
     * standard metadata (for example {@code VECTOR(3,FLOAT32,DENSE)} or {@code vector(3)}).
     *
     * @param column the column name
     * @param typeDefinition the full type definition
     * @since 5.3.0
     */
    public void setColumnTypeDefinition(String column, String typeDefinition) {
        columnTypeDefinitions.put(column.toLowerCase(Locale.ENGLISH), typeDefinition);
    }

    /**
     * @param column the lower case column name
     * @return the full type definition of the column, or null if it was not read
     * @since 5.3.0
     */
    public @Nullable String getColumnTypeDefinition(String column) {
        return columnTypeDefinitions.get(column);
    }

    /**
     * @return the primary key columns in key order, or null if the primary key was not read
     * @since 5.3.0
     */
    public @Nullable List<String> getPrimaryKeyColumns() {
        return primaryKeyColumns;
    }

    /**
     * @param primaryKeyColumns the primary key columns in key order
     * @since 5.3.0
     */
    public void setPrimaryKeyColumns(List<String> primaryKeyColumns) {
        this.primaryKeyColumns = new ArrayList<>(primaryKeyColumns);
    }

    /**
     * @return the table indexes, or null if the indexes were not read
     * @since 5.3.0
     */
    public @Nullable List<SqlIndexMetadata> getIndexes() {
        return indexes;
    }

    /**
     * @param indexes the table indexes
     * @since 5.3.0
     */
    public void setIndexes(List<SqlIndexMetadata> indexes) {
        this.indexes = new ArrayList<>(indexes);
    }
}
