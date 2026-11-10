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
}
