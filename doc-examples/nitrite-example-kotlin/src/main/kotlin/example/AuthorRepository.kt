package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

// tag::join-example[]
@NitriteRepository
interface AuthorRepository : CrudRepository<Author, String> {

    // Fetch books when finding by ID
    @Join("books")
    override fun findById(id: String): Optional<Author>

    // Fetch books when searching by name
    @Join("books")
    fun searchByName(name: String): Author?

    // tag::reverse-lookup[]
    // Find authors by their book's title (reverse lookup on ONE_TO_MANY)
    fun findByBooksTitle(title: String): Author?
    // end::reverse-lookup[]
}
// end::join-example[]
