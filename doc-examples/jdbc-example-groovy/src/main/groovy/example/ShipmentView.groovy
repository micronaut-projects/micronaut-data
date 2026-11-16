package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonView
import io.micronaut.data.annotation.JsonViewField
import io.micronaut.data.annotation.JsonViewGenerated

// tag::view[]
@JsonView(
    value = "EU_SHIPMENT_VIEW",
    entity = Shipment,
    where = "@.country IN ('FR', 'DE', 'IT')", // <1>
    hidden = ["weight", "rate"] // <2>
)
class ShipmentView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id
    String reference
    String country
    @JsonViewField(updatable = false) // <3>
    String trackingCode
    @Nullable
    @JsonViewGenerated(path = '$.weight * $.rate') // <4>
    Double cost
    @Nullable
    @JsonViewGenerated(sql = "UPPER(@.reference)") // <5>
    String label
}
// end::view[]
