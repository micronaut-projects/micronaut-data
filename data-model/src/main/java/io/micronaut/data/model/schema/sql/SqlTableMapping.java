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
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The SQL table mapping information extracted from the {@link io.micronaut.data.model.PersistentEntity}.
 *
 * @param schema The schema name, not required
 * @param name The table name
 * @param escape An indicator telling whether table and column names require escaping
 * @param type The table mapping type
 * @param primaryKeyColumns The list of primary key columns, can be null or empty
 * @param columns The list of columns. See {@link SqlColumnMapping}
 * @param sequences The list of table sequences, can be null or empty. See {@link SqlSequenceMapping}
 * @param indexes The list of table indexes, can be null or empty. See {@link SqlIndexMapping}
 * @param auxiliaryStatements Optional additional statements associated with this table, emitted after table creation and before indexes
 * @param uniqueConstraints The JPA unique constraints ({@code @Column(unique = true)}, {@code @Table(uniqueConstraints = ...)}) represented as unique indexes,
 * created and validated only when enabled in the configuration. See {@link SqlIndexMapping}
 *
 * @author radovanradic
 * @since 4.13.0
 */
@Internal
public record SqlTableMapping(
    @Nullable
    String schema,
    String name,
    boolean escape,
    TableType type,
    List<SqlColumnMapping> primaryKeyColumns,
    List<SqlColumnMapping> columns,
    List<SqlSequenceMapping> sequences,
    List<SqlIndexMapping> indexes,
    List<String> auxiliaryStatements,
    List<SqlIndexMapping> uniqueConstraints) {

    public SqlTableMapping(@Nullable String schema, String name, boolean escape, TableType type, List<SqlColumnMapping> primaryKeyColumns,
                           List<SqlColumnMapping> columns, List<SqlSequenceMapping> sequences, List<SqlIndexMapping> indexes,
                           List<String> auxiliaryStatements) {
        this(schema, name, escape, type, primaryKeyColumns, columns, sequences, indexes, auxiliaryStatements, List.of());
    }

    public SqlTableMapping(@Nullable String schema, String name, boolean escape, TableType type, List<SqlColumnMapping> primaryKeyColumns, @Nullable List<SqlColumnMapping> columns) {
        this(schema, name, escape, type, primaryKeyColumns, columns == null ? List.of() : columns, List.of(), List.of(), List.of(), List.of());
    }

    public SqlTableMapping(@Nullable String schema, String name, boolean escape, TableType type, List<SqlColumnMapping> primaryKeyColumns, @Nullable List<SqlColumnMapping> columns, @Nullable List<SqlSequenceMapping> sequences) {
        this(schema, name, escape, type, primaryKeyColumns, columns == null ? List.of() : columns, sequences == null ? List.of() : sequences, List.of(), List.of(), List.of());
    }

    /**
     * Maps all the names of the mapping: the schema, table, column, sequence, index and unique constraint names,
     * used to resolve their property placeholders.
     *
     * @param nameMapper The function mapping a name
     * @return A copy of this mapping with the mapped names
     * @since 5.3.0
     */
    public SqlTableMapping withNames(UnaryOperator<String> nameMapper) {
        return new SqlTableMapping(
            schema == null ? null : nameMapper.apply(schema),
            nameMapper.apply(name),
            escape,
            type,
            mapColumns(primaryKeyColumns == null ? List.of() : primaryKeyColumns, nameMapper),
            mapColumns(columns, nameMapper),
            sequences.stream().map(sequence -> {
                String definedName = sequence.definedName();
                return new SqlSequenceMapping(nameMapper.apply(sequence.columnName()), sequence.definition(),
                    definedName == null ? null : nameMapper.apply(definedName), sequence.dataType(), sequence.generatedValueType());
            }).toList(),
            mapIndexes(indexes, nameMapper),
            auxiliaryStatements,
            mapIndexes(uniqueConstraints, nameMapper));
    }

    private static List<SqlColumnMapping> mapColumns(List<SqlColumnMapping> columns, UnaryOperator<String> nameMapper) {
        return columns.stream().map(column -> column.withName(nameMapper.apply(column.getName()))).toList();
    }

    private static List<SqlIndexMapping> mapIndexes(List<SqlIndexMapping> indexes, UnaryOperator<String> nameMapper) {
        return indexes.stream().map(index -> new SqlIndexMapping(nameMapper.apply(index.name()), index.unique(),
            Arrays.stream(index.columns()).map(nameMapper).toArray(String[]::new), index.sqlIndexDefinitionProvider(),
            index.vectorIndexMetadata(), index.spatial(), index.srid())).toList();
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
