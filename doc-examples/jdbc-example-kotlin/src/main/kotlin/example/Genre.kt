package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
data class Genre(
    @field:Id
    val id: Long,
    val name: String
)
