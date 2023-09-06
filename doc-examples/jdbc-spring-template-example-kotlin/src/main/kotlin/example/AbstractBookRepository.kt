package example

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import jakarta.transaction.Transactional
import jakarta.validation.constraints.NotNull
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Requires(property = "spec.name", value = "BookRepositoryTest") // tag::clazz[]
@JdbcRepository(dialect = Dialect.H2)
abstract class AbstractBookRepository(dataSource: DataSource) : CrudRepository<Book, Long?> { // <1>

    private val jdbcTemplate : JdbcTemplate

    init {
        jdbcTemplate = JdbcTemplate(DelegatingDataSource.unwrapDataSource(dataSource)) //<2>
    }

    @Transactional
    fun findByTitle(title: @NonNull @NotNull String?): List<Book> {
        return jdbcTemplate.queryForList("SELECT * FROM Book AS book WHERE book.title = ?", title) // <3>
            .stream()
            .map { m: Map<String?, Any?> -> Book(m["id"] as Long?, (m["title"] as String?)!!, (m["pages"] as Int?)!!)
            }
            .toList()
    }
}
