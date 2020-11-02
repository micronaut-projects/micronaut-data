
package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

import java.util.Optional

@JdbcRepository(dialect = Dialect.H2)
interface SaleRepository : CrudRepository<Sale, Long> {

    @JoinSpecifications(value = [Join("product"), Join("product.manufacturer")])
    override fun findById(aLong: Long): Optional<Sale>
}
