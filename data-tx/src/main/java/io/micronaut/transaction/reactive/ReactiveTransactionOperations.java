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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.transaction.TransactionDefinition;
import org.reactivestreams.Publisher;


/**
 * An interface fo reactive transaction management.
 *
 * @param <C> The connection type
 * @since 2.2.0
 */
public interface ReactiveTransactionOperations<C> {
    /**
     * Execute the given handler with a new transaction.
     * @param definition The definition
     * @param handler The handler
     * @param <T> The emitted type
     * @return A publisher that emits the result type
     */
    @NonNull
    <T> Publisher<T> withTransaction(@NonNull TransactionDefinition definition, @NonNull TransactionalCallback<C, T> handler);

    /**
     * Execute the given handler with a new transaction.
     * @param handler The handler
     * @param <T> The emitted type
     * @return A publisher that emits the result type
     */
    default @NonNull <T> Publisher<T> withTransaction(@NonNull TransactionalCallback<C, T> handler) {
        return withTransaction(TransactionDefinition.DEFAULT, handler);
    }

    /**
     * A transactional callback interface.
     *
     * @param <C> The connection type
     * @param <T> The emitted type
     */
    @FunctionalInterface
    interface TransactionalCallback<C, T> {
        /**
         * Invokes the given code passing the {@link ReactiveTransactionStatus}.
         * @param status The status
         * @return A publisher that emits the return type
         * @throws Exception If an error occurs, though generally these should be emitted through the returned {@link Publisher}
         */
        Publisher<T> doInTransaction(ReactiveTransactionStatus<C> status) throws Exception;
    }
}
