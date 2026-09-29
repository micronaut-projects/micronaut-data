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
package io.micronaut.data.model.query.builder.sql.validation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import jakarta.inject.Singleton;

/**
 * A validator for SQL Server table mappings, extending the {@link BaseSqlTableMappingValidator} to provide
 * SQL Server-specific validation logic for SQL table mappings against actual table metadata from the database.
 * <p>
 * This class supports the {@link Dialect#SQL_SERVER} dialect. The generic type matching covers the SQL Server types
 * ({@code VARBINARY(MAX)} for binary data and {@code NVARCHAR(MAX)} for JSON), the validator only provides the query
 * reading the sequences.
 *
 * @since 4.13.0
 */
@Internal
@Singleton
final class SqlServerTableMappingValidator extends BaseSqlTableMappingValidator {
    @Override
    public Dialect getSupportedDialect() {
        return Dialect.SQL_SERVER;
    }

    @Override
    public String getSequenceNamesQuery() {
        return "SELECT s.name FROM sys.sequences s INNER JOIN sys.schemas sc ON s.schema_id = sc.schema_id WHERE sc.name = ?";
    }

    @Override
    public String getPrimaryKeysQuery() {
        return INFORMATION_SCHEMA_PRIMARY_KEYS_QUERY;
    }

    @Override
    public String getIndexesQuery() {
        // A heap has an unnamed index entry, the included columns are not the index key
        return """
            SELECT t.name, i.name, CASE WHEN i.is_unique = 1 THEN 1 ELSE 0 END, c.name, ic.key_ordinal
            FROM sys.indexes i
            JOIN sys.tables t ON t.object_id = i.object_id
            JOIN sys.schemas s ON s.schema_id = t.schema_id
            JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE s.name = ? AND i.name IS NOT NULL AND ic.is_included_column = 0""";
    }
}
