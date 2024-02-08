package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

@Requires(property = "spec.name", value = "BookRepositoryTest")
// tag::clazz[]
@JdbcRepository(dialect = Dialect.H2)
public abstract class AbstractBookRepository implements CrudRepository<@Valid Book, @NotNull Long> {

    private final JdbcTemplate jdbcTemplate;

    public AbstractBookRepository(DataSource dataSource) { // <1>
        this.jdbcTemplate = new JdbcTemplate(DelegatingDataSource.unwrapDataSource(dataSource)); //<2>
    }

    @Transactional
    public List<Book> findByTitle(@NonNull @NotNull String title) {
        return jdbcTemplate.queryForList("SELECT * FROM Book AS book WHERE book.title = ?", title) // <3>
            .stream()
            .map(m -> new Book((Long) m.get("id"), (String) m.get("title"), (Integer) m.get("pages")))
            .toList();
    }
}
// end::clazz[]
