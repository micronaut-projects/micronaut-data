
package example;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import jakarta.transaction.Transactional;
import java.sql.ResultSet;
import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
public abstract class AbstractBookRepository implements CrudRepository<Book, Long> {

    private final JdbcOperations jdbcOperations;

    public AbstractBookRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    /**
     * Finds by title.
     *
     * @param title The title
     * @return List of books with matching title
     */
    @Transactional
    public List<Book> findByTitle(String title) {
        String sql = "SELECT * FROM Book book WHERE book.title = ?";
        return jdbcOperations.prepareStatement(sql, statement -> {
            statement.setString(1, title);
            ResultSet resultSet = statement.executeQuery();
            return jdbcOperations.entityStream(resultSet, Book.class).toList();
        });
    }
}
