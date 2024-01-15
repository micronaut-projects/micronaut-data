
package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.joinOne
import io.micronaut.data.runtime.criteria.where
import io.reactivex.Maybe
import io.reactivex.Single
import org.bson.types.ObjectId
import java.util.concurrent.CompletableFuture

// tag::join[]
// tag::async[]
@MongoRepository
interface ProductRepository : CrudRepository<Product, ObjectId>, JpaSpecificationExecutor<Product> {
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
    fun queryByNameRegex(str: String): Maybe<Product>

    fun countDistinctByManufacturerName(name: String?): Single<Long>
    // end::reactive[]

    // tag::specifications[]
    object Specifications {

        fun manufacturerNameEquals(name: String?) = where<Product> {
            val manufacturer = root.joinOne(Product::manufacturer)
            manufacturer[Manufacturer::name] eq name
        }

        fun nameInList(names: List<String>) = where<Product> {
            val manufacturer = root.joinOne(Product::manufacturer)
            or {
                root[Product::name] inList names
                manufacturer[Manufacturer::name] inList names
            }
        }

        // tag::specifications[]
    }

// tag::join[]
// tag::async[]
}

// end::join[]
// end::async[]
