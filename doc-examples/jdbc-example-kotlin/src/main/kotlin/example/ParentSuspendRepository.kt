package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.GenericRepository
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.joinMany
import io.micronaut.data.runtime.criteria.query
import jakarta.persistence.criteria.JoinType
import java.util.*
import jakarta.transaction.Transactional
import kotlinx.coroutines.selects.select

@JdbcRepository(dialect = Dialect.H2)
interface ParentSuspendRepository : GenericRepository<Parent, Int>, CoroutineJpaSpecificationExecutor<Parent> {

    @Join(value = "children", type = Join.Type.FETCH)
    suspend fun findById(id: Int): Optional<Parent>

    @Transactional(Transactional.TxType.MANDATORY)
    suspend fun queryById(id: Int): Optional<Parent>

    suspend fun save(@NonNull entity: Parent): Parent

    suspend fun update(@NonNull entity: Parent): Parent

    object Specifications {
        fun childNameInList(names: List<String>) = query<Parent> {
            query.distinct(true)
            val children = root.joinMany(Parent::children, JoinType.INNER)
            where {
                children[Child::name] inList names
            }
        }
    }
}
