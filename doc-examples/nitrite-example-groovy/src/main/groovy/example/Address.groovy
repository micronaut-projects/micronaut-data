package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::address[]
@MappedEntity
class Address {
    @Id
    @GeneratedValue
    String id

    String street
    String city
    String zipCode

    Address() {}

    Address(String street, String city, String zipCode) {
        this.street = street
        this.city = city
        this.zipCode = zipCode
    }
}
// end::address[]
