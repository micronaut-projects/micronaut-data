package example

import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import org.bson.types.ObjectId

@MongoRepository
interface ManufacturerRepository : KotlinCrudRepository<Manufacturer, ObjectId> {

    fun save(name: String): Manufacturer
}
