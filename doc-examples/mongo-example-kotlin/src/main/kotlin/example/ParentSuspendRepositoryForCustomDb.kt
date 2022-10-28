package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.GenericRepository
import org.bson.types.ObjectId
import java.util.*
import javax.transaction.Transactional

@MongoRepository(serverName = "custom")
interface ParentSuspendRepositoryForCustomDb : GenericRepository<Parent, ObjectId> {

    @Join(value = "children", type = Join.Type.FETCH)
    suspend fun findById(id: ObjectId): Parent?

    @Transactional(Transactional.TxType.MANDATORY)
    suspend fun queryById(id: ObjectId): Parent?

    suspend fun save(entity: Parent): Parent

    suspend fun update(entity: Parent): Parent

    suspend fun deleteAll()

}
