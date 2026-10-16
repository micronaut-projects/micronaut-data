package example;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class ReactiveMovieService {

    private final ReactiveMovieRepository movieRepository;

    public ReactiveMovieService(ReactiveMovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    // tag::transactional[]
    @Transactional // <1>
    public Mono<Long> saveAndCount(Movie movie) {
        return movieRepository.save(movie).then(movieRepository.count()); // <2>
    }
    // end::transactional[]
}
