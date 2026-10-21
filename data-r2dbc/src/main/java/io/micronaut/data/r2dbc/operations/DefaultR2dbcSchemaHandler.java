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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.multitenancy.internal.SchemaNameUtils;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * The default implementation of {@link R2dbcSchemaHandler}.
 *
 * @author Denis Stepanv
 * @since 3.9.0
 */
@Singleton
@Internal
final class DefaultR2dbcSchemaHandler implements R2dbcSchemaHandler {

    @Override
    public Publisher<Void> createSchema(Connection connection, Dialect dialect, String name) {
        String schemaName;
        try {
            schemaName = SchemaNameUtils.render(dialect, name);
        } catch (IllegalArgumentException e) {
            return Mono.error(new DataAccessException("Invalid schema name: " + e.getMessage(), e));
        }
        if (dialect == Dialect.ORACLE) {
            return executeQuery(connection, "CREATE DATABASE " + schemaName + ";");
        } else {
            return executeQuery(connection, "CREATE SCHEMA " + schemaName + ";");
        }
    }

    @Override
    public Publisher<Void> useSchema(Connection connection, Dialect dialect, String name) {
        String schemaName;
        try {
            schemaName = SchemaNameUtils.render(dialect, name);
        } catch (IllegalArgumentException e) {
            return Mono.error(new DataAccessException("Invalid schema name: " + e.getMessage(), e));
        }
        return switch (dialect) {
            case ORACLE -> executeQuery(connection, "ALTER SESSION SET CURRENT_SCHEMA=" + schemaName);
            case SQL_SERVER -> executeQuery(connection, "USE " + schemaName + ";");
            // Keep the legacy string-literal form for simple names. SET SCHEMA does not
            // accept a quoted identifier, so use search_path when identifier quoting is needed.
            case POSTGRES -> schemaName.equals(name)
                ? executeQuery(connection, "SET SCHEMA '" + name + "';")
                : executeQuery(connection, "SET search_path TO " + schemaName + ";");
            case MYSQL -> executeQuery(connection, "USE " + schemaName + ";");
            case H2 -> executeQuery(connection, "SET SCHEMA " + schemaName + ";");
            default -> Mono.error(new DataAccessException("Unsupported 'useSchema' for dialect:" + dialect));
        };
    }

    @Override
    public Publisher<String> readSchemaSetting(Connection connection, Dialect dialect) {
        String query = switch (dialect) {
            case H2 -> "SELECT SCHEMA()";
            // The whole search path: the schema of a tenant replaces it
            case POSTGRES -> "SELECT current_setting('search_path')";
            case MYSQL -> "SELECT DATABASE()";
            case SQL_SERVER -> "SELECT DB_NAME()";
            case ORACLE -> "SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') FROM DUAL";
            default -> null;
        };
        if (query == null) {
            return Mono.empty();
        }
        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
            DataSettings.QUERY_LOG.trace("Executing Query: {}", query);
        }
        return Flux.from(connection.createStatement(query).execute())
            .flatMap(result -> result.map((row, metadata) -> Optional.ofNullable(row.get(0, String.class))))
            // Consumes the whole result before the connection is used again
            .collectList()
            .flatMap(values -> values.isEmpty() ? Mono.empty() : Mono.justOrEmpty(values.get(0)));
    }

    @Override
    public Publisher<Void> restoreSchemaSetting(Connection connection, Dialect dialect, String setting) {
        try {
            return switch (dialect) {
                case H2 -> executeQuery(connection, "SET SCHEMA " + SchemaNameUtils.quote(dialect, setting));
                case POSTGRES -> {
                    if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                        DataSettings.QUERY_LOG.trace("Restoring the connection search path to: {}", setting);
                    }
                    yield Flux.from(connection.createStatement("SELECT set_config('search_path', $1, false)").bind(0, setting).execute())
                        .flatMap(result -> result.map((row, metadata) -> 1))
                        .then();
                }
                case MYSQL, SQL_SERVER -> executeQuery(connection, "USE " + SchemaNameUtils.quote(dialect, setting));
                case ORACLE -> executeQuery(connection, "ALTER SESSION SET CURRENT_SCHEMA=" + SchemaNameUtils.quote(dialect, setting));
                default -> useSchema(connection, dialect, setting);
            };
        } catch (IllegalArgumentException e) {
            return Mono.error(new DataAccessException("Invalid schema name: " + e.getMessage(), e));
        }
    }

    private static Publisher<Void> executeQuery(Connection connection, String query) {
        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
            DataSettings.QUERY_LOG.trace("Executing Query: {}", query);
        }
        return Flux.from(connection.createStatement(query).execute())
            .flatMap(Result::getRowsUpdated)
            .then();
    }

}
