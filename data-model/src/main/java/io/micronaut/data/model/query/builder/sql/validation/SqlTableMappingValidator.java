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
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils;
import io.micronaut.data.model.schema.sql.SqlIndexMapping;
import io.micronaut.data.model.schema.sql.SqlSequenceMapping;
import io.micronaut.data.model.schema.sql.SqlTableMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlIndexMetadata;
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
    void validateTable(SqlTableMapping tableMapping, SqlTableMetadata tableMetadata, SqlDialectOptions dialectOptions);

    /**
     * Validates a table definition based on {@link PersistentEntity} mapping against its actual corresponding metadata from the database,
     * collecting all errors and warnings into the given result instead of failing on the first one.
     * <p>
     * The default implementation delegates to {@link #validateTable(SqlTableMapping, SqlTableMetadata, SqlDialectOptions)}
     * and collects the thrown {@link SchemaValidationException} as an error, so that implementations of the previous
     * contract keep working.
     *
     * @param tableMapping    The SQL table mapping from {@link PersistentEntity} to validate
     * @param tableMetadata   The SQL table metadata from the database to compare against
     * @param dialectOptions  The dialect options
     * @param result          The validation result collecting the problems found
     * @since 5.3.0
     */
    default void validateTable(SqlTableMapping tableMapping, SqlTableMetadata tableMetadata, SqlDialectOptions dialectOptions, SchemaValidationResult result) {
        try {
            validateTable(tableMapping, tableMetadata, dialectOptions);
        } catch (SchemaValidationException e) {
            result.addError(SchemaValidationResult.errorOf(e, tableMapping));
        }
    }

    /**
     * Validates that the sequences used to generate identity values of the table exist in the database.
     *
     * @param tableMapping    The SQL table mapping from {@link PersistentEntity} to validate
     * @param sequenceNames   The names of the sequences existing in the table schema, in lower case
     * @param dialectOptions  The dialect options
     * @param result          The validation result collecting the problems found
     * @since 5.3.0
     */
    default void validateSequences(SqlTableMapping tableMapping, Set<String> sequenceNames, SqlDialectOptions dialectOptions, SchemaValidationResult result) {
        Dialect dialect = dialectOptions.dialect();
        for (SqlSequenceMapping sequence : tableMapping.sequences()) {
            if (!SqlSchemaUtils.requiresSequence(sequence, dialect)) {
                continue;
            }
            String sequenceName = SqlSchemaUtils.resolveSequenceName(tableMapping, sequence, dialect);
            if (!sequenceNames.contains(sequenceName.toLowerCase(Locale.ENGLISH))) {
                result.addError(String.format("Expected sequence [%s] for column [%s] in table [%s] not found",
                    sequenceName, sequence.columnName(), tableMapping.name()));
            }
        }
    }

    /**
     * Validates that the JPA unique constraints of the table ({@link SqlTableMapping#uniqueConstraints()}) exist in the database
     * as unique indexes or unique constraints (reported as unique indexes). Missing unique constraints are reported as warnings.
     *
     * @param tableMapping    The SQL table mapping from {@link PersistentEntity} to validate
     * @param tableMetadata   The SQL table metadata from the database, see {@link SqlTableMetadata#getIndexes()}
     * @param result          The validation result collecting the problems found
     * @since 5.3.0
     */
    default void validateUniqueConstraints(SqlTableMapping tableMapping, SqlTableMetadata tableMetadata, SchemaValidationResult result) {
        List<SqlIndexMetadata> indexes = tableMetadata.getIndexes();
        if (indexes == null) {
            return;
        }
        for (SqlIndexMapping uniqueConstraint : tableMapping.uniqueConstraints()) {
            Set<String> columns = Arrays.stream(uniqueConstraint.columns())
                .map(column -> column.toLowerCase(Locale.ENGLISH))
                .collect(Collectors.toSet());
            boolean found = indexes.stream().anyMatch(index -> index.unique()
                && index.columns().stream().map(column -> column.toLowerCase(Locale.ENGLISH)).collect(Collectors.toSet()).equals(columns));
            if (!found) {
                result.addWarning(String.format("Unique constraint [%s] on columns %s not found in table [%s]",
                    uniqueConstraint.name(), Arrays.toString(uniqueConstraint.columns()), tableMapping.name()));
            }
        }
    }

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
