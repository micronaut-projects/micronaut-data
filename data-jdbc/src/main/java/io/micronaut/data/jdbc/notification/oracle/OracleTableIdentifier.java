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
package io.micronaut.data.jdbc.notification.oracle;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A canonical Oracle table identifier and its SQL representation.
 *
 * @param schema The optional canonical schema name
 * @param table The canonical table name
 * @param sqlName The table identifier rendered for Oracle SQL
 */
record OracleTableIdentifier(@Nullable String schema, String table, String sqlName) {

    /**
     * Parses an identifier rendered for Oracle SQL, such as {@code "APP"."BOOK"}.
     *
     * @param value The rendered identifier
     * @return The parsed identifier
     */
    static OracleTableIdentifier parse(String value) {
        String sqlName = value.trim();
        int separator = findSchemaSeparator(sqlName);
        return separator < 0
            ? new OracleTableIdentifier(null, canonicalize(sqlName), sqlName)
            : new OracleTableIdentifier(
                canonicalize(sqlName.substring(0, separator)),
                canonicalize(sqlName.substring(separator + 1)),
                sqlName
            );
    }

    /**
     * Returns whether an Oracle table name reported in a notification identifies this table.
     * A reported schema is required to match only when the mapped table specifies one.
     *
     * @param changedTableName The table name reported by Oracle
     * @return {@code true} if it identifies this table
     */
    boolean matches(String changedTableName) {
        final OracleTableIdentifier changed;
        try {
            changed = parse(changedTableName);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return table.equals(changed.table) && (schema == null || schema.equals(changed.schema));
    }

    private static int findSchemaSeparator(String value) {
        int separator = -1;
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '"') {
                quoted = !quoted;
            } else if (character == '.' && !quoted) {
                if (separator != -1) {
                    throw invalidIdentifier(value);
                }
                separator = i;
            }
        }
        if (quoted) {
            throw invalidIdentifier(value);
        }
        return separator;
    }

    private static String canonicalize(String value) {
        String identifier = value.trim();
        if (identifier.isEmpty()) {
            throw invalidIdentifier(value);
        }
        if (identifier.charAt(0) == '"') {
            if (identifier.length() < 3 || identifier.charAt(identifier.length() - 1) != '"') {
                throw invalidIdentifier(value);
            }
            return identifier.substring(1, identifier.length() - 1).replace("\"\"", "\"");
        }
        if (identifier.indexOf('"') >= 0 || identifier.chars().anyMatch(Character::isWhitespace)) {
            throw invalidIdentifier(value);
        }
        return identifier.toUpperCase(Locale.ENGLISH);
    }

    private static IllegalArgumentException invalidIdentifier(String value) {
        return new IllegalArgumentException("Invalid Oracle table identifier: " + value);
    }
}
