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

import io.micronaut.data.tck.entities.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.exceptions.UnexpectedRollbackException
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import jakarta.inject.Inject
import org.hibernate.reactive.stage.Stage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * A failure in a scope that joined a transaction marks the transaction as rollback-only,
 * even if the caller recovers from the failure.
 */
@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
class ReactiveJoinedTransactionFailureSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    ReactorReactiveTransactionOperations<Stage.Session> transactionOperations

    @Inject
    BookRepository bookRepository

    def setup() {
        bookRepository.deleteAll().block()
    }

    def cleanup() {
        bookRepository.deleteAll().block()
    }

    void "a recovered failure of a joined scope rolls back the transaction (Mono)"() {
        when:
        transactionOperations.withTransactionMono { outer ->
            saveBook("Outer").then(
                transactionOperations.withTransactionMono { inner -> Mono.error(new IllegalStateException("inner")) }
                    .onErrorResume(IllegalStateException) { Mono.just("recovered") }
            )
        }.block()

        then:
        thrown(UnexpectedRollbackException)
        bookRepository.count().block() == 0
    }

    void "a recovered failure of a joined scope rolls back the transaction (Flux)"() {
        when:
        transactionOperations.withTransactionFlux { outer ->
            saveBook("Outer").thenMany(
                transactionOperations.withTransactionFlux { inner -> Flux.error(new IllegalStateException("inner")) }
                    .onErrorResume(IllegalStateException) { Flux.just("recovered") }
            )
        }.collectList().block()

        then:
        thrown(UnexpectedRollbackException)
        bookRepository.count().block() == 0
    }

    void "a recovered failure of a NESTED scope is committed"() {
        when:
        def result = transactionOperations.withTransactionMono { outer ->
            saveBook("Outer").then(
                transactionOperations.withTransactionMono(TransactionDefinition.of(TransactionDefinition.Propagation.NESTED)) { inner ->
                    Mono.error(new IllegalStateException("inner"))
                }.onErrorResume(IllegalStateException) { Mono.just("recovered") }
            )
        }.block()

        then:
        result == "recovered"
        bookRepository.count().block() == 1
    }

    void "rollback-only set by a joined scope rolls back without an error"() {
        when:
        def result = transactionOperations.withTransactionMono { outer ->
            saveBook("Outer").then(
                transactionOperations.withTransactionMono { inner ->
                    inner.setRollbackOnly()
                    Mono.just("done")
                }
            )
        }.block()

        then:
        result == "done"
        bookRepository.count().block() == 0
    }

    void "a transaction without a failed scope is committed"() {
        when:
        def result = transactionOperations.withTransactionMono { outer ->
            saveBook("Outer").then(
                transactionOperations.withTransactionMono { inner -> Mono.just("done") }
            )
        }.block()

        then:
        result == "done"
        bookRepository.count().block() == 1
    }

    private Mono<Book> saveBook(String title) {
        return bookRepository.save(new Book(title: title, totalPages: 10))
    }
}
