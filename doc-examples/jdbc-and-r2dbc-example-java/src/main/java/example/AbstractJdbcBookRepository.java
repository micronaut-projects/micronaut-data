
package example;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.transaction.annotation.TransactionalAdvice;

import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Collectors;

@JdbcRepository(dialect = Dialect.H2, dataSource = "jdbc")
public abstract class AbstractJdbcBookRepository implements CrudRepository<Book, Long> {

    private final JdbcOperations jdbcOperations;

    public AbstractJdbcBookRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @TransactionalAdvice("jdbc")
    public List<Book> findByTitle(String title) {
        String sql = "SELECT * FROM Book AS book WHERE book.title = ?";
        return jdbcOperations.prepareStatement(sql, statement -> {
            statement.setString(1, title);
            ResultSet resultSet = statement.executeQuery();
            return jdbcOperations.entityStream(resultSet, Book.class).collect(Collectors.toList());
        });
    }
}
