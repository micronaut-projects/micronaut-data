package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity(value = "TBL_SHIPMENT", alias = "sh")
class Shipment {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id
    String reference
    String country
    @Nullable
    Double weight
    @Nullable
    Double rate
    String trackingCode
}
