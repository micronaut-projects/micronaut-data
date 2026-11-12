package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository
import jakarta.inject.Singleton

// tag::singleton[]
@Singleton
@Repository
interface SingletonManufacturerRepository extends CrudRepository<Manufacturer, Long> {
}
// end::singleton[]
