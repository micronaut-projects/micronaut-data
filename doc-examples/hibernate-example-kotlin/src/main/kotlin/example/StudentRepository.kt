package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Repository
import io.micronaut.data.annotation.Version
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

// tag::studentRepository[]
@Repository
interface StudentRepository : KotlinCrudRepository<Student, Long> {

    fun update(@Id id: Long, @Version version: Long, name: String)

    fun delete(@Id id: Long, @Version version: Long)

}
// end::studentRepository[]
