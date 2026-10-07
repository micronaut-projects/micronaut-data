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
 * A validator for H2 table mappings.
 * <p>
 * This class extends {@link BaseSqlTableMappingValidator} and supports the {@link Dialect#H2} dialect.
 * The generic type matching covers the H2 types, the validator only provides the query reading the sequences.
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
    public String getSequenceNamesQuery() {
        return "SELECT SEQUENCE_NAME FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_SCHEMA = ?";
    }

    @Override
    public String getPrimaryKeysQuery() {
        return INFORMATION_SCHEMA_PRIMARY_KEYS_QUERY;
    }

    @Override
    public String getIndexesQuery() {
        return """
            SELECT TABLE_NAME, INDEX_NAME, CASE WHEN IS_UNIQUE THEN 1 ELSE 0 END, COLUMN_NAME, ORDINAL_POSITION
            FROM INFORMATION_SCHEMA.INDEX_COLUMNS WHERE TABLE_SCHEMA = ?""";
    }
}
