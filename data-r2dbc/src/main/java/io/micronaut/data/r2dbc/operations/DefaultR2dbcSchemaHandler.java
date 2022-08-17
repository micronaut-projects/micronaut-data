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
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        if (dialect == Dialect.ORACLE) {
            return executeQuery(connection, "CREATE DATABASE " + name + ";");
        } else {
            return executeQuery(connection, "CREATE SCHEMA " + name + ";");
        }
    }

    @Override
    public Publisher<Void> useSchema(Connection connection, Dialect dialect, String name) {
        switch (dialect) {
            case ORACLE:
                return executeQuery(connection, "ALTER SESSION SET CURRENT_SCHEMA=" + name);
            case SQL_SERVER:
                return executeQuery(connection, "USE " + name + ";");
            case POSTGRES:
                return executeQuery(connection, "SET SCHEMA '" + name + "';");
            case MYSQL:
                return executeQuery(connection, "USE " + name + ";");
            case H2:
                return executeQuery(connection, "SET SCHEMA " + name + ";");
            default:
                return Mono.error(new DataAccessException("Unsupported 'useSchema' for dialect:" + dialect));
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
