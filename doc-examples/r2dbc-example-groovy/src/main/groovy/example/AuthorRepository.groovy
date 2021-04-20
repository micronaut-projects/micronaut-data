package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import javax.validation.constraints.NotNull

@R2dbcRepository(dialect = Dialect.MYSQL) // <1>
interface AuthorRepository extends ReactiveStreamsCrudRepository<Author, Long> {
    @NonNull
    @Override
    Mono<Author> findById(@NonNull @NotNull Long aLong) // <2>

    @NonNull
    @Override
    Flux<Author> findAll()
}
