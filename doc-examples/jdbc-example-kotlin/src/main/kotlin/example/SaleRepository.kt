
package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface SaleRepository : KotlinCrudRepository<Sale, Long> {

    @JoinSpecifications(value = [Join("product"), Join("product.manufacturer")])
    override fun findById(id: Long): Sale?
}
