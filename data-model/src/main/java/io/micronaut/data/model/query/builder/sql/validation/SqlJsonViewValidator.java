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
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.model.schema.sql.SqlJsonViewMapping;
import io.micronaut.data.model.schema.sql.metadata.SqlJsonViewMetadata;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates Oracle JSON relational duality views against the {@link JsonView} entities.
 * <p>
 * A missing or invalid view, a different root table, a table of a sub view missing in the view, and a JSON field
 * not found or stored in a different column are errors. Different allowed operations and view fields not mapped
 * by the entity are warnings.
 *
 * @author radovanradic
 * @since 5.3.0
 */
@Internal
public final class SqlJsonViewValidator {

    private static final String VALID_STATUS = "VALID";

    private SqlJsonViewValidator() {
    }

    /**
     * Validates the JSON view mapping against the view metadata.
     *
     * @param mapping The view mapping derived from the {@link JsonView} entity
     * @param metadata The view metadata from the database, null if the view doesn't exist
     * @param result The validation result collecting the problems found
     */
    public static void validate(SqlJsonViewMapping mapping, @Nullable SqlJsonViewMetadata metadata, SchemaValidationResult result) {
        String viewName = mapping.schema() == null ? mapping.name() : mapping.schema() + "." + mapping.name();
        if (metadata == null) {
            result.addError("Expected JSON view [" + viewName + "] not found");
            return;
        }
        if (metadata.status() != null && !VALID_STATUS.equalsIgnoreCase(metadata.status())) {
            result.addError("JSON view [" + viewName + "] has status [" + metadata.status() + "]");
        }
        if (!mapping.rootTable().equalsIgnoreCase(metadata.rootTable())) {
            result.addError("JSON view [" + viewName + "] has root table [" + metadata.rootTable() + "] but the view entity is mapped to table [" + mapping.rootTable() + "]");
            return;
        }
        Set<JsonView.Operation> operations = EnumSet.noneOf(JsonView.Operation.class);
        if (metadata.allowInsert()) {
            operations.add(JsonView.Operation.INSERT);
        }
        if (metadata.allowUpdate()) {
            operations.add(JsonView.Operation.UPDATE);
        }
        if (metadata.allowDelete()) {
            operations.add(JsonView.Operation.DELETE);
        }
        if (!operations.equals(EnumSet.copyOf(mapping.operations()))) {
            result.addWarning("JSON view [" + viewName + "] allows operations " + operations + " but the view entity declares " + EnumSet.copyOf(mapping.operations()));
        }
        Set<String> viewTables = metadata.tables().stream().map(SqlJsonViewValidator::lowerCase).collect(Collectors.toSet());
        for (String table : mapping.tables()) {
            if (!viewTables.contains(lowerCase(table))) {
                result.addError("JSON view [" + viewName + "] does not include table [" + table + "]");
            }
        }
        Map<String, SqlJsonViewMetadata.Field> viewFields = new HashMap<>();
        for (SqlJsonViewMetadata.Field field : metadata.fields()) {
            viewFields.put(fieldKey(field.table(), field.key()), field);
        }
        Set<String> mappedFields = new HashSet<>();
        for (SqlJsonViewMapping.Field field : mapping.fields()) {
            String key = fieldKey(field.table(), field.key());
            mappedFields.add(key);
            SqlJsonViewMetadata.Field viewField = viewFields.get(key);
            if (viewField == null) {
                if (viewTables.contains(lowerCase(field.table()))) {
                    result.addError("JSON view [" + viewName + "] field [" + field.key() + "] of table [" + field.table() + "] not found");
                }
            } else if (field.column() != null && !field.column().equalsIgnoreCase(viewField.column())) {
                result.addError("JSON view [" + viewName + "] field [" + field.key() + "] of table [" + field.table() + "] is stored in column ["
                    + viewField.column() + "] but the view entity maps it to column [" + field.column() + "]");
            }
        }
        for (SqlJsonViewMetadata.Field field : metadata.fields()) {
            if (!mappedFields.contains(fieldKey(field.table(), field.key()))) {
                result.addWarning("JSON view [" + viewName + "] field [" + field.key() + "] of table [" + field.table() + "] is not mapped by the view entity");
            }
        }
    }

    private static String fieldKey(String table, String key) {
        // JSON keys are case-sensitive, the table names are not
        return lowerCase(table) + "/" + key;
    }

    private static String lowerCase(String value) {
        return value.toLowerCase(Locale.ENGLISH);
    }
}
