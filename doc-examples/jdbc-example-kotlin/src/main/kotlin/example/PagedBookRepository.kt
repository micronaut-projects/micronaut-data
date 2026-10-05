package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface PagedBookRepository : KotlinCrudRepository<Book, Long> {

    // tag::explicit[]
    @Query(value = "SELECT book_.* FROM book book_ WHERE book_.pages > :pages", // <1>
           countQuery = "SELECT COUNT(*) FROM book book_ WHERE book_.pages > :pages") // <2>
    fun findLongBooks(pages: Int, pageable: Pageable): Page<Book>

    @Query("SELECT book_.* FROM book book_ WHERE book_.pages > :pages") // <3>
    fun sliceLongBooks(pages: Int, pageable: Pageable): Slice<Book>

    @Query("SELECT book_.* FROM book book_ WHERE book_.pages > :pages") // <3>
    fun listLongBooks(pages: Int, pageable: Pageable): List<Book>

    @Query("SELECT book_.* FROM book book_ WHERE book_.pages > :pages") // <4>
    fun listLongBooksSorted(pages: Int, sort: Sort): List<Book>
    // end::explicit[]

    // tag::native[]
    @Query(value = "SELECT * FROM book b WHERE b.title LIKE :title",
           countQuery = "SELECT COUNT(*) FROM book b WHERE b.title LIKE :title",
           nativeQuery = true) // <1>
    fun searchByTitle(title: String, pageable: Pageable): Page<Book>
    // end::native[]

    fun list(pageable: Pageable): Page<BookSummary>
}
