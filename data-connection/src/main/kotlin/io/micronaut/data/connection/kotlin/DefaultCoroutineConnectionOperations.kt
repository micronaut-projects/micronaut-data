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

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Internal
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.reactive.ReactorConnectionOperations
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * The default implementation of CoroutineConnectionOperations that is using the reactive connection manager.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Requires(classes = [ReactorContext::class])
@EachBean(ReactorConnectionOperations::class)
@Singleton
@Internal
class DefaultCoroutineConnectionOperations<C>(private val reactiveConnectionOperations: ReactorConnectionOperations<C>) :
    CoroutineConnectionOperations<C> {

    override suspend fun <R> execute(definition: ConnectionDefinition, handler: suspend (ConnectionStatus<C>) -> R): R {
        return reactiveConnectionOperations.withConnectionMono(definition) {
             mono<R> {
                 val reactorContext = coroutineContext[ReactorContext.Key]
                 if (reactorContext != null) {
                     val micronautPropagatedContext = ReactorPropagation.findPropagatedContext(reactorContext.context).orElse(null)
                     if (micronautPropagatedContext != null) {
                         val newCoroutineContext = KotlinCoroutinePropagation.addPropagatedContext(
                             coroutineContext,
                             micronautPropagatedContext
                         )
                         return@mono withContext(newCoroutineContext) {
                             handler(it)
                         }
                     }
                 }
                 handler(it)
            }
        }.awaitSingle()
    }

    override fun findConnectionStatus(coroutineContext: CoroutineContext): ConnectionStatus<C>? {
        val reactorContext = coroutineContext[ReactorContext.Key]
        if (reactorContext != null) {
            return reactiveConnectionOperations.findConnectionStatus(reactorContext.context).orElse(null)
        }
        return null
    }
}
