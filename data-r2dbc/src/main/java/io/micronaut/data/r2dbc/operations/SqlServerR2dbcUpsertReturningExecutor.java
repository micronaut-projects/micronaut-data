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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.exceptions.NonUniqueResultException;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.QueryOutParameterBinding;
import io.micronaut.data.r2dbc.mapper.ColumnIndexR2dbcResultReader;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.r2dbc.spi.Statement;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Executes SQL Server upserts that expose generated identities as OUTPUT result rows.
 */
@Singleton
final class SqlServerR2dbcUpsertReturningExecutor implements R2dbcUpsertReturningExecutor {

    private final ColumnIndexR2dbcResultReader resultReader;

    SqlServerR2dbcUpsertReturningExecutor(DataConversionService conversionService) {
        this.resultReader = new ColumnIndexR2dbcResultReader(conversionService);
    }

    @Override
    public Dialect getDialect() {
        return Dialect.SQL_SERVER;
    }

    @Override
    public <T> Mono<Object> execute(Statement statement,
                                    SqlStoredQuery<T, ?> storedQuery,
                                    T entity,
                                    int inputParameterCount) {
        List<QueryOutParameterBinding> outParameters = storedQuery.getOutParameterBindings();
        if (outParameters.size() != 1) {
            return Mono.error(new DataAccessException("SQL Server upsert OUTPUT requires exactly one generated identity OUT parameter, but got: " + outParameters.size()));
        }
        QueryOutParameterBinding out = outParameters.getFirst();
        return R2dbcUpsertReturningSupport.readSingleGeneratedId(
            statement,
            result -> result.map((row, ignored) -> Optional.ofNullable(
                resultReader.readDynamic(row, 0, out.dataType())
            )),
            () -> new DataAccessException("SQL Server upsert OUTPUT clause produced no generated ID for entity: " + entity),
            count -> new NonUniqueResultException("SQL Server upsert OUTPUT clause produced " + count + " generated IDs for a single entity: " + entity)
        );
    }
}
