package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Connection

@MicronautTest(transactional = false)
class BookTitlesViewTest {

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var transactionOperations: TransactionOperations<Connection>

    @Test
    fun theContributedViewIsCreatedOverTheBookTable() {
        bookRepository.save(Book(0, "The Left Hand of Darkness", 300))

        val titles = transactionOperations.executeRead { status ->
            status.connection.prepareStatement("SELECT title FROM book_titles").use { ps ->
                ps.executeQuery().use { rs ->
                    val result = mutableListOf<String>()
                    while (rs.next()) {
                        result.add(rs.getString(1))
                    }
                    result
                }
            }
        }

        assertTrue(titles.contains("The Left Hand of Darkness"))
    }
}
