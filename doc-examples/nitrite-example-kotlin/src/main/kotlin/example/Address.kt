package example

import io.micronaut.data.annotation.Embeddable

// tag::address[]
@Embeddable
data class Address(
    val street: String = "",
    val city: String = "",
    val zipCode: String = ""
)
// end::address[]
