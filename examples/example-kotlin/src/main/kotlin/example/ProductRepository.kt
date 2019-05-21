package example

import io.micronaut.data.annotation.*
import io.micronaut.data.repository.CrudRepository

@Repository
interface ProductRepository : CrudRepository<Product, Long> {

    fun save(manufacturer: Manufacturer) : Manufacturer

    @JoinSpec("manufacturer") // <1>
    fun list(): List<Product>
}
