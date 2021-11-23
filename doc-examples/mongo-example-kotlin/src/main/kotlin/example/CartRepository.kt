package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId
import java.util.*

@MongoRepository
interface CartRepository : CrudRepository<Cart, ObjectId> {
    @Join("items")
    override fun findById(id: ObjectId): Optional<Cart>
}