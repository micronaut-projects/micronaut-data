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
package io.micronaut.transaction.support;

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionState;

/***
 * Generic transaction state operations interface.
 *
 * @param <T> The resource type, such as the connection.
 * @param <S> The transaction state type
 * @author graemerocher
 * @author Denis Stepanov
 * @since 3.4.0
 */
@Blocking
@Internal
public interface TransactionStateOperations<T, S extends TransactionState> {

    /**
     * Execute a read-only transaction within the context of the function.
     *
     * @param state      The transaction state
     * @param definition The transaction definition
     * @param callback   The call back
     * @param <R>        The result
     * @return The result
     */
    <R> R execute(@NonNull S state,
                  @NonNull TransactionDefinition definition,
                  @NonNull TransactionCallback<T, R> callback);

    /**
     * Execute a read-only transaction within the context of the function.
     *
     * @param state    The transaction state
     * @param callback The call back
     * @param <R>      The result
     * @return The result
     */
    <R> R executeRead(@NonNull S state,
                      @NonNull TransactionCallback<T, R> callback);

    /**
     * Execute a default transaction within the context of the function.
     *
     * @param state    The transaction state
     * @param callback The call back
     * @param <R>      The result
     * @return The result
     */
    <R> R executeWrite(@NonNull S state,
                       @NonNull TransactionCallback<T, R> callback);
}
