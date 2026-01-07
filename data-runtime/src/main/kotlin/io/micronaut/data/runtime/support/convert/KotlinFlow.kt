/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.runtime.support.convert

import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Internal
import io.micronaut.core.convert.MutableConversionService
import io.micronaut.core.convert.TypeConverterRegistrar
import io.micronaut.data.runtime.support.NullValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * The Kotlin Flow converters.
 *
 * @author Denis Stepanov
 * @since 4.11
 */
@Requires(classes = [Flow::class])
@Prototype
@Internal
internal class KotlinFlow : TypeConverterRegistrar {
    override fun register(conversionService: MutableConversionService) {
        conversionService.addConverter(
            CompletableFuture::class.java,
            Flow::class.java
        ) { completableFuture: CompletableFuture<*> ->
            flow {
                val value = completableFuture.await()
                emit(
                    value
                )
            }
        }
        conversionService.addConverter(
            CompletionStage::class.java,
            Flow::class.java
        ) { completableFuture: CompletionStage<*> ->
            flow {
                val value = completableFuture.await()
                emit(
                    value
                )
            }
        }
        conversionService.addConverter(
            Object::class.java,
            Flow::class.java
        ) { obj: Any ->
            flow {
                emit(
                    obj
                )
            }
        }
        conversionService.addConverter(
            NullValue::class.java,
            Flow::class.java
        ) { _: Any ->
            flow {
                emit(
                    null
                )
            }
        }
    }
}
