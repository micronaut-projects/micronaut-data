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
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions;
import io.micronaut.data.model.schema.sql.SqlColumnMapping;
import io.micronaut.data.model.schema.sql.SqlDbType;
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata;
import jakarta.inject.Singleton;

import java.sql.Types;

/**
 * A validator for MySQL (and MariaDB) table mappings.
 * <p>
 * This class extends {@link BaseSqlTableMappingValidator} and provides MySQL-specific logic
 * for validating table mappings against the actual database metadata.
 *
 * @since 4.13.0
 */
@Internal
@Singleton
final class MySqlTableMappingValidator extends BaseSqlTableMappingValidator {
    @Override
    public Dialect getSupportedDialect() {
        return Dialect.MYSQL;
    }

    @Override
    public String getSequenceNamesQuery() {
        // Only MariaDB supports sequences
        return "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_TYPE = 'SEQUENCE' AND TABLE_SCHEMA = ?";
    }

    @Override
    public String getColumnTypeDefinitionsQuery() {
        return "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND DATA_TYPE = 'vector'";
    }

    @Override
    public String getPrimaryKeysQuery() {
        // Every MySQL primary key is named PRIMARY, the join also matches the table
        return INFORMATION_SCHEMA_PRIMARY_KEYS_QUERY;
    }

    @Override
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        SqlDbType dbType = columnMapping.getDbType();
        if (dbType == SqlDbType.UUID) {
            // MariaDB native UUID type
            return columnMetadata.type() == Types.OTHER && columnMetadata.typeName().equalsIgnoreCase("uuid");
        }
        if (dbType == SqlDbType.BOOLEAN) {
            // BOOLEAN is TINYINT(1), reported as TINYINT when tinyInt1isBit is disabled
            return columnMetadata.type() == Types.TINYINT;
        }
        if (dbType == SqlDbType.JSON || dbType == SqlDbType.JSON_OBJECT) {
            // MariaDB JSON is an alias for LONGTEXT
            return columnMetadata.type() == Types.LONGVARCHAR;
        }
        return false;
    }

    @Override
    protected boolean matchingDialectDefinedColumnType(String expectedType, SqlColumnMetadata columnMetadata, SqlDialectOptions dialectOptions) {
        if (!"GEOMETRY".equals(expectedType)) {
            return false;
        }
        // The spatial columns can be reported with the subtype name
        return switch (normalizeTypeName(columnMetadata.typeName())) {
            case "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION", "GEOMCOLLECTION" -> true;
            default -> false;
        };
    }
}
