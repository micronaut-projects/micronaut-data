package io.micronaut.data.r2dbc.h2;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import io.micronaut.data.tck.entities.Pet;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@R2dbcRepository(dialect = Dialect.H2)
interface H2PetRepository extends ReactiveStreamsCrudRepository<Pet, UUID> {

    @Join("owner")
    Mono<Pet> findByName(String name);

    @Transactional(Transactional.TxType.MANDATORY)
    @Override
    <S extends Pet> Publisher<S> save(@Valid @NotNull S entity);

    @Transactional(Transactional.TxType.MANDATORY)
    @Override
    <S extends Pet> Publisher<S> saveAll( @Valid @NotNull Iterable<S> entities);
}
