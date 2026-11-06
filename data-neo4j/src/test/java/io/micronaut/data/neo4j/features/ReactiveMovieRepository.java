package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

@Neo4jRepository
public interface ReactiveMovieRepository extends ReactorCrudRepository<Movie, String> {
    Flux<Movie> findByReleasedGreaterThan(int released);
}
