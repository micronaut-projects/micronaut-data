/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.data.model.schema.sql.SqlTableMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects the problems found while validating the schema so that all of them can be reported at once.
 * <p>
 * Errors are differences that break reading or writing entities (a missing table, column or sequence, or an incompatible
 * column type) and fail the validation. Warnings are differences that can cause failures only for some values
 * (nullability, length, precision) or that don't affect the mapping at all (primary keys, foreign keys, indexes).
 *
 * @author radovanradic
 * @since 5.3.0
 */
@Internal
public final class SchemaValidationResult {

    private static final String FAILED_PREFIX = "Schema validation failed";

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /**
     * @param error The error message
     */
    public void addError(String error) {
        errors.add(error);
    }

    /**
     * @param warning The warning message
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * @return The errors
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * @return The warnings
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * @return Whether any error was found
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Creates the error message for a validation exception thrown by a validator, see
     * {@link SqlTableMappingValidator#validateTable(SqlTableMapping, io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata, io.micronaut.data.model.query.builder.sql.SqlDialectOptions, SchemaValidationResult)}.
     *
     * @param exception The exception
     * @param tableMapping The validated table
     * @return The error message without the common prefix
     */
    static String errorOf(SchemaValidationException exception, SqlTableMapping tableMapping) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Table [" + tableMapping.name() + "] is not valid";
        }
        String prefix = FAILED_PREFIX + ". ";
        return message.startsWith(prefix) ? message.substring(prefix.length()) : message;
    }

    /**
     * Throws {@link SchemaValidationException} reporting all the errors if there are any.
     *
     * @throws SchemaValidationException if there are errors
     */
    public void throwIfErrors() {
        if (errors.isEmpty()) {
            return;
        }
        if (errors.size() == 1) {
            throw new SchemaValidationException(FAILED_PREFIX + ". " + errors.getFirst());
        }
        StringBuilder message = new StringBuilder(FAILED_PREFIX).append(" with ").append(errors.size()).append(" errors:");
        for (String error : errors) {
            message.append(System.lineSeparator()).append(" - ").append(error);
        }
        throw new SchemaValidationException(message.toString());
    }
}
