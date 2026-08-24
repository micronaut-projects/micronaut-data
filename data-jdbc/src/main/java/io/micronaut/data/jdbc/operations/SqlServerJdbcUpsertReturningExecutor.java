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
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads generated identities produced by SQL Server {@code MERGE ... OUTPUT} result sets.
 */
@Singleton
final class SqlServerJdbcUpsertReturningExecutor implements JdbcUpsertReturningExecutor {

    @Override
    public Dialect getDialect() {
        return Dialect.SQL_SERVER;
    }

    @Override
    public <T> Result execute(Connection connection,
                              SqlStoredQuery<T, ?> storedQuery,
                              List<Entity<T>> entities,
                              Binder<T> binder,
                              IdReader idReader) throws SQLException {
        List<Object> returnedIds = new ArrayList<>(entities.size());
        int rowsUpdated = 0;
        try (PreparedStatement statement = connection.prepareStatement(storedQuery.getQuery())) {
            for (Entity<T> entity : entities) {
                binder.bind(statement, entity);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        returnedIds.add(idReader.read(resultSet));
                    }
                }
                rowsUpdated++;
                statement.clearParameters();
            }
        }
        return new Result(rowsUpdated, returnedIds);
    }
}
