package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.types.ObjectId
import java.util.*

@MongoRepository(serverName = "custom")
abstract class ParentRepositoryForCustomDb : CrudRepository<Parent, ObjectId> {

    @Join(value = "children", type = Join.Type.FETCH)
    abstract override fun findById(id: ObjectId): Optional<Parent>

}
