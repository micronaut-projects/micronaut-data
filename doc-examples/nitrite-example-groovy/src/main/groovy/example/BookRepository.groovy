package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

// tag::repository[]
@NitriteRepository
interface BookRepository extends CrudRepository<Book, String> {
// end::repository[]

    Optional<Book> findByTitle(String title)

    // tag::update[]
    void update(@Id String id, String title)
    // end::update[]

    // tag::findByAuthorName[]
    List<Book> findByAuthorName(String authorName)
    // end::findByAuthorName[]

    // tag::case-insensitive[]
    // Case-insensitive search by title
    List<Book> findByTitleIgnoreCase(String title)

    // Case-insensitive contains
    List<Book> findByTitleContainsIgnoreCase(String keyword)
    // end::case-insensitive[]

    // tag::in-queries[]
    // IN query with List parameter
    List<Book> findByTitleIn(List<String> titles)

    // IN query with array parameter
    List<Book> findByTitleIn(String[] titles)

    // NOT IN query
    List<Book> findByTitleNotIn(List<String> titles)
    // end::in-queries[]

    // tag::projections[]
    // Single-field projection - returns List<String>
    List<String> findDistinctTitle()

    // Count query - returns long
    long countByAuthorId(String authorId)

    // Aggregate functions
    Long countByTitle(String title)
    // end::projections[]

// tag::repository[]
}
// end::repository[]

