package example

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.runtime.schema.SchemaContext
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

    @Inject
    BookTitlesViewContributor contributor

    void "the contributed view is created over the book table"() {
        when:
        bookRepository.save(new Book("The Left Hand of Darkness", 300))
        List<String> titles = transactionOperations.executeRead { status -> selectTitles(status.connection) }

        then:
        titles.contains("The Left Hand of Darkness")
    }

    void "the contributor does not apply to a data source without the book entity"() {
        given:
        SchemaContext other = new SchemaContext("other", Dialect.H2, null, SchemaGenerate.CREATE_DROP, [])

        expect:
        contributor.buildCreateStatements(other, []).isEmpty()
        contributor.buildDropStatements(other, []).isEmpty()
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
