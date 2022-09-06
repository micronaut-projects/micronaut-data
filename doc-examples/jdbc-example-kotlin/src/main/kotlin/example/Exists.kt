package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Mono
import java.util.*

@MappedEntity
data class Record(
    @field:Id val id: UUID,
    val foo: UUID,
    val bar: UUID
)

@JdbcRepository(dialect = Dialect.H2)
interface RecordCoroutineRepository : CoroutineCrudRepository<Record, UUID> {
    suspend fun existsByFoo(foo: UUID): Boolean?
    suspend fun existsByBar(bar: UUID): Boolean
}

@JdbcRepository(dialect = Dialect.H2)
interface RecordReactiveRepository : ReactorCrudRepository<Record, UUID> {
    fun existsByFoo(foo: UUID): Mono<Boolean?>
    fun existsByBar(bar: UUID): Mono<Boolean>
}
