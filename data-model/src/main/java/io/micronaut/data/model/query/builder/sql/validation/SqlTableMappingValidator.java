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
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata;

/**
 * Validates SQL table mappings against the actual table metadata from the database.
 * <p>
 * Implementations of this interface are responsible for checking that the table and column definitions
 * extracted from a {@link PersistentEntity} match the corresponding metadata from the database.
 * <p>
 * This interface is intended for internal use within the Micronaut Data framework.
 *
 * @since 4.13.0
 * @author radovanradic
 */
@Internal
public interface SqlTableMappingValidator {

    /**
     * Validates a table definition based on {@link PersistentEntity} mapping against its actual corresponding metadata from the database.
     *
     * @param tableMapping    The SQL table mapping from {@link PersistentEntity} to validate
     * @param tableMetadata   The SQL table metadata from the database to compare against
     * @throws SchemaValidationException When expected column not found or is not matching expected type
     */
    void validateTable(SqlTableMapping tableMapping,  SqlTableMetadata tableMetadata);

    /**
     * Returns the SQL dialect supported by this validator.
     *
     * @return the supported SQL dialect, never null
     */
     Dialect getSupportedDialect();
}
