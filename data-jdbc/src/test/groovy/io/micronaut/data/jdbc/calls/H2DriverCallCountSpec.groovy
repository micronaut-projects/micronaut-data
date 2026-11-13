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
package io.micronaut.data.jdbc.calls

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection

/**
 * Records the driver calls of typical repository operations on H2, so that a change in the number of statements
 * prepared or executed, or in the connection calls of a transaction, shows up as a test failure.
 */
class H2DriverCallCountSpec extends Specification implements H2TestPropertyProvider {

    /**
     * The connection calls of one statement executed outside a transaction.
     */
    static final Map<String, Integer> ONE_STATEMENT = [
        "Connection.close"            : 1,
        "Connection.prepareStatement" : 1,
        "DataSource.getConnection"    : 1
    ]

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    CountedBookRepository repository = ctx.getBean(CountedBookRepository)

    @Shared
    TransactionOperations<Connection> transactionOperations = ctx.getBean(TransactionOperations)

    @Shared
    DriverCallCounter counter = ctx.getBean(DriverCallCounter)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
            'datasources.default.url'          : 'jdbc:h2:mem:driverCallCount;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
            (DriverCallCounter.ENABLED_PROPERTY): 'true'
        ]
    }

    def cleanup() {
        repository.deleteAll()
    }

    void "find by id prepares and executes one query"() {
        given:
            def book = repository.save(new CountedBook(title: "a", pages: 10))
            counter.reset()
        when:
            def found = repository.findById(book.id)
        then:
            found.present
            counter.connectionCalls() == ONE_STATEMENT
            counter.executes() == 1
            counter.count("PreparedStatement.executeQuery") == 1
    }

    void "save prepares one insert and reads the generated keys"() {
        given:
            counter.reset()
        when:
            repository.save(new CountedBook(title: "a", pages: 10))
        then:
            counter.connectionCalls() == ONE_STATEMENT
            counter.executes() == 1
            counter.count("PreparedStatement.executeUpdate") == 1
            counter.count("PreparedStatement.getGeneratedKeys") == 1
    }

    void "update prepares and executes one update"() {
        given:
            def book = repository.save(new CountedBook(title: "a", pages: 10))
            counter.reset()
        when:
            book.pages = 20
            repository.update(book)
        then:
            counter.connectionCalls() == ONE_STATEMENT
            counter.executes() == 1
            counter.count("PreparedStatement.executeUpdate") == 1
    }

    void "saveAll executes one batch"() {
        given:
            counter.reset()
        when:
            repository.saveAll([new CountedBook(title: "a", pages: 10), new CountedBook(title: "b", pages: 20), new CountedBook(title: "c", pages: 30)])
        then:
            counter.connectionCalls() == ONE_STATEMENT
            counter.batchedRows() == 3
            counter.executes() == 1
            counter.count("PreparedStatement.executeBatch") == 1
            counter.count("PreparedStatement.getGeneratedKeys") == 1
    }

    void "updateAll executes one batch"() {
        given:
            def books = repository.saveAll([new CountedBook(title: "a", pages: 10), new CountedBook(title: "b", pages: 20), new CountedBook(title: "c", pages: 30)])
            counter.reset()
        when:
            books.each { it.pages++ }
            repository.updateAll(books)
        then:
            counter.connectionCalls() == ONE_STATEMENT
            counter.batchedRows() == 3
            counter.executes() == 1
            counter.count("PreparedStatement.executeBatch") == 1
    }

    void "a paged and sorted find outside a transaction runs the page and count queries on separate connections"() {
        given:
            repository.saveAll([new CountedBook(title: "a", pages: 10), new CountedBook(title: "b", pages: 20), new CountedBook(title: "c", pages: 30)])
            counter.reset()
        when:
            Page<CountedBook> page = repository.findByPagesGreaterThan(5, Pageable.from(0, 2, Sort.of(Sort.Order.asc("title"))))
        then:
            page.content*.title == ["a", "b"]
            page.totalSize == 3
            counter.connectionCalls() == [
                "Connection.close"           : 2,
                "Connection.prepareStatement": 2,
                "DataSource.getConnection"   : 2
            ]
            counter.executes() == 2
            counter.count("PreparedStatement.executeQuery") == 2
    }

    void "an IN list gets one placeholder per value"() {
        given:
            def books = repository.saveAll([new CountedBook(title: "a", pages: 10), new CountedBook(title: "b", pages: 20), new CountedBook(title: "c", pages: 30)])
            counter.reset()
        when:
            def found = repository.findByIdIn(books*.id)
        then:
            found.size() == 3
            counter.connectionCalls() == ONE_STATEMENT
            counter.executes() == 1
            counter.statements().size() == 1
            counter.statements()[0].contains("IN (?,?,?)")
            counter.count("PreparedStatement.setLong") == 3
    }

    void "a write transaction uses one connection and commits once"() {
        given:
            def book = repository.save(new CountedBook(title: "a", pages: 10))
            counter.reset()
        when:
            transactionOperations.executeWrite {
                def found = repository.findById(book.id).orElseThrow()
                found.pages = 20
                repository.update(found)
            }
        then:
            counter.connectionCalls() == [
                "Connection.close"           : 1,
                "Connection.commit"          : 1,
                "Connection.getAutoCommit"   : 1,
                "Connection.prepareStatement": 2,
                "Connection.setAutoCommit"   : 2,
                "DataSource.getConnection"   : 1
            ]
            counter.executes() == 2
    }

    void "a read-only transaction also sets and restores the read-only flag"() {
        given:
            def book = repository.save(new CountedBook(title: "a", pages: 10))
            counter.reset()
        when:
            transactionOperations.executeRead {
                repository.findById(book.id).orElseThrow()
                repository.count()
            }
        then:
            counter.connectionCalls() == [
                "Connection.close"           : 1,
                "Connection.commit"          : 1,
                "Connection.getAutoCommit"   : 1,
                "Connection.isReadOnly"      : 1,
                "Connection.prepareStatement": 2,
                "Connection.setAutoCommit"   : 2,
                "Connection.setReadOnly"     : 2,
                "DataSource.getConnection"   : 1
            ]
            counter.executes() == 2
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface CountedBookRepository extends CrudRepository<CountedBook, Long> {

    Page<CountedBook> findByPagesGreaterThan(int pages, Pageable pageable)

    List<CountedBook> findByIdIn(List<Long> ids)
}

@MappedEntity
class CountedBook {
    @Id
    @GeneratedValue
    Long id

    String title

    int pages
}
