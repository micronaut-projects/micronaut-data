// tag::join[]
// tag::async[]
package example

import io.micronaut.data.annotation.*
import io.micronaut.data.repository.CrudRepository
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.CompletableFuture

@Repository
interface ProductRepository : CrudRepository<Product, Long> {
// end::join[]
// end::async[]

    // tag::join[]
    fun save(manufacturer: Manufacturer) : Manufacturer

    @Join("manufacturer") // <1>
    fun list(): List<Product>
    // end::join[]

    // tag::async[]
    fun findByNameContains(str: String): CompletableFuture<Product>

    fun countByManufacturerName(name: String): CompletableFuture<Long>
    // end::async[]

    // tag::reactive[]
    fun queryByNameContains(str: String): Maybe<Product>

    fun countDistinctByManufacturerName(name: String): Single<Long>
    // end::reactive[]
// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
