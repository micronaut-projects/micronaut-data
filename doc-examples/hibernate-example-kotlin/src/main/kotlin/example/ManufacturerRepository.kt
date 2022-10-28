package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

@Repository
interface ManufacturerRepository : KotlinCrudRepository<Manufacturer, Long> {

    fun save(name: String): Manufacturer
}
