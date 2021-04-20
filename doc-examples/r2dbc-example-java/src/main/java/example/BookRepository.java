package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@R2dbcRepository(dialect = Dialect.MYSQL) // <1>
public interface BookRepository extends ReactiveStreamsCrudRepository<Book, Long> {
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
}
