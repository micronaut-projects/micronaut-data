package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface BookCoroutineRepository : CoroutineCrudRepository<Book, Long> {

    suspend fun find(pageable: CursoredPageable): CursoredPage<Book>

    suspend fun findByPagesBetween(minPageCount: Int, maxPageCount: Int, pageable: Pageable): CursoredPage<Book>

    suspend fun findByTitleStartingWith(title: String, pageable: Pageable): Page<Book>

}
