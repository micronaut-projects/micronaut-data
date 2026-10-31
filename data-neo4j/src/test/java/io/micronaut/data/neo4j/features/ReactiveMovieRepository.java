package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.reactive.ReactorPageableRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Neo4jRepository
public interface ReactiveMovieRepository extends ReactorPageableRepository<Movie, String> {
    Flux<Movie> findByReleasedGreaterThan(int released);

    Mono<Movie> findByTitle(String title);
}
