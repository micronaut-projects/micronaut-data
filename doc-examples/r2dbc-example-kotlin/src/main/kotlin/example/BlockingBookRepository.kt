package example

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository

@R2dbcRepository(dialect = Dialect.MYSQL)
interface BlockingBookRepository : CrudRepository<Book, Long> {

    fun findOne(title: String): BookDTO

}
