package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Mono;

@Neo4jRepository
public interface ReactiveEnsembleRepository extends ReactorCrudRepository<Ensemble, String> {

    @Join("seats")
    Mono<Ensemble> findByName(String name);
}
