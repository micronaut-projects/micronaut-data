package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::address[]
@MappedEntity
data class Address(
    @field:Id
    @field:GeneratedValue
    val id: String? = null,
    val street: String = "",
    val city: String = "",
    val zipCode: String = ""
)
// end::address[]
