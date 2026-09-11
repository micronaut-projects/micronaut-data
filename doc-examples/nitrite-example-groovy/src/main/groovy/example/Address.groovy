package example

import io.micronaut.data.annotation.Embeddable

// tag::address[]
@Embeddable
class Address {
    String street
    String city
    String zipCode

    Address(String street, String city, String zipCode) {
        this.street = street
        this.city = city
        this.zipCode = zipCode
    }
    // end::address[]

    Address() {}
// tag::address[]
}
// end::address[]
