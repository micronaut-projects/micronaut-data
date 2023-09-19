package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.sql.Procedure
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

@R2dbcRepository(dialect = Dialect.POSTGRES) // <1>
interface BookRepository extends ReactiveStreamsCrudRepository<Book, Long> {
    @NonNull
    @Override
    @Join("author")
    Mono<Book> findById(@NonNull @NotNull Long id); // <2>

    @NonNull
    @Override
    @Join("author")
    Flux<Book> findAll();

    // tag::mandatory[]
    @NonNull
    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    <S extends Book> Publisher<S> save(@NonNull @Valid @NotNull S entity);

    @NonNull
    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    <S extends Book> Publisher<S> saveAll(@NonNull @Valid @NotNull Iterable<S> entities);
    // end::mandatory[]

    // tag::procedure[]
    @Procedure
    Mono<Long> calculateSum(@NonNull Long bookId);
    // end::procedure[]
}
