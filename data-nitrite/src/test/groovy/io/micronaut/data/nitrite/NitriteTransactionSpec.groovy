package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Book
import io.micronaut.data.nitrite.repository.BookRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import jakarta.transaction.Transactional

@MicronautTest(transactional = false)
@io.micronaut.context.annotation.Property(name = "nitrite.db-path", value = "build/nitrite-tx-test.db")
class NitriteTransactionSpec extends Specification {

    @Inject
    BookRepository bookRepository

    @Inject
    io.micronaut.transaction.SynchronousTransactionManager transactionManager

    def cleanup() {
        new File("build/nitrite-tx-test.db").delete()
    }

    void "test simple transaction"() {
        when:
        def status = transactionManager.getTransaction(io.micronaut.transaction.TransactionDefinition.DEFAULT)
        bookRepository.save(new Book("Tx Book"))
        transactionManager.commit(status)

        then:
        bookRepository.findByTitle("Tx Book") != null
    }

    void "test transaction rollback"() {
        when:
        def status = transactionManager.getTransaction(io.micronaut.transaction.TransactionDefinition.DEFAULT)
        try {
            bookRepository.save(new Book("Rollback Book"))
            throw new RuntimeException("Rollback")
        } catch (e) {
            transactionManager.rollback(status)
        }

        then:
        bookRepository.findByTitle("Rollback Book").isEmpty()
    }

    @Transactional
    void executeInTransaction(Closure closure) {
        closure.call()
    }
}
