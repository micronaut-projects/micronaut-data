package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.interceptor.TransactionalInterceptor
import io.micronaut.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.persistence.EntityManager

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class SpringTransactionAnnotation2Spec extends Specification {

    @Inject BookService bookService

    void "test transactional annotation"() {
        when:
        bookService.saveAndSuccess()

        then:
        bookService.listBooks().size() == 1
        !TransactionSynchronizationManager.isSynchronizationActive()

        when:
        bookService.saveAndError()

        then:
        thrown(RuntimeException)
        bookService.listBooks().size() == 1
        !TransactionSynchronizationManager.isSynchronizationActive()

        when:
        bookService.saveAndChecked()

        then:
        thrown(Exception)
        bookService.listBooks().size() == 1
        !TransactionSynchronizationManager.isSynchronizationActive()

        when:
        bookService.saveAndManualRollback()

        then:
        bookService.listBooks().size() == 1
        !TransactionSynchronizationManager.isSynchronizationActive()
    }

    @Transactional
    static class BookService {
        @Inject EntityManager entityManager

        List<Book> listBooks() {
            entityManager.createQuery("from Book").resultList
        }

        void saveAndError() {
            entityManager.persist(new Book(title: "The Stand", totalPages: 1000))
            throw new RuntimeException("Bad")
        }

        void saveAndChecked() {
            entityManager.persist(new Book(title: "The Shining", totalPages: 500))
            throw new Exception("Bad")
        }

        void saveAndManualRollback() {

            entityManager.persist(new Book(title: "Stuff", totalPages: 500))
            TransactionalInterceptor.currentTransactionStatus().setRollbackOnly()
        }

        void saveAndSuccess() {
            entityManager.persist(new Book(title: "IT", totalPages: 1000))
        }
    }
}
