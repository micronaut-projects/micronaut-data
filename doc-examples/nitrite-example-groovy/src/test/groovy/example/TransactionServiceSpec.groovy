package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class TransactionServiceSpec extends Specification {

    @Inject TransactionService transactionService
    @Inject BookRepository bookRepository

    def cleanup() {
        bookRepository.deleteAll()
    }

    def "transactional save persists the book"() {
        when:
        transactionService.saveBook("Transactional Book")

        then:
        def books = bookRepository.findAll()
        books.size() == 1
        books[0].title == "Transactional Book"
    }

    def "non-transactional operation persists the book"() {
        when:
        transactionService.logWithoutTransaction("Non-transactional Book")

        then:
        def books = bookRepository.findAll()
        books.size() == 1
        books[0].title == "Non-transactional Book"
    }
}
