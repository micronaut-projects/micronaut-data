package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class ReactiveMovieService {

    private final ReactiveMovieRepository repository;

    public ReactiveMovieService(ReactiveMovieRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Mono<Long> saveAndCount(Movie movie) {
        return repository.save(movie).then(repository.count());
    }

    @Transactional
    public Mono<Movie> saveAndFail(Movie movie) {
        return repository.save(movie).then(Mono.error(new IllegalStateException("Rolled back")));
    }
}
