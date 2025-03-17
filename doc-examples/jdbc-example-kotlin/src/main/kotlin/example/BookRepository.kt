
// tag::repository[]
package example

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.sql.Procedure
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.*
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import jakarta.transaction.Transactional

@JdbcRepository(dialect = Dialect.H2) // <1>
interface BookRepository : CrudRepository<Book, Long> { // <2>
// end::repository[]

    // tag::simple[]
    fun findByTitle(title: String): Book

    fun getByTitle(title: String): Book

    fun retrieveByTitle(title: String): Book
    // end::simple[]

    // tag::simple-alt[]
    // tag::repository[]
    @Executable
    fun find(title: String): Book
    // end::simple-alt[]
    // end::repository[]

    // tag::greaterthan[]
    fun findByPagesGreaterThan(pageCount: Int): List<Book>
    // end::greaterthan[]

    // tag::logical[]
    fun findByPagesGreaterThanOrTitleLike(pageCount: Int, title: String): List<Book>
    // end::logical[]

    // tag::pageable[]
    fun findByPagesGreaterThan(pageCount: Int, pageable: Pageable): List<Book>

    fun findByTitleLike(title: String, pageable: Pageable): Page<Book>

    fun list(pageable: Pageable): Slice<Book>
    // end::pageable[]

    // tag::cursored-pageable[]
    fun find(pageable: CursoredPageable): CursoredPage<Book> // <1>

    fun findByPagesBetween(minPageCount: Int, maxPageCount: Int, pageable: Pageable): CursoredPage<Book> // <2>

    fun findByTitleStartingWith(title: String, pageable: Pageable): Page<Book>  // <3>
    // end::cursored-pageable[]

    // tag::simple-projection[]
    fun findTitleByPagesGreaterThan(pageCount: Int): List<String>
    // end::simple-projection[]

    // tag::top-projection[]
    fun findTop3ByTitleLike(title: String): List<Book>
    // end::top-projection[]

    // tag::ordering[]
    fun listOrderByTitle(): List<Book>

    fun listOrderByTitleDesc(): List<Book>
    // end::ordering[]

    // tag::explicit[]
    @Query("SELECT * FROM book as b WHERE b.title = :t ORDER BY b.title")
    fun listBooks(t: String): List<Book>
    // end::explicit[]

    // tag::save[]
    fun persist(entity: Book): Book
    // end::save[]

    // tag::save2[]
    fun persist(title: String, pages: Int): Book
    // end::save2[]

    // tag::update[]
    fun update(@Id id: Long?, pages: Int)

    fun update(@Id id: Long?, title: String)
    // end::update[]

    // tag::update2[]
    fun updateByTitle(title: String, pages: Int)
    // end::update2[]

    // tag::deleteall[]
    override fun deleteAll()
    // end::deleteall[]

    @Transactional(Transactional.TxType.MANDATORY)
    fun deleteAllRequiresTx() {
        deleteAll()
    }

    // tag::deleteone[]
    fun delete(title: String)
    // end::deleteone[]

    // tag::deleteby[]
    fun deleteByTitleLike(title: String)
    // end::deleteby[]

    // tag::dto[]
    fun findOne(title: String): BookDTO
    // end::dto[]

    // tag::native[]
    @Query("select * from book b where b.title like :title limit 5")
    fun findBooks(title: String): List<Book>
    // end::native[]

    // tag::procedure[]
    @Procedure
    fun calculateSum(bookId: @NonNull Long): Long
    // end::procedure[]

    // tag::onetomanycustom[]
    @Query("""
        SELECT book_.*,
               reviews_.id AS reviews_id, reviews_.reviewer AS reviews_reviewer,
               reviews_.content AS reviews_content, reviews_.book_id AS reviews_book_id
        FROM book book_ INNER JOIN review reviews_ ON book_.id = reviews_.book_id
        WHERE book_.title = :title
        """)
    @Join("reviews")
    fun searchBooksByTitle(title: String): List<Book>
    // end::onetomanycustom[]

// tag::repository[]
}
// end::repository[]
