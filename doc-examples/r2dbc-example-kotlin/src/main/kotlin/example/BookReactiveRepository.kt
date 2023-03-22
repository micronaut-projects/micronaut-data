package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import javax.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

@R2dbcRepository(dialect = Dialect.MYSQL) // <1>
interface BookReactiveRepository : ReactiveStreamsCrudRepository<Book?, Long> {
    @Join("author")
    override fun findById(id: @NotNull Long): Mono<Book> // <2>

    @Join("author")
    override fun findAll(): Flux<Book>

    // tag::mandatory[]
    @Transactional(Transactional.TxType.MANDATORY)
    override fun <S : Book?> save(entity: @Valid @NotNull S): Publisher<S>

    @Transactional(Transactional.TxType.MANDATORY)
    override fun <S : Book?> saveAll(entities: @Valid @NotNull Iterable<S>): Publisher<S>
    // end::mandatory[]
}
