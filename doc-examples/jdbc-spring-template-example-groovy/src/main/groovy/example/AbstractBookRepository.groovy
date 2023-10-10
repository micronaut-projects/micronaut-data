package example

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.jdbc.core.JdbcTemplate

import javax.sql.DataSource

@Requires(property = 'spec.name', value = 'BookRepositorySpec')
// tag::clazz[]
@JdbcRepository(dialect = Dialect.H2)
abstract class AbstractBookRepository implements CrudRepository<@Valid Book, @NotNull Long> {

    private final JdbcTemplate jdbcTemplate;

    AbstractBookRepository(DataSource dataSource) { // <1>
        this.jdbcTemplate = new JdbcTemplate(DelegatingDataSource.unwrapDataSource(dataSource)); //<2>
    }

    @Transactional
    List<Book> findByTitle(@NonNull @NotNull String title) {
        return jdbcTemplate.queryForList('SELECT * FROM Book AS book WHERE book.title = ?', title) // <3>
            .collect(m -> new Book(m.id as Long, m.title as String, m.pages as Integer))
    }
}
// end::clazz[]
