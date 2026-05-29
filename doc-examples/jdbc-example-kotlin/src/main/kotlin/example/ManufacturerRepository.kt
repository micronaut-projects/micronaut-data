package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface ManufacturerRepository : KotlinCrudRepository<Manufacturer, Long> {

    fun save(name: String): Manufacturer
}
