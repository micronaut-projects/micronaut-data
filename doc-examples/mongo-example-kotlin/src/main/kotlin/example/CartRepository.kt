package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import org.bson.types.ObjectId
import java.util.*

@MongoRepository
interface CartRepository : KotlinCrudRepository<Cart, ObjectId> {
    @Join("items")
    override fun findById(id: ObjectId): Cart?
}
