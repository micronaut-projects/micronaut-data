package example

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import jakarta.validation.constraints.NotNull

@R2dbcRepository(dialect = Dialect.MYSQL)
interface BlockingAuthorRepository : CrudRepository<Author, Long> {}
