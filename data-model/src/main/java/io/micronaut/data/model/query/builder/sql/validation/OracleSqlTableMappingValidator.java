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
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata;
import jakarta.inject.Singleton;

/**
 * An implementation of {@link SqlTableMappingValidator} for Oracle databases.
 * <p>
 * This class extends {@link BaseSqlTableMappingValidator} and provides Oracle-specific logic for validating
 * SQL table mappings against the actual table metadata from the database.
 * Oracle stores all the integral types as {@code NUMBER} with precision, which is matched by the generic
 * type name matching, and a smaller precision is reported as a warning.
 *
 * @since 4.13.0
 */
@Internal
@Singleton
final class OracleSqlTableMappingValidator extends BaseSqlTableMappingValidator {
    @Override
    public Dialect getSupportedDialect() {
        return Dialect.ORACLE;
    }

    @Override
    public String getSequenceNamesQuery() {
        return "SELECT SEQUENCE_NAME FROM ALL_SEQUENCES WHERE SEQUENCE_OWNER = ?";
    }

    @Override
    public String getColumnTypeDefinitionsQuery() {
        // The vector dimension and format are only available in VECTOR_INFO (Oracle 23ai)
        return "SELECT TABLE_NAME, COLUMN_NAME, VECTOR_INFO FROM ALL_TAB_COLS WHERE OWNER = ? AND VECTOR_INFO IS NOT NULL";
    }

    @Override
    public String getPrimaryKeysQuery() {
        // Oracle has no information schema, the driver returns no primary keys for a null table name
        return """
            SELECT cc.TABLE_NAME, cc.COLUMN_NAME, cc.POSITION
            FROM ALL_CONSTRAINTS c
            JOIN ALL_CONS_COLUMNS cc ON cc.OWNER = c.OWNER AND cc.CONSTRAINT_NAME = c.CONSTRAINT_NAME AND cc.TABLE_NAME = c.TABLE_NAME
            WHERE c.CONSTRAINT_TYPE = 'P' AND c.OWNER = ?""";
    }

    @Override
    public String getIndexesQuery() {
        // The driver requires the table name, it also computes the table statistics unless approximate
        return """
            SELECT ic.TABLE_NAME, ic.INDEX_NAME, CASE WHEN i.UNIQUENESS = 'UNIQUE' THEN 1 ELSE 0 END, ic.COLUMN_NAME, ic.COLUMN_POSITION
            FROM ALL_INDEXES i
            JOIN ALL_IND_COLUMNS ic ON ic.INDEX_OWNER = i.OWNER AND ic.INDEX_NAME = i.INDEX_NAME
            WHERE i.TABLE_OWNER = ?""";
    }

    @Override
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping,
                                                SqlColumnMetadata columnMetadata,
                                                SqlDialectOptions dialectOptions) {
        if (isOracleBinaryDoubleOrFloat(columnMetadata.typeName())) {
            return isFloatOrRealOrDouble(columnMapping.getDbType().getType());
        }
        return false;
    }

    private static boolean isOracleBinaryDoubleOrFloat(String typeName) {
        return "BINARY_DOUBLE".equalsIgnoreCase(typeName) || "BINARY_FLOAT".equalsIgnoreCase(typeName);
    }
}
