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

import java.sql.Connection;
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

    private static void executeQuery(Connection connection, String query) throws SQLException {
        if (DataSettings.QUERY_LOG.isTraceEnabled()) {
            DataSettings.QUERY_LOG.trace("Executing Query: {}", query);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(query);
        }
    }

}
