
package example.oracle;

import example.Book;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import jakarta.transaction.Transactional;

import java.sql.ResultSet;
import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
@Requires(env="oracle")
public abstract class AbstractBookRepository extends example.AbstractBookRepository {

    public AbstractBookRepository(JdbcOperations jdbcOperations) {
        super(jdbcOperations);
    }

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
