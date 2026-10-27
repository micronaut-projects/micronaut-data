package io.micronaut.data.age.r2dbc;

import io.micronaut.data.age.r2dbc.annotation.AgeR2dbcRepository;
import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.repository.reactive.ReactorPageableRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AgeR2dbcRepository
public interface ReactiveMovieRepository extends ReactorPageableRepository<Movie, String> {
    Flux<Movie> findByReleasedGreaterThan(int released);

    Mono<Movie> findByTitle(String title);

    java.util.concurrent.CompletableFuture<Long> countByReleasedGreaterThan(int released);
}
