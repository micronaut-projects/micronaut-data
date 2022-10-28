
package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import javax.transaction.Transactional

@Repository
abstract class AbstractBookRepository(private val jdbcOperations: JdbcOperations) : KotlinCrudRepository<Book, Long> {

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
