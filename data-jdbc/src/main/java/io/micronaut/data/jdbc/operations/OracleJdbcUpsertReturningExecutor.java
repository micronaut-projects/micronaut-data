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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.mapper.JdbcQueryStatement;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.QueryOutParameterBinding;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import jakarta.inject.Singleton;
import oracle.jdbc.OraclePreparedStatement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Oracle DML-returning execution used by generated-id upserts, including real JDBC batches.
 */
@Singleton
@Requires(classes = OraclePreparedStatement.class)
final class OracleJdbcUpsertReturningExecutor implements JdbcUpsertReturningExecutor {

    @Override
    public Dialect getDialect() {
        return Dialect.ORACLE;
    }

    @Override
    public <T> Result execute(Connection connection,
                              SqlStoredQuery<T, ?> storedQuery,
                              List<Entity<T>> entities,
                              Binder<T> binder,
                              IdReader idReader) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(storedQuery.getQuery())) {
            OraclePreparedStatement oracleStatement = statement.unwrap(OraclePreparedStatement.class);
            boolean returnParametersRegistered = false;
            for (Entity<T> entity : entities) {
                int inputCount = binder.bind(statement, entity);
                if (!returnParametersRegistered) {
                    registerReturnParameters(oracleStatement, storedQuery, inputCount);
                    returnParametersRegistered = true;
                }
                if (entities.size() > 1) {
                    statement.addBatch();
                }
            }
            int rowsUpdated = entities.size() == 1
                ? oracleStatement.executeUpdate()
                : Arrays.stream(statement.executeBatch()).sum();
            List<Object> returnedIds = new ArrayList<>(entities.size());
            try (ResultSet resultSet = oracleStatement.getReturnResultSet()) {
                while (resultSet.next()) {
                    returnedIds.add(idReader.read(resultSet));
                }
            }
            return new Result(rowsUpdated, returnedIds);
        }
    }

    private void registerReturnParameters(OraclePreparedStatement statement,
                                          SqlStoredQuery<?, ?> query,
                                          int inputCount) throws SQLException {
        List<QueryOutParameterBinding> outParameters = query.getOutParameterBindings();
        if (CollectionUtils.isEmpty(outParameters)) {
            throw new DataAccessException("Missing OUT parameter metadata for Oracle RETURNING");
        }
        int position = inputCount;
        for (QueryOutParameterBinding outParameter : outParameters) {
            DataType dataType = query.getDialect().getDataType(outParameter.dataType());
            int sqlType = JdbcQueryStatement.findSqlType(dataType, query.getDialect());
            statement.registerReturnParameter(++position, sqlType == -1 ? Types.VARCHAR : sqlType);
        }
    }
}
