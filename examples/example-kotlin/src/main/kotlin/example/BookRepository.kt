// tag::repository[]
package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

@Repository // <1>
interface BookRepository : CrudRepository<Book, Long> { // <2>
// end::repository[]

    // tag::simple[]
    fun findByTitle(title: String): Book

    fun getByTitle(title: String): Book

    fun retrieveByTitle(title: String): Book
    // end::simple[]

    // tag::greaterthan[]
    fun findByPagesGreaterThan(pageCount: Int): List<Book>
    // end::greaterthan[]

// tag::repository[]
}
// end::repository[]
