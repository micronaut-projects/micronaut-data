
package example

import io.micronaut.data.annotation.*
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.reactivex.Maybe
import io.reactivex.Single
import org.bson.types.ObjectId
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
    fun queryByNameRegex(str: String): Maybe<Product>

    fun countDistinctByManufacturerName(name: String?): Single<Long>
    // end::reactive[]

    // tag::native[]
    @Query("""SELECT *, m_.name as m_name, m_.id as m_id 
                    FROM product p 
                    INNER JOIN manufacturer m_ ON p.manufacturer_id = m_.id 
                    WHERE p.name like :name limit 5""")
    @Join(value = "manufacturer", alias = "m_")
    fun searchProducts(name: String): List<Product>
    // end::native[]
// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
