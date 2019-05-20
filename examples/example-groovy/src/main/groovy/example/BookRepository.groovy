// tag::repository[]
package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

@Repository // <1>
interface BookRepository extends CrudRepository<Book, Long> { // <2>
// end::repository[]

    // tag::simple[]
    Book findByTitle(String title)

    Book getByTitle(String title)

    Book retrieveByTitle(String title)
    // end::simple[]

    // tag::greaterthan[]
    List<Book> findByPagesGreaterThan(int pageCount)
    // end::greaterthan[]

// tag::repository[]
}
// end::repository[]
