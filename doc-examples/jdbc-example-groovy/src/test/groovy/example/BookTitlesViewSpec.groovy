package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import spock.lang.Specification

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

@MicronautTest(transactional = false)
class BookTitlesViewSpec extends Specification {

    @Inject
    BookRepository bookRepository

    @Inject
    TransactionOperations<Connection> transactionOperations

    void "the contributed view is created over the book table"() {
        when:
        bookRepository.save(new Book("The Left Hand of Darkness", 300))
        List<String> titles = transactionOperations.executeRead { status -> selectTitles(status.connection) }

        then:
        titles.contains("The Left Hand of Darkness")
    }

    private static List<String> selectTitles(Connection connection) {
        List<String> titles = []
        try (PreparedStatement statement = connection.prepareStatement("SELECT title FROM book_titles")
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                titles << resultSet.getString(1)
            }
        }
        return titles
    }
}
