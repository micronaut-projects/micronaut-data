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

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Mono;

/**
 * Executes the dialect-specific part of an R2DBC upsert that returns a generated identity.
 */
interface R2dbcUpsertReturningExecutor {

    /**
     * @return The supported dialect
     */
    Dialect getDialect();

    /**
     * Executes an upsert statement whose input parameters have already been bound.
     *
     * @param statement The bound statement
     * @param storedQuery The stored query
     * @param entity The entity being upserted
     * @param inputParameterCount The number of bound input parameters
     * @param <T> The entity type
     * @return The generated identity result
     */
    <T> Mono<Object> execute(Statement statement,
                             SqlStoredQuery<T, ?> storedQuery,
                             T entity,
                             int inputParameterCount);
}
