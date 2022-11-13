
// tag::repository[]
package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.cosmos.annotation.CosmosRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.repository.CrudRepository

@CosmosRepository // <1>
interface BookRepository : CrudRepository<Book, String> { // <2>
// end::repository[]

    // tag::simple[]
    fun findByTitle(title: String): Book

    fun getByTitle(title: String): Book

    fun retrieveByTitle(title: String): Book
    // end::simple[]

    // tag::simple-alt[]
    // tag::repository[]
    fun find(title: String): Book
    // end::simple-alt[]
    // end::repository[]

    // tag::greaterthan[]
    fun findByPagesGreaterThan(pageCount: Int): List<Book>
    // end::greaterthan[]

    // tag::pageable[]
    fun findByPagesGreaterThan(pageCount: Int, pageable: Pageable): List<Book>

    fun list(pageable: Pageable): Slice<Book>
    // end::pageable[]

    // tag::simple-projection[]
    fun findTitleByPagesGreaterThan(pageCount: Int): List<String>
    // end::simple-projection[]

    // tag::ordering[]
    fun listOrderByTitle(): List<Book>

    fun listOrderByTitleDesc(): List<Book>
    // end::ordering[]

    // tag::save[]
    fun persist(entity: Book): Book
    // end::save[]

    // tag::save2[]
    fun persist(title: String, pages: Int): Book
    // end::save2[]

    // tag::update[]
    fun update(@Id id: String, pages: Int)

    fun update(@Id id: String, title: String)
    // end::update[]

    // tag::update2[]
    fun updateByTitle(title: String, pages: Int)
    // end::update2[]

    // tag::deleteall[]
    override fun deleteAll()
    // end::deleteall[]

    // tag::deleteone[]
    fun delete(title: String)
    // end::deleteone[]

    // tag::dto[]
    fun findOne(title: String): BookDTO
    // end::dto[]

// tag::repository[]
}
// end::repository[]
