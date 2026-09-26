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
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata;
import org.jspecify.annotations.Nullable;

import java.util.Set;

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
    default void validateTable(SqlTableMapping tableMapping, SqlTableMetadata tableMetadata) {
        validateTable(tableMapping, tableMetadata, SqlDialectOptions.defaults(getSupportedDialect()));
    }

    /**
     * Validates a table definition based on {@link PersistentEntity} mapping against its actual corresponding metadata from the database.
     *
     * @param tableMapping    The SQL table mapping from {@link PersistentEntity} to validate
     * @param tableMetadata   The SQL table metadata from the database to compare against
     * @param dialectOptions  The dialect options
     * @throws SchemaValidationException When expected column not found or is not matching expected type
     */
    default void validateTable(SqlTableMapping tableMapping, SqlTableMetadata tableMetadata, SqlDialectOptions dialectOptions) {
        SchemaValidationResult result = new SchemaValidationResult();
        validateTable(tableMapping, tableMetadata, dialectOptions, result);
        result.throwIfErrors();
    }

    /**
     * Validates a table definition based on {@link PersistentEntity} mapping against its actual corresponding metadata from the database,
     * collecting all errors and warnings into the given result instead of failing on the first one.
     *
     * @param tableMapping    The SQL table mapping from {@link PersistentEntity} to validate
     * @param tableMetadata   The SQL table metadata from the database to compare against
     * @param dialectOptions  The dialect options
     * @param result          The validation result collecting the problems found
     * @since 5.3.0
     */
    void validateTable(SqlTableMapping tableMapping, SqlTableMetadata tableMetadata, SqlDialectOptions dialectOptions, SchemaValidationResult result);

    /**
     * Validates that the sequences used to generate identity values of the table exist in the database.
     *
     * @param tableMapping    The SQL table mapping from {@link PersistentEntity} to validate
     * @param sequenceNames   The names of the sequences existing in the table schema, in lower case
     * @param dialectOptions  The dialect options
     * @param result          The validation result collecting the problems found
     * @since 5.3.0
     */
    void validateSequences(SqlTableMapping tableMapping, Set<String> sequenceNames, SqlDialectOptions dialectOptions, SchemaValidationResult result);

    /**
     * Returns the query selecting the names of the sequences of a schema. The query has a single parameter, the schema name
     * (the database name for MySQL).
     *
     * @return The query or null if the database doesn't support sequences
     * @since 5.3.0
     */
    default @Nullable String getSequenceNamesQuery() {
        return null;
    }

    /**
     * Returns the query selecting the full type definitions of the schema columns whose type arguments are not reported
     * by the standard metadata (like the vector dimension). The query has a single parameter, the schema name
     * (the database name for MySQL), and selects the table name, the column name and the type definition.
     *
     * @return The query or null if not supported
     * @since 5.3.0
     */
    default @Nullable String getColumnTypeDefinitionsQuery() {
        return null;
    }

    /**
     * Returns the SQL dialect supported by this validator.
     *
     * @return the supported SQL dialect, never null
     */
     Dialect getSupportedDialect();
}
