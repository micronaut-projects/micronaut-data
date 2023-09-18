package example

import io.micronaut.context.annotation.Requires
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import javax.sql.DataSource
import org.springframework.jdbc.core.JdbcTemplate

@Requires(property = "spec.name", value = "BookRepositoryTest") // tag::clazz[]
// tag::clazz[]
@JdbcRepository(dialect = Dialect.H2)
abstract class AbstractBookRepository(val jdbcTemplate: JdbcTemplate) : CrudRepository<@Valid Book, Long> {  //<1>

    @Transactional
    open fun findByTitle(title: String) = jdbcTemplate
        .queryForList("SELECT * FROM Book AS book WHERE book.title = ?", title) // <2>
        .map { m -> Book(m["id"] as Long, m["title"] as String, m["pages"] as Int) }
}
// end::clazz[]
