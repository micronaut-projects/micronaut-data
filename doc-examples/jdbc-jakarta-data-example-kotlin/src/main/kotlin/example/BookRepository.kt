package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import jakarta.data.Sort
import jakarta.data.page.CursoredPage
import jakarta.data.page.Page
import jakarta.data.page.PageRequest
import jakarta.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository : CrudRepository<Book, Long> {

    fun count(): Long

    fun deleteAll()

    fun findByPagesGreaterThan(pageCount: Int, pageRequest: PageRequest): List<Book>

    fun findByTitleLike(title: String, pageRequest: PageRequest): Page<Book>

    fun list(pageRequest: PageRequest): Page<Book>

    fun find(pageRequest: PageRequest, sort: Sort<*>): CursoredPage<Book>

    fun findByPagesBetween(minPageCount: Int, maxPageCount: Int, pageRequest: PageRequest): CursoredPage<Book>

    fun findByTitleStartingWith(title: String, pageRequest: PageRequest): Page<Book>
}
