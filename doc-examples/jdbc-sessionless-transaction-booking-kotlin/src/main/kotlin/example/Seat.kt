package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
data class Seat(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.SEQUENCE)
    val id: Long? = null,
    val flightId: String,
    val seatId: String,
    val customerId: String,
    val status: String? = null
)
