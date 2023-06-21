package io.micronaut.data.spring.hibernate


import io.micronaut.data.tck.entities.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.hibernate.Session
import spock.lang.Specification

@MicronautTest(packages = "io.micronaut.data.tck.entities", transactional = false)
class SpringTransactionAnnotationSpec extends Specification implements H2Properties {

    @Inject BookService bookService
    @Inject TransactionOperations<Session> transactionOperations

    void "test transactional annotation"() {
        when:
        bookService.saveAndSuccess()

        then:
        bookService.listBooks().size() == 1
        transactionOperations.findTransactionStatus().isEmpty()

        when:
        bookService.saveAndError()

        then:
        thrown(RuntimeException)
        bookService.listBooks().size() == 1
        transactionOperations.findTransactionStatus().isEmpty()

        when:
        bookService.saveAndChecked()

        then:
        thrown(Exception)
        bookService.listBooks().size() == 1
        transactionOperations.findTransactionStatus().isEmpty()

        when:
        bookService.saveAndManualRollback()

        then:
        bookService.listBooks().size() == 1
        transactionOperations.findTransactionStatus().isEmpty()
    }

    @Transactional
    static class BookService {
        @Inject EntityManager entityManager
        @Inject TransactionOperations<Session> transactionOperations

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
            transactionOperations.findTransactionStatus().get().setRollbackOnly()
        }

        void saveAndSuccess() {
            entityManager.persist(new Book(title: "IT", totalPages: 1000))
        }
    }
}
