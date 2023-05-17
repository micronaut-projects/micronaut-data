package example

import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId

@MongoRepository
interface ManufacturerRepository : CrudRepository<Manufacturer, ObjectId> {

    fun save(name: String): Manufacturer
}