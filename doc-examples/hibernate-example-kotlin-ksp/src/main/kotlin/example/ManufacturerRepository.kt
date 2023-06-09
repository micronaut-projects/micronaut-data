package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

@Repository
interface ManufacturerRepository : CrudRepository<Manufacturer, Long> {

    fun save(name: String): Manufacturer
}