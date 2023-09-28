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

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Internal
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.reactive.ReactiveTransactionOperations
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext

/**
 * The default implementation of CoroutineTransactionOperations that is using the reactive TX manager.
 *
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Requires(classes = [ReactorContext::class])
@EachBean(ReactiveTransactionOperations::class)
@Singleton
@Internal
class DefaultCoroutineTransactionOperations<C>(private val reactiveTransactionOperations: ReactiveTransactionOperations<C>) : CoroutineTransactionOperations<C> {

    override suspend fun <R> execute(definition: TransactionDefinition,
                                     handler: suspend (CoroutineTransactionStatus<C>) -> R): R {
        return reactiveTransactionOperations.withTransaction(definition) {
            mono<R> {
                val reactorContext = coroutineContext[ReactorContext.Key]
                if (reactorContext != null) {
                    val micronautPropagatedContext =
                        ReactorPropagation.findPropagatedContext(reactorContext.context).orElse(null)
                    if (micronautPropagatedContext != null) {
                        val newCoroutineContext = KotlinCoroutinePropagation.addPropagatedContext(
                            coroutineContext,
                            micronautPropagatedContext
                        )
                        return@mono withContext(newCoroutineContext) {
                            handler(DefaultCoroutineTransactionStatus(it))
                        }
                    }
                }
                handler(DefaultCoroutineTransactionStatus(it))
            }
        }.awaitSingle()
    }
}
