package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::book[]
@MappedEntity
data class Book(
    @field:Id @field:GeneratedValue val id: Long?,
    val title: String,
    val pages: Int
)
// end::book[]
