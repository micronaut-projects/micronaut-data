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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.r2dbc.spi.Connection;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * The schema handler for R2DBC.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Experimental
public interface R2dbcSchemaHandler {

    /**
     * Creates a new schema.
     *
     * @param connection The R2DBC connection
     * @param dialect    The dialect
     * @param name       The schema name
     *
     * @return The publisher
     */
    Publisher<Void> createSchema(Connection connection, Dialect dialect, String name);

    /**
     * Uses the given schema.
     *
     * @param connection The R2DBC connection
     * @param dialect    The dialect
     * @param name       The schema name
     *
     * @return The publisher
     */
    Publisher<Void> useSchema(Connection connection, Dialect dialect, String name);

    /**
     * Reads the setting that determines the schema the connection uses: the current schema (H2, Oracle),
     * the search path (PostgreSQL) or the current database (MySQL, SQL Server).
     * It is read before the schema of a tenant is used, and restored with
     * {@link #restoreSchemaSetting(Connection, Dialect, String)} before the connection is closed (returned to the pool).
     *
     * @param connection The R2DBC connection
     * @param dialect    The dialect
     *
     * @return The schema setting, or an empty publisher if it can't be read; the setting is then not restored
     * @since 5.3.0
     */
    default Publisher<String> readSchemaSetting(Connection connection, Dialect dialect) {
        return Mono.empty();
    }

    /**
     * Restores a schema setting read with {@link #readSchemaSetting(Connection, Dialect)}.
     *
     * @param connection The R2DBC connection
     * @param dialect    The dialect
     * @param setting    The schema setting
     *
     * @return The publisher
     * @since 5.3.0
     */
    default Publisher<Void> restoreSchemaSetting(Connection connection, Dialect dialect, String setting) {
        return useSchema(connection, dialect, setting);
    }

}
