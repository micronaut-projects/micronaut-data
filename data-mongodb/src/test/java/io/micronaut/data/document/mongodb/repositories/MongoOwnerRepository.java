package io.micronaut.data.document.mongodb.repositories;

import com.mongodb.reactivestreams.client.ClientSession;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.document.tck.entities.Owner;
import io.micronaut.data.document.tck.entities.Pet;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import io.micronaut.transaction.reactive.ReactiveTransactionOperations;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.Arrays;

@MongoRepository
public abstract class MongoOwnerRepository implements ReactiveStreamsCrudRepository<Owner, String> {

    private final MongoPetRepository petRepository;
    private final ReactiveTransactionOperations<ClientSession> operations;

    protected MongoOwnerRepository(MongoPetRepository petRepository, ReactiveTransactionOperations<ClientSession> operations) {
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