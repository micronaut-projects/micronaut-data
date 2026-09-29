package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity

// tag::upsert-entity[]
@MappedEntity
@Index(columns = ["email"], unique = true)
data class Passenger(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    var id: Long? = null,
    val email: String,
    var firstName: String,
    var lastName: String
)
// end::upsert-entity[]
