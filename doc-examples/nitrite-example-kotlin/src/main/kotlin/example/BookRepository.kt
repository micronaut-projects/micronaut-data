package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

// tag::repository[]
@NitriteRepository
interface BookRepository : CrudRepository<Book, String> {
// end::repository[]

    fun findByTitle(title: String): Optional<Book>

    // tag::update[]
    fun update(@Id id: String, title: String)
    // end::update[]

// tag::repository[]
}
// end::repository[]

