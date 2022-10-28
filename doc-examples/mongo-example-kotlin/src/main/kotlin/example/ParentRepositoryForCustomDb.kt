package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import org.bson.types.ObjectId

@MongoRepository(serverName = "custom")
abstract class ParentRepositoryForCustomDb : KotlinCrudRepository<Parent, ObjectId> {

    @Join(value = "children", type = Join.Type.FETCH)
    abstract override fun findById(id: ObjectId): Parent?

}
