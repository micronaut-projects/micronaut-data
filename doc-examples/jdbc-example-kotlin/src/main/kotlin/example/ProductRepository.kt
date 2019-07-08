
package example

import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.CompletableFuture
// tag::join[]
// tag::async[]
@JdbcRepository(dialect = Dialect.H2)
interface ProductRepository : CrudRepository<Product, Long> {
// end::join[]
// end::async[]

    // tag::join[]
    fun save(manufacturer: Manufacturer) : Manufacturer

    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    fun list(): List<Product>
    // end::join[]

    // tag::async[]
    @Join("manufacturer")
    fun findByNameContains(str: String): CompletableFuture<Product>

    fun countByManufacturerName(name: String): CompletableFuture<Long>
    // end::async[]

    // tag::reactive[]
    @Join("manufacturer")
    fun queryByNameContains(str: String): Maybe<Product>

    fun countDistinctByManufacturerName(name: String): Single<Long>
    // end::reactive[]
// tag::join[]O
// tag::async[]
}
// end::join[]
// end::async[]
