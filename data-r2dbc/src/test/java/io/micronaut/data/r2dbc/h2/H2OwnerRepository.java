package io.micronaut.data.r2dbc.h2;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import io.micronaut.data.tck.entities.Owner;
import io.micronaut.data.tck.entities.Pet;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.Arrays;

@R2dbcRepository(dialect = Dialect.H2)
abstract class H2OwnerRepository implements ReactiveStreamsCrudRepository<Owner, Long> {

    private final H2PetRepository petRepository;
    private final R2dbcOperations operations;

    protected H2OwnerRepository(H2PetRepository petRepository, R2dbcOperations operations) {
        this.petRepository = petRepository;
        this.operations = operations;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    @Override
    @NonNull
    public abstract Publisher<Owner> findAll();

    @Transactional
    public Mono<Void> setupData() {
        return Mono.from(operations.withTransaction(status ->
                Flux.from(save(new Owner("Fred")))
                .flatMap(owner ->
                        petRepository.saveAll(Arrays.asList(
                                new Pet("Dino", owner),
                                new Pet("Hoppy", owner)
                        ))
                ).then(Flux.from(save(new Owner("Barney")))
                        .flatMap(owner ->
                                petRepository.save(new Pet("Rabbid", owner))
                        ).then()
                )));
    }

    @Transactional
    public Mono<Void> testSetRollbackOnly() {
        return Mono.from(operations.withTransaction(status ->
                Flux.from(save(new Owner("Fred")))
                        .flatMap(owner ->
                                petRepository.saveAll(Arrays.asList(
                                        new Pet("Dino", owner),
                                        new Pet("Hoppy", owner)
                                ))
                        ).then(Flux.from(save(new Owner("Barney")))
                        .flatMap(owner ->
                                petRepository.save(new Pet("Rabbid", owner))
                        ).map(pet -> {
                            status.setRollbackOnly();
                            return pet;
                        }).then()
                )));
    }


    @Transactional
    public Mono<Void> testRollbackOnException() {
        return Flux.from(save(new Owner("Fred")))
                        .flatMap(owner ->
                                petRepository.saveAll(Arrays.asList(
                                        new Pet("Dino", owner),
                                        new Pet("Hoppy", owner)
                                ))
                        ).then(Flux.from(save(new Owner("Barney")))
                        .flatMap(owner ->
                                petRepository.save(new Pet("Rabbid", owner))
                        ).flatMap(pet -> Mono.error(new RuntimeException("Something bad happened"))).then()
                );
    }
}