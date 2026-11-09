package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity(value = "TBL_SHIPMENT", alias = "sh")
data class Shipment(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long?,
    val reference: String,
    val country: String,
    val weight: Double?,
    val rate: Double?,
    val trackingCode: String
)
