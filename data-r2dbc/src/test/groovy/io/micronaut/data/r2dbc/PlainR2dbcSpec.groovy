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
package io.micronaut.data.r2dbc

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.transaction.reactive.ReactiveTransactionOperations
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class PlainR2dbcSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    ConnectionFactory connectionFactory = context.getBean(ConnectionFactory)

    ReactiveTransactionOperations<Connection> reactiveTransactionOperations = context.getBean(ReactiveTransactionOperations)

    protected abstract AuthorRepository getAuthorRepository()

    protected String getInsertQuery() {
        'INSERT INTO author (name) VALUES ($1)'
    }

    def "save one"() {
        when:
            def author = new Author(name: "Denis")
            def result = Flux.usingWhen(connectionFactory.create(), connection -> {
                return Flux.usingWhen(Mono.from(connection.beginTransaction()).then(Mono.just(connection)),
                        (b) -> {
                            return Flux.from(connection.createStatement(getInsertQuery())
                                    .bind(0, author.getName())
                                    .execute()).flatMap { r -> Mono.from(r.getRowsUpdated()) };
                        },
                        (b) -> connection.commitTransaction(),
                        (b, throwable) -> connection.rollbackTransaction(),
                        (b) -> connection.commitTransaction())

            }, { it -> it.close() })
                    .collectList()
                    .block()
        then:
            result.size() == 1
            result[0] == 1
            authorRepository.findByNameContains("Denis").size() == 1
    }

    def "save one - convert flux to mono"() {
        when:
            def author = new Author(name: "Zed")
            def result = Mono.from(Flux.usingWhen(connectionFactory.create(), connection -> {
                return Flux.usingWhen(Mono.from(connection.beginTransaction()).then(Mono.just(connection)),
                        (b) -> {
                            return Flux.from(connection.createStatement(getInsertQuery())
                                    .bind(0, author.getName())
                                    .execute()).flatMap { r -> Mono.from(r.getRowsUpdated()) };
                        },
                        (b) -> connection.commitTransaction(),
                        (b, throwable) -> connection.rollbackTransaction(),
                        (b) -> connection.commitTransaction())

            }, { it -> it.close() })
            ).block()
        then:
            result == 1
            if (isFailsRandomlyWhenConvertingFluxToMono()) {
                true
            } else {
                authorRepository.findByNameContains("Zed").size() == (isFailsWhenConvertingFluxToMono() ? 0 : 1)
            }
    }

    def "save one - reactiveTransactionOperations"() {
        when:
            def author = new Author(name: "John")
            def result = Flux.from(reactiveTransactionOperations.withTransaction { status ->
                return Flux.from(status.connection.createStatement(getInsertQuery())
                        .bind(0, author.getName())
                        .execute()).flatMap { r -> Mono.from(r.getRowsUpdated()) };
            })
                    .collectList()
                    .block()
        then:
            result.size() == 1
            result[0] == 1
            authorRepository.findByNameContains("John").size() == 1
    }

    def "save one - reactiveTransactionOperations - convert flux to mono"() {
        when:
            def author = new Author(name: "Josh")
            def result = Mono.from(reactiveTransactionOperations.withTransaction { status ->
                return Flux.from(status.connection.createStatement(getInsertQuery())
                        .bind(0, author.getName())
                        .execute()).flatMap { r -> Mono.from(r.getRowsUpdated()) };
            }).block()
        then:
            result == 1
        if (isFailsRandomlyWhenConvertingFluxToMono()) {
            true
        } else {
            authorRepository.findByNameContains("Josh").size() == (isFailsWhenConvertingFluxToMono() ? 0 : 1)
        }
    }

    boolean isFailsWhenConvertingFluxToMono() {
        // Override if the driver is not correctly handling `cancel` when Flux is converted to Mono
        return false
    }

    boolean isFailsRandomlyWhenConvertingFluxToMono() {
        return false
    }

    def "save one - without `getRowsUpdated` call nothing is saved"() {
        when:
            def author = new Author(name: "Fred")
            Flux.usingWhen(connectionFactory.create(), connection -> {
                return Flux.usingWhen(Mono.from(connection.beginTransaction()).then(Mono.just(connection)),
                        (b) -> {
                            return Flux.from(connection.createStatement(getInsertQuery())
                                    .bind(0, author.getName())
                                    .execute()).flatMap { r -> Mono.just(1) };
                        },
                        (b) -> connection.commitTransaction(),
                        (b, throwable) -> connection.rollbackTransaction(),
                        (b) -> connection.commitTransaction())

            }, { it -> it.close() })
                    .collectList()
                    .block()
        then:
            authorRepository.findByNameContains("Fred").size() == (isFailsToInsertWithoutGetRowsUpdatedCall() ? 0 : 1)
    }

    boolean isFailsToInsertWithoutGetRowsUpdatedCall() {
        // Override if the driver is not inserting a record without `getRowsUpdated` call
        return false
    }

}