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
package io.micronaut.data.runtime.reactive

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.async.propagation.ReactorPropagation
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.reactive.ContextInjector
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.coroutines.CoroutineContext

@OptIn(InternalCoroutinesApi::class)
class MicronautContextInjector : ContextInjector {

    override fun <T> injectCoroutineContext(publisher: Publisher<T>, coroutineContext: CoroutineContext): Publisher<T> {
        val propagatedContext = KotlinCoroutinePropagation.findPropagatedContext(coroutineContext)
        if (propagatedContext != null) {
            if (publisher is Mono) {
                return publisher.contextWrite {
                    ReactorPropagation.addPropagatedContext(it, propagatedContext)
                }
            }
            if (publisher is Flux) {
                return publisher.contextWrite {
                    ReactorPropagation.addPropagatedContext(it, propagatedContext)
                }
            }
        }
        return publisher
    }

}
