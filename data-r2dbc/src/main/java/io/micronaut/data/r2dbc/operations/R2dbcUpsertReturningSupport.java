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

import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Shared result handling for dialect-specific upserts that return one generated identity.
 */
final class R2dbcUpsertReturningSupport {

    private R2dbcUpsertReturningSupport() {
    }

    /**
     * Executes the statement and requires exactly one non-null generated identity.
     *
     * @param statement The statement to execute
     * @param resultMapper The dialect-specific returned-value mapper
     * @param emptyResultException The exception produced when no identity is returned
     * @param multipleResultsException The exception produced when multiple identities are returned
     * @return The generated identity
     */
    static Mono<Object> readSingleGeneratedId(
        Statement statement,
        Function<Result, Publisher<Optional<Object>>> resultMapper,
        Supplier<? extends RuntimeException> emptyResultException,
        IntFunction<? extends RuntimeException> multipleResultsException) {
        return Flux.from(statement.execute())
            .flatMap(resultMapper)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collectList()
            .map(ids -> switch (ids.size()) {
                case 0 -> throw emptyResultException.get();
                case 1 -> ids.getFirst();
                default -> throw multipleResultsException.apply(ids.size());
            });
    }
}
