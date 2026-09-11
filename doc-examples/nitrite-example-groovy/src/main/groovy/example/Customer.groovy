package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::customer[]
@MappedEntity
class Customer {
    @Id
    @GeneratedValue
    String id

    String name
    Address address

    Customer(String name, Address address) {
        this.name = name
        this.address = address
    }
    // end::customer[]

    Customer() {}
// tag::customer[]
}
// end::customer[]
