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
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.transaction.reactive.ReactiveTransactionOperations
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.BiFunction
import java.util.stream.LongStream

abstract class PlainR2dbcSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    ConnectionFactory connectionFactory = context.getBean(ConnectionFactory)

    R2dbcOperations r2dbcOperations = context.getBean(R2dbcOperations)

    ReactiveTransactionOperations<Connection> reactiveTransactionOperations = context.getBean(ReactiveTransactionOperations)

    ApplicationContext getApplicationContext() {
        return context
    }

    protected abstract AuthorRepository getAuthorRepository()

    protected String getInsertQuery() {
        'INSERT INTO author (name) VALUES ($1)'
    }

    protected String getSelectByIdQuery() {
        'SELECT * FROM author WHERE id=$1'
    }

    protected String correctOutput(String output) {
        return output
    }

    def "use withTransaction and withConnection"() {
        given:
            ByteArrayOutputStream out = new ByteArrayOutputStream()
            System.setOut(new PrintStream(out))
        when:
            authorRepository.deleteAll()
            def author = new Author(name: "Denis")
            authorRepository.save(author)
            Mono<Long> newRecord = Mono.from(r2dbcOperations.<Author> withTransaction(status -> saveAuthor(status.getConnection(), "Test")))
            Long id = newRecord.block()
        then:
            correctOutput(out.toString()).isEmpty()
            id
        when:
            Mono<Author> testResultAuthor = Mono.from(r2dbcOperations.<Author> withTransaction(status -> findById(status.getConnection(), id)))
            Author testAuthor = testResultAuthor.block()
        then:
            correctOutput(out.toString()).isEmpty()
            testAuthor.name == "Test"
        when:
            Mono<Author> testResultAuthor2 = Mono.from(r2dbcOperations.<Author> withConnection(connection -> findById(connection, id)))
            Author testAuthor2 = testResultAuthor2.block()
        then:
            correctOutput(out.toString()).isEmpty()
            testAuthor2.name == "Test"
        when:
            Mono<Author> testResultAuthor3 = Mono.from(r2dbcOperations.<Author> withConnection(connection -> findById(connection, 3333L)))
            Author testAuthor3 = testResultAuthor3.block()
        then:
            correctOutput(out.toString()).isEmpty()
            testAuthor3 == null
        when:
            Mono<Author> testResultAuthor4 = Mono.from(r2dbcOperations.<Author> withTransaction(status -> findById(status.getConnection(), 3333L)))
            Author testAuthor4 = testResultAuthor4.block()
        then:
            correctOutput(out.toString()).isEmpty()
            testAuthor4 == null
        cleanup:
            System.setOut(System.out)
    }

    protected Mono<Long> saveAuthor(Connection connection, String name) {
        return Mono.from(connection.createStatement(getInsertQuery()).bind(0, name).returnGeneratedValues("id").execute())
                .flatMap(r -> Mono.from(r.map((row, rowMetadata) -> row.get("id", Long.class))))
    }

    protected Mono<Author> findById(Connection connection, Long id) {
        return Mono.from(connection.createStatement(getSelectByIdQuery()).bind(0, id).execute())
                .flatMap(r -> Mono.from(r.map(mappingFn())))
                .switchIfEmpty(Mono.empty())
    }

    protected static BiFunction<Row, RowMetadata, Author> mappingFn() {
        return new BiFunction<Row, RowMetadata, Author>() {
            @Override
            Author apply(Row row, RowMetadata rowMetadata) {
                return new Author(name: row.get("name", String));
            }
        }
    }

    def "save one"() {
        when:
            authorRepository.deleteAll()
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

    def "save many"() {
        given:
            authorRepository.deleteAll()
            long recordsCount = 10_000
        when:
            def authors = LongStream.range(0, recordsCount)
                    .mapToObj(id -> new Author(name: "Name " + id))
                    .toList()
            def result = Flux.usingWhen(connectionFactory.create(), connection -> {
                return Flux.usingWhen(Mono.from(connection.beginTransaction()).then(Mono.just(connection)),
                        (b) -> {
                            def stmt = connection.createStatement(getInsertQuery())
                            def it = authors.iterator()
                            boolean isFirst = true
                            while (it.hasNext()) {
                                if (isFirst) {
                                    isFirst = false
                                } else {
                                    // https://github.com/r2dbc/r2dbc-spi/issues/259
                                    stmt = stmt.add()
                                }
                                def author = it.next()
                                stmt = stmt.bind(0, author.getName())
                            }
                            return Flux.from(stmt.execute()).flatMap { Result r -> r.getRowsUpdated() }
                        },
                        (b) -> connection.commitTransaction(),
                        (b, throwable) -> connection.rollbackTransaction(),
                        (b) -> connection.commitTransaction())

            }, { it -> it.close() })
                    .collectList()
                    .block()
        then:
            result.size() == recordsCount
            authorRepository.count() == recordsCount
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
