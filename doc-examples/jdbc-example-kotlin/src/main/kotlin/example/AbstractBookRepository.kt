
package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.repository.CrudRepository
import java.util.stream.Collectors

import javax.transaction.Transactional
import kotlin.streams.toList

@Repository
abstract class AbstractBookRepository(private val jdbcOperations: JdbcOperations) : CrudRepository<Book, Long> {

    @Transactional
    fun findByTitle(title: String): List<Book> {
        val sql = "SELECT * FROM Book AS book WHERE book.title = ?"
        return jdbcOperations.prepareStatement(sql) { statement ->
            statement.setString(1, title)
            val resultSet = statement.executeQuery()
            jdbcOperations.entityStream(resultSet, Book::class.java)
                    .toList()
        }
    }
}
