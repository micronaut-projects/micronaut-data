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
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlDbType;
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata;
import jakarta.inject.Singleton;

/**
 * A validator for PostgreSQL table mappings, extending the {@link BaseSqlTableMappingValidator} to provide
 * PostgreSQL-specific validation logic for SQL table mappings against actual table metadata from the database.
 * <p>
 * This class is designed to be used with PostgreSQL databases and supports the {@link Dialect#POSTGRES} dialect.
 * JSON properties can be stored in both {@code jsonb} (the generated type) and {@code json} columns.
 *
 * @since 4.13.0
 */
@Internal
@Singleton
final class PostgresSqlTableMappingValidator extends BaseSqlTableMappingValidator {
    @Override
    public Dialect getSupportedDialect() {
        return Dialect.POSTGRES;
    }

    @Override
    public String getSequenceNamesQuery() {
        return "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = ?";
    }

    @Override
    public String getColumnTypeDefinitionsQuery() {
        return """
            SELECT c.relname, a.attname, format_type(a.atttypid, a.atttypmod)
            FROM pg_catalog.pg_attribute a
            JOIN pg_catalog.pg_class c ON a.attrelid = c.oid
            JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid
            JOIN pg_catalog.pg_type t ON a.atttypid = t.oid
            WHERE n.nspname = ? AND t.typname IN ('vector', 'halfvec', 'sparsevec') AND a.attnum > 0 AND NOT a.attisdropped""";
    }

    @Override
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.JSON || columnMapping.getDbType() == SqlDbType.JSON_OBJECT) {
            return "json".equalsIgnoreCase(columnMetadata.typeName()) || "jsonb".equalsIgnoreCase(columnMetadata.typeName());
        }
        return false;
    }
}
