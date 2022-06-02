package example

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import kotlinx.coroutines.flow.Flow
import javax.transaction.Transactional

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface Parent2Repository : CoroutineCrudRepository<Parent, Long> {

    @Transactional(Transactional.TxType.MANDATORY)
    override fun findAll(): Flow<Parent>

    @Transactional(Transactional.TxType.MANDATORY)
    override suspend fun findById(id: Long): Parent?
}