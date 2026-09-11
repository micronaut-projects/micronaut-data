package example

import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor

// tag::personWithDateOfBirthRepository[]
@NitriteRepository
interface PersonWithDateOfBirthRepository : CrudRepository<PersonWithDateOfBirth, String>, JpaSpecificationExecutor<PersonWithDateOfBirth> {

    // tag::personWithDateOfBirthRepository-countByName[]
    fun countByName(name: String): Long
    // end::personWithDateOfBirthRepository-countByName[]
}
// end::personWithDateOfBirthRepository[]
