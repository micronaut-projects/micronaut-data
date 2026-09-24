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

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Utilities for safely rendering schema names in schema-switching SQL.
 */
@Internal
public final class SchemaNameUtils {

    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SchemaNameUtils() {
    }

    /**
     * Renders a schema name as a dialect-compatible SQL identifier. Simple
     * identifiers are left unquoted to preserve the case-folding behavior of
     * existing applications. All other names are quoted and their delimiters
     * escaped. The input is always treated as a logical name; SQL quoting
     * supplied by a caller is not interpreted.
     *
     * @param dialect The SQL dialect
     * @param name The logical schema name
     * @return The safely rendered identifier
     * @throws IllegalArgumentException If the name is null, empty, or contains a NUL character
     */
    public static String render(Dialect dialect, String name) {
        if (name == null || name.isEmpty() || name.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("Schema name must not be null, empty, or contain a NUL character");
        }
        if (SIMPLE_IDENTIFIER.matcher(name).matches()) {
            return name;
        }
        return switch (dialect) {
            case MYSQL, H2 -> "`" + name.replace("`", "``") + "`";
            // SQL Server bracket-delimited identifiers only use ] as the
            // closing delimiter. A [ inside the name is ordinary content;
            // escaping it would change the schema name.
            case SQL_SERVER -> "[" + name.replace("]", "]]") + "]";
            case ORACLE -> "\"" + name.toUpperCase(Locale.ENGLISH).replace("\"", "\"\"") + "\"";
            default -> "\"" + name.replace("\"", "\"\"") + "\"";
        };
    }
}
