// tag::repository[]
package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.*
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

    // tag::pageable[]
    fun findByPagesGreaterThan(pageCount: Int, pageable: Pageable): List<Book>

    fun findByTitleLike(title: String, pageable: Pageable): Page<Book>

    fun list(pageable: Pageable): Slice<Book>
    // end::pageable[]

    // tag::simple-projection[]
    fun findTitleByPagesGreaterThan(pageCount: Int): List<String>
    // end::simple-projection[]

    // tag::top-projection[]
    fun findTop3ByTitleLike(title: String): List<String>
    // end::top-projection[]

// tag::repository[]
}
// end::repository[]
