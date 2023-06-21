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
            if (dialect == Dialect.ORACLE) {
                executeQuery(connection, "CREATE DATABASE " + name + ";");
            } else {
                executeQuery(connection, "CREATE SCHEMA " + name + ";");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create the schema: " + e.getMessage(), e);
        }
    }

    @Override
    public void useSchema(Connection connection, Dialect dialect, String name) {
        try {
            switch (dialect) {
                case ORACLE:
                    executeQuery(connection, "ALTER SESSION SET CURRENT_SCHEMA=" + name);
                    break;
                case SQL_SERVER:
                case MYSQL:
                    executeQuery(connection, "USE " + name + ";");
                    break;
                case POSTGRES:
                    if (DataSettings.QUERY_LOG.isTraceEnabled()) {
                        DataSettings.QUERY_LOG.trace("Changing the connection schema to: {}", name);
                    }
                    connection.setSchema(name);
                    break;
                default:
                    executeQuery(connection, "SET SCHEMA " + name + ";");
                    break;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change the schema: " + e.getMessage(), e);
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
