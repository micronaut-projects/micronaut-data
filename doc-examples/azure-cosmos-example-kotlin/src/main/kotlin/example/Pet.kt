package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class Pet {
    var givenName: String? = null
    var type: String? = null
}
