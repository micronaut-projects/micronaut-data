package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
data class Bar(
    @field:Id
    @GeneratedValue
    val id: Long? = null,

    val title: String? = null,
)
