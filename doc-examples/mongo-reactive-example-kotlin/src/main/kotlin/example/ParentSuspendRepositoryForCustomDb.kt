package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.GenericRepository
import org.bson.types.ObjectId
import java.util.*
import javax.transaction.Transactional

@MongoRepository("custom")
interface ParentSuspendRepositoryForCustomDb : GenericRepository<Parent, ObjectId> {

    @Join(value = "children", type = Join.Type.FETCH)
    suspend fun findById(id: ObjectId): Optional<Parent>

    @Transactional(Transactional.TxType.MANDATORY)
    suspend fun queryById(id: ObjectId): Optional<Parent>

    suspend fun save(@NonNull entity: Parent): Parent

    suspend fun update(@NonNull entity: Parent): Parent

    suspend fun deleteAll()
}
