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
 * An implementation of {@link SqlTableMappingValidator} for Oracle databases.
 * <p>
 * This class extends {@link BaseSqlTableMappingValidator} and provides Oracle-specific logic for validating
 * SQL table mappings against actual table metadata from an Oracle database.
 * <p>
 * It overrides the {@link #matchingDialectColumnType(SqlColumnMapping, SqlColumnMetadata, SqlDialectOptions)} method to handle
 * Oracle-specific type mappings, such as UUIDs stored as VARCHAR(36) and numeric types represented as NUMBER.
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
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping,
                                                SqlColumnMetadata columnMetadata,
                                                SqlDialectOptions dialectOptions) {
        if (columnMapping.getDbType() == SqlDbType.UUID) {
            return uuidMatchesVarchar(columnMetadata);
        } else if (columnMetadata.type() == Types.NUMERIC) {
            // Custom sql type name for ORACLE
            String oracleSqlType = "NUMBER";
            if (columnMetadata.columnSize() > 0) {
                oracleSqlType += "(" + columnMetadata.columnSize();
                if (columnMetadata.decimalDigits() > 0) {
                    oracleSqlType += "," + columnMetadata.decimalDigits();
                }
                oracleSqlType += ")";
            }
            return columnMapping.getSqlType(Dialect.ORACLE, dialectOptions).equalsIgnoreCase(oracleSqlType);
        } else if (isOracleBinaryDoubleOrFloat(columnMetadata.typeName())) {
            return isFloatOrRealOrDouble(columnMapping.getDbType().getType());
        }
        return false;
    }

    private static boolean isOracleBinaryDoubleOrFloat(String typeName) {
        return "BINARY_DOUBLE".equalsIgnoreCase(typeName) || "BINARY_FLOAT".equalsIgnoreCase(typeName);
    }
}
