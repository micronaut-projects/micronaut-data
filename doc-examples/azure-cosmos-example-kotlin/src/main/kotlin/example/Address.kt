package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@Embeddable
class Address {
    var state: String? = null
    var county: String? = null
    var city: String? = null
}
