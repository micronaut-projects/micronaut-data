package example

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import javax.validation.constraints.NotNull

@R2dbcRepository(dialect = Dialect.MYSQL) // <1>
interface AuthorRepository : ReactiveStreamsCrudRepository<Author, Long> {
    override fun findById(id: @NotNull Long): Mono<Author> // <2>
    override fun findAll(): Flux<Author>
}