/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.runtime.multitenancy.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlSchemaNames;

/**
 * Utilities for safely rendering schema names in schema-switching SQL.
 */
@Internal
public final class SchemaNameUtils {

    private SchemaNameUtils() {
    }

    /**
     * Renders a schema name as a dialect-compatible SQL identifier.
     *
     * @param dialect The SQL dialect
     * @param name The logical schema name
     * @return The safely rendered identifier
     * @throws IllegalArgumentException If the name is null, empty, or contains a NUL character
     * @see SqlSchemaNames#render(Dialect, String)
     */
    public static String render(Dialect dialect, String name) {
        return SqlSchemaNames.render(dialect, name);
    }

    /**
     * Builds the Oracle statement that creates a schema.
     *
     * @param name The logical schema name
     * @return The PL/SQL block creating the schema
     * @throws IllegalArgumentException If the name is null, empty, or contains a NUL character
     * @see SqlSchemaNames#oracleCreateSchemaStatement(String)
     */
    public static String oracleCreateSchemaStatement(String name) {
        return SqlSchemaNames.oracleCreateSchemaStatement(name);
    }

    /**
     * Quotes a schema name read from the database as a dialect-compatible SQL identifier.
     * Unlike {@link #render(Dialect, String)}, the name is always quoted and its case is kept,
     * so the identifier names exactly the schema the database reported.
     *
     * @param dialect The SQL dialect
     * @param name The schema name as stored by the database
     * @return The quoted identifier
     * @throws IllegalArgumentException If the name is null, empty, or contains a NUL character
     * @since 5.3.0
     */
    public static String quote(Dialect dialect, String name) {
        if (name == null || name.isEmpty() || name.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("Schema name must not be null, empty, or contain a NUL character");
        }
        return switch (dialect) {
            case MYSQL -> "`" + name.replace("`", "``") + "`";
            case SQL_SERVER -> "[" + name.replace("]", "]]") + "]";
            default -> "\"" + name.replace("\"", "\"\"") + "\"";
        };
    }
}
