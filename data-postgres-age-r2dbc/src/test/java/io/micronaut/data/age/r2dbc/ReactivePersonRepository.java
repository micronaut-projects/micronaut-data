package io.micronaut.data.age.r2dbc;

import io.micronaut.data.age.r2dbc.annotation.AgeR2dbcRepository;
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.tck.entities.Person;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@AgeR2dbcRepository
public interface ReactivePersonRepository extends ReactorCrudRepository<Person, UUID> {
    Flux<Person> findByNameStartsWith(String prefix);

    @PathLimit(3)
    Mono<Boolean> existsPathByMentorBetweenName(String from, String to);
}
