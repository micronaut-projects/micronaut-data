/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.support.AbstractReactorConnectionOperations
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * Verifies that a nullable result of [CoroutineConnectionOperations.execute] is propagated.
 */
class DefaultCoroutineConnectionOperationsTest {

    private val operations: CoroutineConnectionOperations<String> =
        DefaultCoroutineConnectionOperations(StubReactorConnectionOperations())

    @Test
    fun `null result is propagated`() = runBlocking<Unit> {
        val result: String? = operations.execute { null }
        assertNull(result)
    }

    @Test
    fun `non-null result of a nullable type is propagated`() = runBlocking<Unit> {
        val result: String? = operations.execute { "result" }
        assertEquals("result", result)
    }

    @Test
    fun `the connection is exposed to the handler`() = runBlocking<Unit> {
        val result: String? = operations.execute { it.connection }
        assertEquals(CONNECTION, result)
    }

    private companion object {
        private const val CONNECTION = "connection"
    }

    private class StubReactorConnectionOperations : AbstractReactorConnectionOperations<String>() {

        override fun openConnection(definition: ConnectionDefinition): Publisher<String> = Mono.just(CONNECTION)

        override fun closeConnection(connection: String, definition: ConnectionDefinition): Publisher<Void> = Mono.empty()
    }
}
