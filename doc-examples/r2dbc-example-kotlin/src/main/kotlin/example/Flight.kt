package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
data class Flight(
    @field:Id
    val number: String,
    var origin: String,
    var destination: String
)
