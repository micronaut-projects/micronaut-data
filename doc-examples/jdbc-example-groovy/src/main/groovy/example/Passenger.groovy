package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity

// tag::upsert-entity[]
@MappedEntity
@Index(columns = "email", unique = true)
class Passenger {

    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id

    final String email

    String firstName

    String lastName

    Passenger(String email, String firstName, String lastName) {
        this.email = email
        this.firstName = firstName
        this.lastName = lastName
    }
}
// end::upsert-entity[]
