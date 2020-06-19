package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface ManufacturerRepository : CrudRepository<Manufacturer, Long> {

    fun save(name: String): Manufacturer
}