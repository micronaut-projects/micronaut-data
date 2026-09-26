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
package io.micronaut.data.jdbc.h2.external

import io.micronaut.context.annotation.Property
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.h2.H2BookRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.tck.entities.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.ExternalTransactionOperations
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.exceptions.IllegalTransactionStateException
import io.micronaut.transaction.exceptions.TransactionUsageException
import io.micronaut.transaction.exceptions.UnexpectedRollbackException
import io.micronaut.transaction.support.TransactionSynchronization

import static io.micronaut.transaction.support.TransactionSynchronization.Status.COMMITTED
import static io.micronaut.transaction.support.TransactionSynchronization.Status.ROLLED_BACK
import static io.micronaut.transaction.support.TransactionSynchronization.Status.UNKNOWN
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection

@MicronautTest(transactional = false)
@Property(name = "spec.name", value = "H2ExternalTransactionSpec")
class H2ExternalTransactionSpec extends Specification implements H2TestPropertyProvider {

    private static final Set<String> FORBIDDEN = ["commit", "rollback", "close", "setAutoCommit", "setReadOnly", "setTransactionIsolation"] as Set

    @Inject
    H2BookRepository bookRepository

    @Inject
    DataSource dataSource

    @Inject
    ExternalTransactionOperations<Connection> externalTransactionOperations

    @Inject
    TransactionOperations<Connection> transactionOperations

    @Inject
    ExternalTxBookService bookService

    Connection physical
    List<String> calls = []
    Connection external

    @Override
    List<String> packages() {
        return ["io.micronaut.data.tck.entities", "io.micronaut.data.tck.jdbc.entities"]
    }

    def setup() {
        assert committedCount() == 0
        physical = DelegatingDataSource.unwrapDataSource(dataSource).getConnection()
        physical.autoCommit = false          // the owner's responsibility
        external = recording(physical)
    }

    def cleanup() {
        // The owner restores the connection before returning it to the pool
        physical.rollback()
        physical.autoCommit = true
        physical.close()
        bookRepository.deleteAll()
    }

    void "repository DML uses the bound connection and is committed by the owner only"() {
        when:
        externalTransactionOperations.bindExternal(external) { tx ->
            bookRepository.save(book("A"))
            assert transactionOperations.findTransactionStatus().get().connection.is(external)
            tx.beforeCommit()
            assert committedCount() == 0     // not visible to other connections yet
            physical.commit()
            tx.afterCompletion(COMMITTED)
            return null
        }

        then:
        committedCount() == 1
        calls.contains("prepareStatement")
        untouched()
        transactionOperations.findTransactionStatus().isEmpty()
    }

    void "owner rollback discards repository DML"() {
        when:
        externalTransactionOperations.bindExternal(external) { tx ->
            bookRepository.save(book("A"))
            physical.rollback()
            tx.afterCompletion(ROLLED_BACK)
            return null
        }

        then:
        committedCount() == 0
        untouched()
    }

    void "@Transactional REQUIRED and MANDATORY join without committing"() {
        when:
        externalTransactionOperations.bindExternal(external) { tx ->
            bookService.saveRequired("A")
            bookService.saveMandatory("B")
            assert committedCount() == 0
            tx.beforeCommit()
            physical.commit()
            tx.afterCompletion(COMMITTED)
            return null
        }

        then:
        committedCount() == 2
        untouched()
    }

    void "REQUIRES_NEW commits independently and the external transaction resumes"() {
        when:
        externalTransactionOperations.bindExternal(external) { tx ->
            bookRepository.save(book("outer"))
            bookService.saveRequiresNew("inner")
            assert committedCount() == 1     // only the REQUIRES_NEW row
            assert transactionOperations.findTransactionStatus().get().connection.is(external)
            physical.rollback()
            tx.afterCompletion(ROLLED_BACK)
            return null
        }

        then:
        bookRepository.findAll()*.title == ["inner"]
        untouched()
    }

    void "a caught participant failure makes the external transaction rollback-only"() {
        when:
        externalTransactionOperations.bindExternal(external) { tx ->
            try {
                bookService.saveRequiredAndFail("A")
            } catch (IllegalStateException ignored) {
            }
            bookRepository.save(book("B"))
            try {
                tx.beforeCommit()
                assert false: "beforeCommit must fail"
            } catch (UnexpectedRollbackException expected) {
                physical.rollback()
                tx.afterCompletion(ROLLED_BACK)
            }
            return null
        }

        then:
        committedCount() == 0
        untouched()
    }

    void "NESTED failure rolls back only its savepoint"() {
        when:
        externalTransactionOperations.bindExternal(external) { tx ->
            bookRepository.save(book("outer"))
            try {
                bookService.saveNestedAndFail("nested")
            } catch (IllegalStateException ignored) {
            }
            tx.beforeCommit()
            physical.commit()
            tx.afterCompletion(COMMITTED)
            return null
        }

        then:
        bookRepository.findAll()*.title == ["outer"]
        untouched()
    }

    void "synchronizations follow the reported outcome"() {
        given:
        List<String> events = []

        when:
        externalTransactionOperations.bindExternal(external) { tx ->
            transactionOperations.findTransactionStatus().get().registerSynchronization(new TransactionSynchronization() {
                @Override
                void beforeCommit(boolean readOnly) { events << "beforeCommit" }

                @Override
                void afterCommit() { events << "afterCommit" }

                @Override
                void afterCompletion(TransactionSynchronization.Status status) { events << "afterCompletion:" + status }
            })
            tx.beforeCommit()
            physical.commit()
            tx.afterCompletion(outcome)
            return null
        }

        then:
        events == expected

        where:
        outcome     | expected
        COMMITTED   | ["beforeCommit", "afterCommit", "afterCompletion:COMMITTED"]
        ROLLED_BACK | ["beforeCommit", "afterCompletion:ROLLED_BACK"]
        UNKNOWN     | ["beforeCommit", "afterCompletion:UNKNOWN"]
    }

    void "binding inside an active transaction is rejected"() {
        when:
        transactionOperations.executeWrite { status ->
            externalTransactionOperations.bindExternal(external) { tx -> null }
        }

        then:
        thrown(TransactionUsageException)
    }

    void "the owner must report the outcome"() {
        when:
        externalTransactionOperations.bindExternal(external) { tx -> null }

        then:
        thrown(IllegalTransactionStateException)
        transactionOperations.findTransactionStatus().isEmpty()
    }

    private boolean untouched() {
        def forbidden = calls.findAll { FORBIDDEN.contains(it) }
        assert forbidden.isEmpty(): "Micronaut Data touched the external connection: " + forbidden
        return true
    }

    /**
     * Counts committed rows through a separate physical connection, never the bound one.
     */
    private long committedCount() {
        Connection other = DelegatingDataSource.unwrapDataSource(dataSource).getConnection()
        try {
            def rs = other.createStatement().executeQuery("SELECT COUNT(*) FROM book")
            rs.next()
            return rs.getLong(1)
        } finally {
            other.close()
        }
    }

    private Connection recording(Connection target) {
        return (Connection) Proxy.newProxyInstance(getClass().classLoader, [Connection] as Class[], { proxy, method, args ->
            // Savepoint operations (NESTED) are allowed; record only whole-connection calls
            if (!(args != null && args.length == 1 && method.name == "rollback")) {
                calls << method.name
            }
            try {
                return method.invoke(target, args)
            } catch (InvocationTargetException e) {
                throw e.cause
            }
        } as InvocationHandler)
    }

    private static Book book(String title) {
        def book = new Book()
        book.title = title
        book.totalPages = 10
        return book
    }
}
