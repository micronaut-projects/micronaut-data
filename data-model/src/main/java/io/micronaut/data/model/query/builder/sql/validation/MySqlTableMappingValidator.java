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
 * A validator for SQL table mappings specific to MySQL databases.
 * <p>
 * This class extends {@link BaseSqlTableMappingValidator} and provides MySQL-specific logic
 * for validating column types and mappings against the actual database schema.
 * <p>
 * It is designed to be used with Micronaut Data and is annotated with {@link Singleton} to
 * indicate that it should be treated as a singleton bean within the application context.
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
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.UUID) {
            return uuidMatchesVarchar(columnMetadata) ||
                // For MariaDB
                (columnMetadata.type() == Types.OTHER && columnMetadata.typeName().equalsIgnoreCase("uuid"));
        }
        if (columnMapping.getDbType() == SqlDbType.BOOLEAN) {
            return columnMetadata.type() == Types.BIT;
        }
        if (columnMapping.getDbType() == SqlDbType.JSON) {
            return columnMetadata.type() == Types.LONGVARCHAR;
        }
        return false;
    }
}
