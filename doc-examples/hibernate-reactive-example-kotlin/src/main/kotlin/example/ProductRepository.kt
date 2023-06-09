package example

import io.micronaut.data.annotation.*
import io.micronaut.data.jpa.annotation.EntityGraph
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import kotlinx.coroutines.flow.Flow

// tag::join[]
// tag::async[]
@Repository
interface ProductRepository : CoroutineCrudRepository<Product, Long> {
// end::join[]
// end::async[]

    // tag::join[]
    @Join(value = "manufacturer", type = Join.Type.FETCH) // <1>
    suspend fun list(): List<Product>
    // end::join[]

    // tag::entitygraph[]
    @EntityGraph(attributePaths = ["manufacturer", "title"]) // <1>
    override fun findAll(): Flow<Product>
    // end::entitygraph[]


// tag::join[]
// tag::async[]
}
// end::join[]
// end::async[]
