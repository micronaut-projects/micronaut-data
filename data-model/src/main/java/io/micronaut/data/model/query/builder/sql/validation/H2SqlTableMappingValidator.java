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
 * A validator for SQL table mappings specific to the H2 database dialect.
 * <p>
 * This class extends {@link BaseSqlTableMappingValidator} and provides H2-specific logic for validating
 * column types between the expected {@link SqlColumnMapping} and the actual {@link SqlColumnMetadata}
 * retrieved from the database.
 * <p>
 * It supports the H2 dialect and includes custom type matching for cases where the default comparison
 * in {@link BaseSqlTableMappingValidator#matchingColumnType(SqlColumnMapping, SqlColumnMetadata, Dialect)}
 * is insufficient.
 *
 * @since 4.13.0
 */
@Internal
@Singleton
final class H2SqlTableMappingValidator extends BaseSqlTableMappingValidator {
    @Override
    public Dialect getSupportedDialect() {
        return Dialect.H2;
    }

    @Override
    protected boolean matchingDialectColumnType(SqlColumnMapping columnMapping, SqlColumnMetadata columnMetadata) {
        if (columnMapping.getDbType() == SqlDbType.BINARY) {
            return columnMetadata.type() == Types.BLOB;
        }
        return false;
    }
}
