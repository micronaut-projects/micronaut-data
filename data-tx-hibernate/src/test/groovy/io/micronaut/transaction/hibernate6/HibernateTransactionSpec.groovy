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
package io.micronaut.transaction.hibernate6

import io.micronaut.core.type.Argument
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.hibernate.connection.HibernateConnectionOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.tests.AbstractTransactionSpec
import io.micronaut.data.tck.tests.TestResourcesDatabaseTestPropertyProvider
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.hibernate.HibernateTransactionManager
import io.micronaut.transaction.hibernate6.micronaut.HibernateBookRepository
import io.micronaut.transaction.hibernate6.micronaut.ReadOnlyTest
import io.micronaut.data.tck.entities.Book
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.resource.transaction.spi.TransactionStatus

class HibernateTransactionSpec extends AbstractTransactionSpec implements TestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.POSTGRES
    }

    @Override
    Map<String, String> getProperties() {
        return TestResourcesDatabaseTestPropertyProvider.super.getProperties() + [
                "datasources.default.name"                     : "mypgdb",
                'jpa.default.properties.hibernate.hbm2ddl.auto': 'create-drop',
                'jpa.default.properties.hibernate.dialect'     : 'org.hibernate.dialect.PostgreSQLDialect'
        ]
    }

    @Override
    String[] getPackages() {
        return ["io.micronaut.data.tck.entities"]
    }

    @Override
    protected TransactionOperations getTransactionOperations() {
        return context.getBean(HibernateTransactionManager)
    }

    @Override
    protected ConnectionOperations getConnectionOperations() {
        return context.getBean(HibernateConnectionOperations)
    }

    @Override
    protected Runnable getNoTxCheck() {
        ConnectionOperations<Session> connectionOperations = context.getBean(Argument.of(ConnectionOperations.class, Session.class))
        return new Runnable() {
            @Override
            void run() {
                def status = connectionOperations.findConnectionStatus()
                if (status.isEmpty()) {
                    return
                }
                assert status.get()
                        .getConnection()
                        .getTransaction()
                        .getStatus() == TransactionStatus.NOT_ACTIVE
            }
        }
    }

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return HibernateBookRepository.class
    }

    @Override
    boolean supportsReadOnlyFlag() {
        return false
    }

    @Override
    boolean supportsModificationInNonTransaction() {
        // Hibernate always requires TX to modify data
        return false
    }

    def "read-only transaction scoped to an existing session"() {
        given:
            def connectionOperations = getConnectionOperations()
            def transactionOperations = getTransactionOperations()
            def bookRepository = context.getBean(HibernateBookRepository)
            Long bookId = transactionOperations.executeWrite {
                bookRepository.save(new Book(title: "Original", totalPages: 10)).id
            }
        when:
            def state = connectionOperations.executeWrite { status ->
                Session session = status.connection
                Book book = null
                def inTx = transactionOperations.executeRead { txStatus ->
                    book = bookRepository.findById(bookId).get()
                    [txStatus.connection.hibernateFlushMode, txStatus.connection.isReadOnly(book)]
                }
                def afterTx = [session.hibernateFlushMode, session.isReadOnly(book)]
                transactionOperations.executeWrite { txStatus ->
                    // The entity loaded in the read-only transaction is still managed by the outer session
                    book.title = "Updated after read-only"
                }
                [inTx, afterTx]
            }
        then:
            // The session is owned by the outer scope: the read-only mode isn't applied to its entities
            state[0] == [FlushMode.AUTO, false]
            state[1] == [FlushMode.AUTO, false]
            transactionOperations.executeRead { bookRepository.findById(bookId).get().title } == "Updated after read-only"
    }

    def "read-only transaction on a new session"() {
        given:
            def transactionOperations = getTransactionOperations()
        when:
            def state = transactionOperations.executeRead { txStatus ->
                [txStatus.connection.hibernateFlushMode, txStatus.connection.defaultReadOnly]
            }
        then:
            state == [FlushMode.MANUAL, true]
    }

    def "test book is not updated if TX has readOnly=true"() {
        given:
            def service = context.getBean(ReadOnlyTest)
            Long bookId = service.createBook()
        when:
            service.bookIsNotUpdated1(bookId)
        then:
            service.findBook(bookId).title == "New book"
        when:
            service.bookIsNotUpdated2(bookId)
        then:
            service.findBook(bookId).title == "New book"
        when:
            service.bookIsNotUpdated3(bookId)
        then:
            service.findBook(bookId).title == "New book"
        when:
            service.bookIsNotUpdated4(bookId)
        then:
            service.findBook(bookId).title == "New book"
        when:
            service.bookIsUpdated(bookId)
        then:
            service.findBook(bookId).title == "Xyz"
    }

}
