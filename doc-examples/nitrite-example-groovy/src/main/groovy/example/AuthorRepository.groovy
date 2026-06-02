package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

// tag::join-example[]
@NitriteRepository
interface AuthorRepository extends CrudRepository<Author, String> {

    // Fetch books when finding by ID
    @Join("books")
    Optional<Author> findById(String id)

    // Fetch books when searching by name
    @Join("books")
    Author searchByName(String name)

    // Fetch all authors with their books eagerly loaded
    @Join("books")
    List<Author> findAll()

    // tag::reverse-lookup[]
    // Find authors by their book's title (reverse lookup on ONE_TO_MANY)
    Author findByBooksTitle(String title)
    // end::reverse-lookup[]
}
// end::join-example[]
