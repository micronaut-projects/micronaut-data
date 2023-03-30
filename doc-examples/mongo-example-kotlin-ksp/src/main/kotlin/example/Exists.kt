package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
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

@MongoRepository
interface RecordCoroutineRepository : CoroutineCrudRepository<Record, UUID> {
    suspend fun existsByFoo(foo: UUID): Boolean?
    suspend fun existsByBar(bar: UUID): Boolean
}

@MongoRepository
interface RecordReactiveRepository : ReactorCrudRepository<Record, UUID> {
    fun existsByFoo(foo: UUID): Mono<Boolean?>
    fun existsByBar(bar: UUID): Mono<Boolean>
}
