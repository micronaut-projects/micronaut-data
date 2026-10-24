package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import spock.lang.Specification

import java.sql.Connection

@MicronautTest(transactional = false)
class BookTitlesViewSpec extends Specification {

    @Inject
    BookRepository bookRepository

    @Inject
    TransactionOperations<Connection> transactionOperations

    void "the contributed view is created over the book table"() {
        when:
        bookRepository.save(new Book("The Left Hand of Darkness", 300))
        List<String> titles = transactionOperations.executeRead { status ->
            List<String> result = []
            status.connection.prepareStatement("SELECT title FROM book_titles").withCloseable { ps ->
                ps.executeQuery().withCloseable { rs ->
                    while (rs.next()) {
                        result << rs.getString(1)
                    }
                }
            }
            return result
        }

        then:
        titles.contains("The Left Hand of Darkness")
    }
}
