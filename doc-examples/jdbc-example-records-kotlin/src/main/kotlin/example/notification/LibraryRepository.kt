package example.notification

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.ORACLE)
interface LibraryRepository : CrudRepository<Library, Long> {

    fun findByCapacityGreaterThanEquals(bookCount: Int): List<Library>
}
