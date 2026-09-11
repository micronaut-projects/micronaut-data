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
    var id: String? = null

    var name: String = ""

    var dateOfBirth: LocalDate? = null

    constructor()

    constructor(name: String, dateOfBirth: LocalDate) {
        this.name = name
        this.dateOfBirth = dateOfBirth
    }
}
// end::personWithDateOfBirth[]
