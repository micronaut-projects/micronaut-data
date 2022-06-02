/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.transaction.reactive;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.transaction.TransactionDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.function.Function;

/**
 * Special version of {@link ReactiveTransactionOperations} that is exposing Reactor publishers.
 *
 * @param <C> The transaction connection
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Experimental
public interface ReactorReactiveTransactionOperations<C> extends ReactiveTransactionOperations<C> {

    /**
     * The prefix of transaction status Reactor context key.
     */
    String TRANSACTION_STATUS_KEY_PREFIX = "io.micronaut.tx.status";

    /**
     * The prefix of transaction definition Reactor context key.
     */
    String TRANSACTION_DEFINITION_KEY_PREFIX = "io.micronaut.tx.definition";

    /**
     * Retrieve the transaction status associated to the current transaction manager from the Reactor context.
     *
     * @param contextView The context view
     * @return the key
     */
    @Nullable
    ReactiveTransactionStatus<C> getTransactionStatus(@NonNull ContextView contextView);

    /**
     * Retrieve the transaction definition associated to the current transaction from the Reactor context.
     *
     * @param contextView The context view
     * @return the key
     */
    @Nullable
    TransactionDefinition getTransactionDefinition(@NonNull ContextView contextView);

    /**
     * Execute the given handler with a new transaction.
     *
     * @param definition The definition
     * @param handler    The handler
     * @param <T>        The emitted type
     * @return A publisher that emits the result type
     */
    default <T> Flux<T> withTransactionFlux(@NonNull TransactionDefinition definition, @NonNull Function<ReactiveTransactionStatus<C>, Flux<T>> handler) {
        return withTransaction(definition, handler::apply);
    }

    /**
     * Execute the given handler with a new transaction.
     *
     * @param handler The handler
     * @param <T>     The emitted type
     * @return A publisher that emits the result type
     */
    default <T> Flux<T> withTransactionFlux(@NonNull Function<ReactiveTransactionStatus<C>, Flux<T>> handler) {
        return withTransactionFlux(TransactionDefinition.DEFAULT, handler);
    }

    /**
     * Execute the given handler with a new transaction.
     *
     * @param definition The definition
     * @param handler    The handler
     * @param <T>        The emitted type
     * @return A publisher that emits the result type
     */
    default <T> Mono<T> withTransactionMono(@NonNull TransactionDefinition definition, @NonNull Function<ReactiveTransactionStatus<C>, Mono<T>> handler) {
        return withTransaction(definition, handler::apply).next();
    }

    /**
     * Execute the given handler with a new transaction.
     *
     * @param handler The handler
     * @param <T>     The emitted type
     * @return A publisher that emits the result type
     */
    default <T> Mono<T> withTransactionMono(@NonNull Function<ReactiveTransactionStatus<C>, Mono<T>> handler) {
        return withTransactionMono(TransactionDefinition.DEFAULT, handler);
    }

    @NonNull
    <T> Flux<T> withTransaction(@NonNull TransactionDefinition definition, @NonNull TransactionalCallback<C, T> handler);

    @NonNull
    default <T> Flux<T> withTransaction(@NonNull TransactionalCallback<C, T> handler) {
        return withTransaction(TransactionDefinition.DEFAULT, handler);
    }

}
