package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import java.util.*

@JdbcRepository(dialect = Dialect.H2)
interface CartRepository : KotlinCrudRepository<Cart, Long> {
    @Join("items")
    override fun findById(id: Long): Cart?
}
