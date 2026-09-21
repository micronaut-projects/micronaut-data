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
package io.micronaut.transaction.kotlin

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.ConnectionSynchronization
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.reactive.ReactiveTransactionOperations
import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.util.context.ContextView
import java.util.Optional

/**
 * Verifies that the result type parameter of [CoroutineTransactionOperations.execute] stays unbounded,
 * so that handlers returning a nullable value keep compiling and a `null` result is propagated.
 */
class DefaultCoroutineTransactionOperationsTest {

    private val operations: CoroutineTransactionOperations<String> =
        DefaultCoroutineTransactionOperations(StubReactorTransactionOperations())

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

    private class StubReactorTransactionOperations : ReactorReactiveTransactionOperations<String> {

        override fun findTransactionStatus(contextView: ContextView): Optional<ReactiveTransactionStatus<String>> =
            Optional.empty()

        override fun getTransactionDefinition(contextView: ContextView): TransactionDefinition? = null

        override fun managesTransaction(transactionStatus: ReactiveTransactionStatus<String>) = true

        override fun <T : Any> withTransaction(
            definition: TransactionDefinition,
            handler: ReactiveTransactionOperations.TransactionalCallback<String, T>
        ): Flux<T> = Flux.from(handler.doInTransaction(StubReactiveTransactionStatus()))
    }

    private class StubReactiveTransactionStatus : ReactiveTransactionStatus<String> {

        override fun getConnectionStatus(): ConnectionStatus<String> = StubConnectionStatus()

        override fun isNewTransaction() = true

        override fun setRollbackOnly() = Unit

        override fun isRollbackOnly() = false

        override fun isCompleted() = false

        override fun getTransactionDefinition(): TransactionDefinition = TransactionDefinition.DEFAULT
    }

    private class StubConnectionStatus : ConnectionStatus<String> {

        override fun getConnection() = CONNECTION

        override fun isNew() = true

        override fun getDefinition(): ConnectionDefinition = ConnectionDefinition.DEFAULT

        override fun registerSynchronization(synchronization: ConnectionSynchronization) = Unit
    }
}
