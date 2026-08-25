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
import io.micronaut.data.r2dbc.mapper.ColumnNameByIndexR2dbcResultReader;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.operations.internal.sql.OracleReturningMetadata;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.r2dbc.spi.Readable;
import io.r2dbc.spi.Statement;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Executes Oracle upserts that expose generated identities through DML RETURNING OUT parameters.
 */
@Singleton
final class OracleR2dbcUpsertReturningExecutor implements R2dbcUpsertReturningExecutor {

    private final DataConversionService conversionService;

    OracleR2dbcUpsertReturningExecutor(DataConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Dialect getDialect() {
        return Dialect.ORACLE;
    }

    @Override
    public <T> Mono<Result> execute(Statement statement,
                                    SqlStoredQuery<T, ?> storedQuery,
                                    T entity,
                                    Class<?> identityType,
                                    int inputParameterCount) {
        List<QueryOutParameterBinding> outParameters = storedQuery.getOutParameterBindings();
        if (outParameters.size() != 1) {
            return Mono.error(new DataAccessException("Oracle upsert RETURNING requires exactly one generated identity OUT parameter, but got: " + outParameters.size()));
        }
        QueryOutParameterBinding out = outParameters.getFirst();
        OracleR2dbcReturningSupport.bindOracleReturningOutParameters(statement, storedQuery, inputParameterCount);
        OracleReturningMetadata metadata = OracleReturningMetadata.create(List.of(out.name()));
        ColumnNameByIndexR2dbcResultReader resultReader = new ColumnNameByIndexR2dbcResultReader(conversionService, metadata.columnIndexesByName());
        return Flux.from(statement.execute())
            .flatMap(result -> Flux.from(result.map(readable -> Mono.justOrEmpty(mapOutValue(readable, identityType, resultReader, out)))).flatMap(value -> value))
            .collectList()
            .flatMap(values -> {
                if (values.isEmpty()) {
                    return Mono.just(new Result(null));
                }
                if (values.size() > 1) {
                    return Mono.error(new NonUniqueResultException());
                }
                return Mono.just(new Result(values.getFirst()));
            });
    }

    private @Nullable Object mapOutValue(Readable readable,
                                         Class<?> targetType,
                                         ColumnNameByIndexR2dbcResultReader resultReader,
                                         QueryOutParameterBinding out) {
        Object value = resultReader.readDynamic(readable, out.name(), out.dataType());
        if (value == null || targetType.isInstance(value)) {
            return value;
        }
        return conversionService.convert(value, targetType).orElse(null);
    }
}
