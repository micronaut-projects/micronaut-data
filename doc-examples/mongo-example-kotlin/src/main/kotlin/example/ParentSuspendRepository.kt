package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.GenericRepository
import org.bson.types.ObjectId
import java.util.*
import javax.transaction.Transactional

@MongoRepository
interface ParentSuspendRepository : GenericRepository<Parent, ObjectId> {

    @Join("children")
    suspend fun findById(id: ObjectId): Parent?

    suspend fun save(entity: Parent): Parent

    suspend fun update(entity: Parent): Parent

    @Transactional(Transactional.TxType.MANDATORY)
    suspend fun queryById(id: ObjectId): Parent?
}
