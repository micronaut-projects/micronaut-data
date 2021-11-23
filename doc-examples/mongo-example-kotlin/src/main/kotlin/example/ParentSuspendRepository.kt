package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.repository.GenericRepository
import org.bson.types.ObjectId
import java.util.*

@MongoRepository
interface ParentSuspendRepository : GenericRepository<Parent, ObjectId> {

    @Join("children")
    suspend fun findById(id: ObjectId): Optional<Parent>

    suspend fun save(@NonNull entity: Parent): Parent

    suspend fun update(@NonNull entity: Parent): Parent

}
