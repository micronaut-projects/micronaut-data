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
package io.micronaut.data.mongodb.operations;

import com.mongodb.reactivestreams.client.ClientSession;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * A variation of {@link MongoReactiveRepositoryOperations} with Reactor specific method to execute an operation with the contextual {@link ClientSession}.
 */
@Experimental
public interface MongoReactorRepositoryOperations extends MongoReactiveRepositoryOperations, ReactorReactiveTransactionOperations<ClientSession> {

    /**
     * Starts a new session or reuses one from the context.
     *
     * @param function The function
     * @param <T>      The emitted type
     * @return The processed publisher
     */
    <T> Mono<T> withClientSession(Function<ClientSession, Mono<? extends T>> function);

    /**
     * Starts a new session or reuses one from the context.
     *
     * @param function The function
     * @param <T>      The emitted type
     * @return The processed publisher
     */
    <T> Flux<T> withClientSessionMany(Function<ClientSession, Flux<? extends T>> function);

}
