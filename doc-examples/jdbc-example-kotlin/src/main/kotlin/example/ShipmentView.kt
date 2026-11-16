package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonView
import io.micronaut.data.annotation.JsonViewField
import io.micronaut.data.annotation.JsonViewGenerated

// tag::view[]
@JsonView(
    value = "EU_SHIPMENT_VIEW",
    entity = Shipment::class,
    where = "@.country IN ('FR', 'DE', 'IT')", // <1>
    hidden = ["weight", "rate"] // <2>
)
data class ShipmentView(
    @field:Id
    @field:GeneratedValue(GeneratedValue.Type.IDENTITY)
    val id: Long?,
    val reference: String,
    val country: String,
    @field:JsonViewField(updatable = false) // <3>
    val trackingCode: String,
    @field:JsonViewGenerated(path = "\$.weight * \$.rate") // <4>
    val cost: Double?,
    @field:JsonViewGenerated(sql = "UPPER(@.reference)") // <5>
    val label: String?
)
// end::view[]
