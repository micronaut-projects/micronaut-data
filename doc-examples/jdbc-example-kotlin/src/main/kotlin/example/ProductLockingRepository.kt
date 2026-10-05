package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

// tag::forUpdate[]
@JdbcRepository(dialect = Dialect.H2)
interface ProductLockingRepository : KotlinCrudRepository<Product, Long> {

    @Join("manufacturer")
    fun findByIdForUpdate(id: Long): Product?

    @Join("manufacturer")
    fun findAllOrderByNameForUpdate(): List<Product>

    @Join("manufacturer")
    fun findByNameForUpdate(name: String): List<Product>
}
// end::forUpdate[]
