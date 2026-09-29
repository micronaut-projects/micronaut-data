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
 * An implementation of {@link SqlTableMappingValidator} that validates SQL table mappings against
 * ANSI SQL standards.
 *
 * @since 4.13.0
 */
@Internal
@Singleton
final class AnsiSqlTableMappingValidator extends BaseSqlTableMappingValidator {
    @Override
    public Dialect getSupportedDialect() {
        return Dialect.ANSI;
    }

    @Override
    public String getSequenceNamesQuery() {
        return "SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_SCHEMA = ?";
    }

    @Override
    public String getPrimaryKeysQuery() {
        return INFORMATION_SCHEMA_PRIMARY_KEYS_QUERY;
    }

    @Override
    public String getForeignKeysQuery() {
        return INFORMATION_SCHEMA_FOREIGN_KEYS_QUERY;
    }
}
