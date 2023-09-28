/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.transaction.kotlin

import io.micronaut.core.annotation.Experimental
import io.micronaut.transaction.TransactionDefinition

/**
 * The transaction operations that supports coroutines.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Experimental
interface CoroutineTransactionOperations<C> {

    /**
     * Execute the given handler with a new transaction.
     * @param definition The definition
     * @param handler The handler
     * @param <R> The result type
     * @return The result
     */
    suspend fun <R> execute(definition: TransactionDefinition, handler: suspend (CoroutineTransactionStatus<C>) -> R): R

    /**
     * Execute the given handler with a new transaction.
     * @param handler The handler
     * @param <R> The result type
     * @return The result
     */
    suspend fun <R> execute(handler: suspend (CoroutineTransactionStatus<C>) -> R) = execute(TransactionDefinition.DEFAULT, handler)

}
