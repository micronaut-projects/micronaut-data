package example;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import javax.transaction.Transactional;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Collectors;

@JdbcRepository(dialect = Dialect.H2)
public abstract class AbstractBookRepository implements CrudRepository<Book, Long> {

    private final JdbcOperations jdbcOperations;

    public AbstractBookRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Transactional
    public List<Book> findByTitle(String title) {
        return jdbcOperations.execute(connection -> {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    "SELECT * FROM Book AS book WHERE book.title = ?"
            );
            preparedStatement.setString(1, title);
            ResultSet resultSet = preparedStatement.executeQuery();

            return jdbcOperations.resultStream(resultSet, Book.class).collect(Collectors.toList());
        });
    }
}
