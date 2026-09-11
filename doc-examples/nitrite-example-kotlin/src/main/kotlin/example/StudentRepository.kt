package example

import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor

// tag::studentRepository[]
@NitriteRepository
interface StudentRepository : CrudRepository<Student, String>, JpaSpecificationExecutor<Student> {

    // tag::studentRepository-findByName[]
    fun findByName(name: String): Student?
    // end::studentRepository-findByName[]
}
// end::studentRepository[]
