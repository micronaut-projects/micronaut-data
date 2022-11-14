package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@Embeddable
data class Address(
    var state: String,
    var county: String,
    var city: String
)
