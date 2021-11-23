package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId
import java.util.Optional

@MongoRepository
abstract class ParentRepository : CrudRepository<Parent, ObjectId> {

    @Join("children")
    abstract override fun findById(id: ObjectId): Optional<Parent>

}
