package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::customer[]
@MappedEntity
class Customer {
    @Id
    @GeneratedValue
    var id: String? = null

    var name: String? = null
    var address: Address? = null

    constructor(name: String, address: Address) {
        this.name = name
        this.address = address
    }
    // end::customer[]

    constructor()
// tag::customer[]
}
// end::customer[]
