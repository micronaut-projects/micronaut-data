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
