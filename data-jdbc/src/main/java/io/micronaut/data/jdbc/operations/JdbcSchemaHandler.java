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
package io.micronaut.data.jdbc.operations;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.query.builder.sql.Dialect;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;

/**
 * JDBC schema handler.
 *
 * @since 3.9.0
 * @author Denis Stepanov
 */
@Experimental
public interface JdbcSchemaHandler {

    /**
     * Creates a new schema.
     *
     * @param connection The JDBC connection
     * @param dialect The dialect
     * @param name The schema name
     */
    void createSchema(Connection connection, Dialect dialect, String name);

    /**
     * Uses the given schema.
     *
     * @param connection The JDBC connection
     * @param dialect The dialect
     * @param name The schema name
     */
    void useSchema(Connection connection, Dialect dialect, String name);

    /**
     * Reads the setting that determines the schema the connection uses: the current schema (H2, Oracle),
     * the search path (PostgreSQL) or the current database (MySQL, SQL Server).
     * It is read before the schema of a tenant is used, and restored with
     * {@link #restoreSchemaSetting(Connection, Dialect, String)} before the connection is closed (returned to the pool).
     *
     * @param connection The JDBC connection
     * @param dialect The dialect
     * @return The schema setting, or {@code null} if it can't be read; the setting is then not restored
     * @since 5.3.0
     */
    default @Nullable String readSchemaSetting(Connection connection, Dialect dialect) {
        return null;
    }

    /**
     * Restores a schema setting read with {@link #readSchemaSetting(Connection, Dialect)}.
     *
     * @param connection The JDBC connection
     * @param dialect The dialect
     * @param setting The schema setting
     * @since 5.3.0
     */
    default void restoreSchemaSetting(Connection connection, Dialect dialect, String setting) {
        useSchema(connection, dialect, setting);
    }

}
