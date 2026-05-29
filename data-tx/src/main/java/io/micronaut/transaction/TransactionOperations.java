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
package io.micronaut.transaction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.Blocking;

import java.util.Optional;

/***
 * Generic transaction operations interface.
 *
 * @param <T> The resource type, such as the connection.
 * @author graemerocher
 * @since 1.0.0
 */
@Blocking
public interface TransactionOperations<T> {

    /**
     * Obtains the connection for the current transaction.
     * @return The connection
     * @throws io.micronaut.transaction.exceptions.NoTransactionException if no
     * connection exists for the current transaction
     */
    @NonNull
    T getConnection();

    /**
     * Check if the connection exists.
     *
     * @return True if transaction exists
     * @since 3.3
     */
    boolean hasConnection();

    /**
     * Find optional propagated transaction status.
     * @return The transaction status.
     */
    Optional<TransactionStatus<T>> findTransactionStatus();

    /**
     * Execute a transaction within the context of the function.
     *
     * @param definition The transaction definition
     * @param callback The call back
     * @param <R> The result
     * @return The result
     */
    <R extends @Nullable Object> R execute(@NonNull TransactionDefinition definition, @NonNull TransactionCallback<T, R> callback);

    /**
     * Execute a read-only transaction within the context of the function.
     *
     * @param callback The call back
     * @param <R> The result
     * @return The result
     */
    default <R extends @Nullable Object> R executeRead(@NonNull TransactionCallback<T, R> callback) {
        return execute(TransactionDefinition.READ_ONLY, callback);
    }

    /**
     * Execute a default transaction within the context of the function.
     *
     * @param callback The call back
     * @param <R> The result
     * @return The result
     */
    default <R extends @Nullable Object> R executeWrite(@NonNull TransactionCallback<T, R> callback) {
        return execute(TransactionDefinition.DEFAULT, callback);
    }

    /**
     * Determine whether the given transaction status refers to a transaction
     * managed by this {@link TransactionOperations} instance.
     *
     * @param transactionStatus The transaction status to verify
     * @return true if the transaction is managed (i.e. created/supplied) by this operations instance
     * @since 5.0
     */
    boolean managesTransaction(@NonNull TransactionStatus<T> transactionStatus);
}
