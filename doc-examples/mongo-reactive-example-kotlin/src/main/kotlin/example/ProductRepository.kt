
package example

import io.micronaut.data.annotation.*
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture
// tag::join[]
// tag::async[]
@MongoRepository
interface ProductRepository : CrudRepository<Product, ObjectId> {
// end::join[]
// end::async[]

    // tag::join[]
    @Join("manufacturer") // <1>
    fun list(): List<Product>
    // end::join[]

    // tag::async[]
    @Join("manufacturer")
    fun findByNameRegex(str: String): CompletableFuture<Product>

    fun countByManufacturerName(name: String?): CompletableFuture<Long>
    // end::async[]

    // tag::reactive[]
    @Join("manufacturer")
    fun queryByNameRegex(str: String): Mono<Product>

    fun countDistinctByManufacturerName(name: String?): Mono<Long>
    // end::reactive[]

// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
