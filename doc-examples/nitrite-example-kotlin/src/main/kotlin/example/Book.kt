package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::book[]
@MappedEntity
data class Book(
    @field:Id
    @field:GeneratedValue
    var id: String? = null,
    var title: String
)
// end::book[]

