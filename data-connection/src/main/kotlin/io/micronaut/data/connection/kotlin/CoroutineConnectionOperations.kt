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
package io.micronaut.data.connection.kotlin

import io.micronaut.core.annotation.Experimental
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionStatus
import kotlin.coroutines.CoroutineContext

/**
 * The connection operations that supports coroutines.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Experimental
interface CoroutineConnectionOperations<C> {

    /**
     * Find optional propagated connection status.
     * @return The connection status.
     */
    fun findConnectionStatus(coroutineContext: CoroutineContext): ConnectionStatus<C>?

    /**
     * Execute the given handler with a new connection.
     * @param definition The definition
     * @param handler The handler
     * @param <R> The result type
     * @return The result
     */
    suspend fun <R> execute(definition: ConnectionDefinition, handler: suspend (ConnectionStatus<C>) -> R): R

    /**
     * Execute the given handler with a new connection.
     * @param handler The handler
     * @param <R> The result type
     * @return The result
     */
    suspend fun <R> execute(handler: suspend (ConnectionStatus<C>) -> R) = execute(ConnectionDefinition.DEFAULT, handler)

}
