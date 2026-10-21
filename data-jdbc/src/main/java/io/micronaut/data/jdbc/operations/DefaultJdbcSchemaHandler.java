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

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.multitenancy.internal.SchemaNameUtils;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Default {@link JdbcSchemaHandler}.
 *
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Singleton
@Internal
final class DefaultJdbcSchemaHandler implements JdbcSchemaHandler {

    @Override
    public void createSchema(Connection connection, Dialect dialect, String name) {
        try {
            String schemaName = SchemaNameUtils.render(dialect, name);
            if (dialect == Dialect.ORACLE) {
                executeQuery(connection, "CREATE DATABASE " + schemaName + ";");
            } else {
                executeQuery(connection, "CREATE SCHEMA " + schemaName + ";");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create the schema: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Invalid schema name: " + e.getMessage(), e);
        }
    }

    @Override
    public void useSchema(Connection connection, Dialect dialect, String name) {
        try {
            String schemaName = SchemaNameUtils.render(dialect, name);
            switch (dialect) {
                case ORACLE:
                    executeQuery(connection, "ALTER SESSION SET CURRENT_SCHEMA=" + schemaName);
                    break;
                case SQL_SERVER:
                case MYSQL:
                    executeQuery(connection, "USE " + schemaName + ";");
                    break;
                case POSTGRES:
                    if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                        DataSettings.QUERY_LOG.trace("Changing the connection schema to: {}", name);
                    }
                    connection.setSchema(name);
                    break;
                default:
                    executeQuery(connection, "SET SCHEMA " + schemaName + ";");
                    break;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change the schema: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Invalid schema name: " + e.getMessage(), e);
        }
    }

    @Override
    public @Nullable String readSchemaSetting(Connection connection, Dialect dialect) {
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
            return null;
        }
        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
            DataSettings.QUERY_LOG.trace("Executing Query: {}", query);
        }
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the schema setting: " + e.getMessage(), e);
        }
    }

    @Override
    public void restoreSchemaSetting(Connection connection, Dialect dialect, String setting) {
        try {
            switch (dialect) {
                case H2 -> executeQuery(connection, "SET SCHEMA " + SchemaNameUtils.quote(dialect, setting));
                case POSTGRES -> {
                    if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                        DataSettings.QUERY_LOG.trace("Restoring the connection search path to: {}", setting);
                    }
                    try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('search_path', ?, false)")) {
                        statement.setString(1, setting);
                        statement.execute();
                    }
                }
                case MYSQL, SQL_SERVER -> executeQuery(connection, "USE " + SchemaNameUtils.quote(dialect, setting));
                case ORACLE -> executeQuery(connection, "ALTER SESSION SET CURRENT_SCHEMA=" + SchemaNameUtils.quote(dialect, setting));
                default -> useSchema(connection, dialect, setting);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to restore the schema setting: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Invalid schema name: " + e.getMessage(), e);
        }
    }

    private static void executeQuery(Connection connection, String query) throws SQLException {
        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
            DataSettings.QUERY_LOG.trace("Executing Query: {}", query);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

}
