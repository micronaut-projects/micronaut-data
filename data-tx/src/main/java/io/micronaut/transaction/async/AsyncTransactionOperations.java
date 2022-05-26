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
package io.micronaut.transaction.async;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.transaction.TransactionDefinition;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * An interface for async transaction management.
 *
 * @param <C> The connection type
 * @since 3.5.0
 */
public interface AsyncTransactionOperations<C> {

    /**
     * Execute the given handler with a new transaction.
     *
     * @param definition The definition
     * @param handler    The handler
     * @param <T>        The emitted type
     * @return A publisher that emits the result type
     */
    @NonNull
    <T> CompletionStage<T> withTransaction(@NonNull TransactionDefinition definition,
                                           @NonNull Function<AsyncTransactionStatus<C>, CompletionStage<T>> handler);

    /**
     * Execute the given handler with a new transaction.
     *
     * @param handler The handler
     * @param <T>     The emitted type
     * @return A publisher that emits the result type
     */
    default @NonNull <T> CompletionStage<T> withTransaction(@NonNull Function<AsyncTransactionStatus<C>, CompletionStage<T>> handler) {
        return withTransaction(TransactionDefinition.DEFAULT, handler);
    }
}
