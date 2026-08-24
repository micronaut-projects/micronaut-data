/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Executes the dialect-specific part of an upsert that returns generated values.
 * Entity lifecycle handling and applying returned values remain in the default repository operations.
 */
interface JdbcUpsertReturningExecutor {

    /**
     * @return The supported dialect
     */
    Dialect getDialect();

    /**
     * Executes one or more upsert statements and reads their returned generated values.
     *
     * @param connection The connection
     * @param storedQuery The stored query
     * @param entities The entities to execute
     * @param binder The parameter binder
     * @param idReader The generated identity reader
     * @param <T> The entity type
     * @return The execution result
     * @throws SQLException If statement execution fails
     */
    <T> Result execute(Connection connection,
                       SqlStoredQuery<T, ?> storedQuery,
                       List<Entity<T>> entities,
                       Binder<T> binder,
                       IdReader idReader) throws SQLException;

    /**
     * An entity and its previously captured values.
     *
     * @param entity The entity
     * @param previousValues The previous values
     * @param <T> The entity type
     */
    record Entity<T>(T entity, @Nullable Map<QueryParameterBinding, Object> previousValues) {
    }

    /**
     * The statement execution result.
     *
     * @param rowsUpdated The number of affected rows
     * @param returnedIds The returned generated identities, ordered like the input entities
     */
    record Result(int rowsUpdated, List<Object> returnedIds) {
    }

    /**
     * Binds one entity to a prepared statement.
     *
     * @param <T> The entity type
     */
    @FunctionalInterface
    interface Binder<T> {

        /**
         * @param statement The statement
         * @param entity The entity and previous values
         * @return The number of bound input parameters
         * @throws SQLException If binding fails
         */
        int bind(PreparedStatement statement, Entity<T> entity) throws SQLException;
    }

    /**
     * Reads one generated identity from a result set row.
     */
    @FunctionalInterface
    interface IdReader {

        /**
         * @param resultSet The result set positioned at the generated identity row
         * @return The generated identity
         * @throws SQLException If reading fails
         */
        Object read(ResultSet resultSet) throws SQLException;
    }
}
