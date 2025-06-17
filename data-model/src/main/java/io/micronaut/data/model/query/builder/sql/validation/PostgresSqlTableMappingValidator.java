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

import java.sql.Types;

/**
 * A validator for PostgreSQL table mappings, extending the {@link BaseSqlTableMappingValidator} to provide
 * PostgreSQL-specific validation logic for SQL table mappings against actual table metadata from the database.
 * <p>
 * This class is designed to be used with PostgreSQL databases and supports the {@link Dialect#POSTGRES} dialect.
 * It overrides the {@link #matchingDialectColumnType(SqlColumnMapping, SqlColumnMetadata)} method to handle
 * PostgreSQL-specific column type comparisons, particularly for boolean types.
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
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.BOOLEAN) {
            return columnMetadata.type() == Types.BIT;
        }
        return false;
    }
}
