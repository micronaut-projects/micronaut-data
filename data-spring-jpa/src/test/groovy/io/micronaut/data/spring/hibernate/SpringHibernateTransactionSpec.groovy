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
package io.micronaut.data.spring.hibernate

import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.spring.hibernate.micronaut.HibernateBookRepository
import io.micronaut.data.spring.hibernate.micronaut.ReadOnlyTest
import io.micronaut.data.spring.jpa.hibernate.SpringHibernateConnectionOperations
import io.micronaut.data.spring.jpa.hibernate.SpringHibernateTransactionOperations
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.tests.AbstractTransactionSpec
import io.micronaut.data.tck.tests.TestResourcesDatabaseTestPropertyProvider
import org.hibernate.resource.transaction.spi.TransactionStatus

class SpringHibernateTransactionSpec extends AbstractTransactionSpec implements TestResourcesDatabaseTestPropertyProvider {

    @Override
    Dialect dialect() {
        return Dialect.POSTGRES
    }

    @Override
    Map<String, String> getProperties() {
        return TestResourcesDatabaseTestPropertyProvider.super.getProperties() + [
                "datasources.default.name"                     : "mydb",
                "datasources.default.transaction-manager"       : "springHibernate",
                'jpa.default.properties.hibernate.hbm2ddl.auto': 'create-drop',
                'jpa.default.properties.hibernate.dialect'     : 'org.hibernate.dialect.PostgreSQLDialect'
        ]
    }

    @Override
    String[] getPackages() {
        return ["io.micronaut.data.tck.entities"]
    }

    @Override
    protected SpringHibernateTransactionOperations getTransactionOperations() {
        return context.getBean(SpringHibernateTransactionOperations)
    }

    @Override
    protected ConnectionOperations getConnectionOperations() {
        return context.getBean(SpringHibernateConnectionOperations)
    }

    @Override
    protected Runnable getNoTxCheck() {
        return new Runnable() {
            @Override
            void run() {
                assert getTransactionOperations().getConnection().transaction.status == TransactionStatus.NOT_ACTIVE
            }
        }
    }

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return HibernateBookRepository.class
    }

    @Override
    boolean supportsNoTxProcessing() {
        // Spring always tries to flush which fails because there is no transaction
        return false
    }

    @Override
    boolean supportsDontRollbackOn() {
        return false
    }

    @Override
    boolean supportsReadOnlyFlag() {
        return false
    }

    @Override
    boolean supportsModificationInNonTransaction() {
        return false
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
