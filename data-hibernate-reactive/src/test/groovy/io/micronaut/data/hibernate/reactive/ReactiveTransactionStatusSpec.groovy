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
package io.micronaut.data.hibernate.reactive

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import jakarta.inject.Inject
import org.hibernate.reactive.stage.Stage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
class ReactiveTransactionStatusSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    ReactorReactiveTransactionOperations<Stage.Session> transactionOperations

    void "the status of a committed transaction is completed (Mono)"() {
        given:
        ReactiveTransactionStatus<Stage.Session> transactionStatus = null

        when:
        def completedInTransaction = transactionOperations.withTransactionMono { status ->
            transactionStatus = status
            Mono.just(status.isCompleted())
        }.block()

        then:
        !completedInTransaction
        transactionStatus.isCompleted()
    }

    void "the status of a committed transaction is completed (Flux)"() {
        given:
        ReactiveTransactionStatus<Stage.Session> transactionStatus = null

        when:
        def completedInTransaction = transactionOperations.withTransactionFlux { status ->
            transactionStatus = status
            Flux.just(status.isCompleted())
        }.collectList().block()

        then:
        completedInTransaction == [false]
        transactionStatus.isCompleted()
    }

    void "the status of a rolled back transaction is completed"() {
        given:
        ReactiveTransactionStatus<Stage.Session> transactionStatus = null

        when:
        transactionOperations.withTransactionMono { status ->
            transactionStatus = status
            Mono.error(new IllegalStateException("rollback"))
        }.block()

        then:
        thrown(IllegalStateException)
        transactionStatus.isCompleted()
    }

    void "SUPPORTS without an existing transaction executes without a transaction"() {
        when:
        def result = transactionOperations.withTransactionMono(TransactionDefinition.of(TransactionDefinition.Propagation.SUPPORTS)) { status ->
            Mono.just([newTransaction: status.isNewTransaction(), sessionTransaction: status.connection.currentTransaction() != null])
        }.block()

        then:
        result == [newTransaction: false, sessionTransaction: false]
    }
}
