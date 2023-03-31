package io.micronaut.data.document.mongodb.repositories;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.document.tck.entities.Pet;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@MongoRepository
interface MongoPetRepository extends ReactiveStreamsCrudRepository<Pet, String> {

    @Join("owner")
    Mono<Pet> findByName(String name);

    @Transactional(Transactional.TxType.MANDATORY)
    @Override
    <S extends Pet> Publisher<S> save(@Valid @NotNull S entity);

    @Transactional(Transactional.TxType.MANDATORY)
    @Override
    <S extends Pet> Publisher<S> saveAll( @Valid @NotNull Iterable<S> entities);
}
