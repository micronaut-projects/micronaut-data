package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.repository.CrudRepository

import javax.transaction.Transactional
import kotlin.streams.toList

@Repository
abstract class AbstractBookRepository(private val jdbcOperations: JdbcOperations) : CrudRepository<Book, Long> {

    @Transactional
    fun findByTitle(title: String): List<Book> {
        return jdbcOperations.execute { connection ->
            val preparedStatement = connection.prepareStatement(
                    "SELECT * FROM Book AS book WHERE book.title = ?"
            )
            preparedStatement.setString(1, title)
            val resultSet = preparedStatement.executeQuery()

            jdbcOperations.resultStream(resultSet, Book::class.java)
                    .toList()
        }
    }
}
