/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import javax.transaction.Transactional

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
class ReactiveTransactionalAnnotationSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject BookService bookService

    @Inject ReactorReactiveTransactionOperations transactionOperations

    void "test transactional annotation"() {
        when:
        bookService.saveAndSuccess().block()

        then:
        bookService.listBooks().collectList().block().size() == 1

        when:
        bookService.saveAndError().block()

        then:
        thrown(RuntimeException)
        bookService.listBooks().collectList().block().size() == 1

        when:
        bookService.saveAndChecked().block()

        then:
        thrown(Exception)
        bookService.listBooks().collectList().block().size() == 1

        when:
        bookService.saveAndManualRollback().block()

        then:
        bookService.listBooks().collectList().block().size() == 1

        and: "check that tx is not propagated after it's committed"
        bookService.listBooks()
                .collectList()
                .flatMap(list -> {
                    Mono.deferContextual({ ctx ->
                        assert transactionOperations.getTransactionStatus(ctx) == null
                        assert transactionOperations.getTransactionDefinition(ctx) == null
                        return Mono.just(list)
                    })
                })
                .block()
                .size() == 1

    }

    @Transactional
    static class BookService {
        @Inject BookRepository bookRepository

        @Inject ReactorReactiveTransactionOperations transactionOperations

        Flux<Book> listBooks() {
            bookRepository.findAll()
        }

        Mono<Book> saveAndError() {
            bookRepository.save(new Book(title: "The Stand", totalPages: 1000))
            throw new Exception("Bad")
        }

        Mono<Book> saveAndChecked() {
            bookRepository.save(new Book(title: "The Shining", totalPages: 500))
            throw new Exception("Bad")
        }

        Mono<Book> saveAndManualRollback() {
            // Strange Groovy bug doesn't recognized `transactionOperations` as a field
            ReactorReactiveTransactionOperations ops = transactionOperations
            return bookRepository.save(new Book(title: "Stuff", totalPages: 500)).flatMap(b -> {
                return Mono.deferContextual(ctx -> {
                    def txStatus = ops.getTransactionStatus(ctx)
                    def txDefinition = ops.getTransactionDefinition(ctx)
                    assert txStatus
                    assert txDefinition
                    txStatus.setRollbackOnly()
                    return Mono.just(b)
                })
            })
        }

        Mono<Book> saveAndSuccess() {
            bookRepository.save(new Book(title: "IT", totalPages: 1000))
        }
    }
}
