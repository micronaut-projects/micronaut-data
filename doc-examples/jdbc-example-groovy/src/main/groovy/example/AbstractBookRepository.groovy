
package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

import javax.transaction.Transactional
import java.sql.ResultSet
import java.util.List
import java.util.stream.Collectors

@JdbcRepository(dialect = Dialect.H2)
abstract class AbstractBookRepository implements CrudRepository<Book, Long> {

    private final JdbcOperations jdbcOperations

    AbstractBookRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations
    }

    @Transactional
    List<Book> findByTitle(String title) {
        String sql = "SELECT * FROM Book AS book WHERE book.title = ?"
        return jdbcOperations.prepareStatement(sql,  { statement ->
            statement.setString(1, title)
            ResultSet resultSet = statement.executeQuery()
            return jdbcOperations.entityStream(resultSet, Book.class)
                    .collect(Collectors.toList())
        })
    }
}
