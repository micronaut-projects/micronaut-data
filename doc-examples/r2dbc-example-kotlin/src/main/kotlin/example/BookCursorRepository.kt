package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.CoroutinePageableCrudRepository

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface BookCursorRepository : CoroutinePageableCrudRepository<Book, Long> {

    @Query(
        value = "SELECT book_.* FROM book book_ WHERE (:minPages IS NULL OR book_.pages >= :minPages)",
        countQuery = "SELECT COUNT(*) FROM book book_ WHERE (:minPages IS NULL OR book_.pages >= :minPages)"
    )
    suspend fun findByCursor(minPages: Int?, pageable: Pageable): CursoredPage<Book>
}
