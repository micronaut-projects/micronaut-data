package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Reservable
import jakarta.validation.constraints.PositiveOrZero

// tag::reservable[]
@MappedEntity("account")
data class Account(
    @field:Id @field:GeneratedValue val id: Long?,
    val name: String,
    @field:Reservable @field:PositiveOrZero val balance: Long
)
// end::reservable[]
