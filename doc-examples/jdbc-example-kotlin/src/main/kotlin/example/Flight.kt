package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::upsert-entity[]
@MappedEntity
data class Flight(
    @field:Id
    val number: String,
    var origin: String,
    var destination: String
)
// end::upsert-entity[]
