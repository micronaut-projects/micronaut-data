package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.GenericRepository
import java.util.*
import javax.transaction.Transactional

@Repository("custom")
interface ParentSuspendRepositoryForCustomDb : GenericRepository<Parent, Int> {

    @Join(value = "children", type = Join.Type.FETCH)
    suspend fun findById(id: Int): Parent?

    @Transactional(Transactional.TxType.MANDATORY)
    suspend fun queryById(id: Int): Parent?

    suspend fun save(entity: Parent): Parent

    suspend fun update(entity: Parent): Parent

}
