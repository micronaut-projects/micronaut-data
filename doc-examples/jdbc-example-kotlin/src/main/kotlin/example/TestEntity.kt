package example

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import java.time.Instant
import java.util.*

@MappedEntity("test_table")
@Introspected
data class TestEntity(
    @field:Id
    val id: UUID,
    val destination: String,
    val scheduledTime: Instant,
    val payload: String,
    val attempts: Int,
    @DateCreated
    val created: Instant?
)
