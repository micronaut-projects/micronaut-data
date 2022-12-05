package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import org.bson.types.ObjectId

@MongoRepository
abstract class ParentRepository : KotlinCrudRepository<Parent, ObjectId> {

    @Join("children")
    abstract override fun findById(id: ObjectId): Parent?

}
