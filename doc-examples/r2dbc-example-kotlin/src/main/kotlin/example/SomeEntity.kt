package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
data class SomeEntity(
    @field:GeneratedValue
    @field:Id
    val id: Long? = null,
    val something: Int? = null,
)