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
    public String getPrimaryKeysQuery() {
        // The catalog, the information schema only shows the constraints of the tables the user owns or can modify
        return """
            SELECT c.relname, a.attname, k.position
            FROM pg_catalog.pg_constraint con
            JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            CROSS JOIN LATERAL unnest(con.conkey) WITH ORDINALITY AS k(attnum, position)
            JOIN pg_catalog.pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum
            WHERE con.contype = 'p' AND n.nspname = ?""";
    }

    @Override
    public String getIndexesQuery() {
        // An expression has no attribute (attnum 0), its column name is null. The key columns are followed
        // by the INCLUDE columns, which are not the index key. A partial index (with a predicate) is not unique for all the rows
        return """
            SELECT ct.relname, ci.relname, CASE WHEN i.indisunique AND i.indpred IS NULL THEN 1 ELSE 0 END, a.attname, k.position
            FROM pg_catalog.pg_index i
            JOIN pg_catalog.pg_class ct ON ct.oid = i.indrelid
            JOIN pg_catalog.pg_class ci ON ci.oid = i.indexrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = ct.relnamespace
            CROSS JOIN LATERAL unnest(i.indkey::int2[]) WITH ORDINALITY AS k(attnum, position)
            LEFT JOIN pg_catalog.pg_attribute a ON a.attrelid = ct.oid AND a.attnum = k.attnum AND k.attnum > 0
            WHERE n.nspname = ? AND k.position <= i.indnkeyatts""";
    }

    @Override
    public String getForeignKeysQuery() {
        // The columns and the referenced columns are the arrays of the constraint, unnested together
        return """
            SELECT c.relname, con.conname, a.attname, rn.nspname, rc.relname, ra.attname, k.position
            FROM pg_catalog.pg_constraint con
            JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_class rc ON rc.oid = con.confrelid
            JOIN pg_catalog.pg_namespace rn ON rn.oid = rc.relnamespace
            CROSS JOIN LATERAL unnest(con.conkey, con.confkey) WITH ORDINALITY AS k(attnum, refattnum, position)
            JOIN pg_catalog.pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum
            JOIN pg_catalog.pg_attribute ra ON ra.attrelid = con.confrelid AND ra.attnum = k.refattnum
            WHERE con.contype = 'f' AND n.nspname = ?""";
    }

    @Override
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.JSON || columnMapping.getDbType() == SqlDbType.JSON_OBJECT) {
            return "json".equalsIgnoreCase(columnMetadata.typeName()) || "jsonb".equalsIgnoreCase(columnMetadata.typeName());
        }
        return false;
    }
}
