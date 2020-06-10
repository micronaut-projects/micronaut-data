
package example

import io.micronaut.data.annotation.*
import io.micronaut.data.jpa.annotation.EntityGraph
import io.micronaut.data.repository.CrudRepository
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.CompletableFuture
// tag::join[]
// tag::async[]
@Repository
interface ProductRepository : CrudRepository<Product, Long> {
// end::join[]
// end::async[]

    // tag::join[]
    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    fun list(): List<Product>
    // end::join[]

    // tag::entitygraph[]
    @EntityGraph(attributePaths = ["manufacturer", "title"]) // <1>
    override fun findAll(): List<Product>
    // end::entitygraph[]

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
// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
