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
package io.micronaut.data.r2dbc.transaction

import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.reactive.ReactiveConnectionStatus
import io.micronaut.data.connection.reactive.ReactiveConnectionSynchronization
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration
import io.micronaut.data.r2dbc.connection.DefaultR2dbcReactorConnectionOperations
import io.micronaut.data.r2dbc.operations.R2dbcSchemaHandler
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.support.DefaultTransactionDefinition
import io.micronaut.transaction.support.ReactiveTransactionExecutionListener
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.IsolationLevel
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean

class TransactionCompletionThrowingDriverSpec extends Specification {

    void "the listener synchronizations run when the driver throws instead of returning a failed commit"() {
        given:
            def completed = new AtomicBoolean()
            def listener = new ReactiveTransactionExecutionListener<Connection>() {
                @Override
                Publisher<Void> beforeBegin(ConnectionStatus<Connection> status, TransactionDefinition definition) {
                    ((ReactiveConnectionStatus<Connection>) status).registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
                        @Override
                        Publisher<Void> onComplete() {
                            return Mono.fromRunnable { completed.set(true) }
                        }

                        @Override
                        Publisher<Void> onError(Throwable failure) {
                            return onComplete()
                        }
                    })
                    return Mono.empty()
                }
            }
            def connection = Stub(Connection) {
                isAutoCommit() >> true
                getTransactionIsolationLevel() >> IsolationLevel.READ_COMMITTED
                close() >> Mono.empty()
                beginTransaction() >> Mono.empty()
                commitTransaction() >> { throw new IllegalStateException('connection closed') }
            }
            def factory = Stub(ConnectionFactory) {
                create() >> Mono.just(connection)
            }
            def connectionOperations = new DefaultR2dbcReactorConnectionOperations('default', factory,
                    new DataR2dbcConfiguration('default', factory, null), null, Stub(R2dbcSchemaHandler))
            def operations = new DefaultR2dbcReactorTransactionOperations('default', connectionOperations, [listener])

        when:
            operations.withTransactionMono(new DefaultTransactionDefinition(), status -> Mono.just(1)).block()

        then:
            thrown(IllegalStateException)
        and: "the synchronizations ran and the transaction is no longer tracked"
            completed.get()
            operations.listenerConnectionStatuses.isEmpty()
    }
}
