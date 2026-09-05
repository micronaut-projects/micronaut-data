package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

import java.time.LocalDate

// tag::personWithDateOfBirth[]
@MappedEntity
class PersonWithDateOfBirth {
    @Id
    @GeneratedValue
    String id

    String name

    LocalDate dateOfBirth

    PersonWithDateOfBirth() {}

    PersonWithDateOfBirth(String name, LocalDate dateOfBirth) {
        this.name = name
        this.dateOfBirth = dateOfBirth
    }
}
// end::personWithDateOfBirth[]
