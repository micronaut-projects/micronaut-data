package example;

import io.micronaut.data.age.r2dbc.annotation.AgeR2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

// tag::repository[]
@AgeR2dbcRepository // <1>
public interface MovieRepository extends ReactorCrudRepository<Movie, String> { // <2>

    Flux<Movie> findByDirectorsName(String name); // <3>

    Flux<Movie> findByReleasedGreaterThanOrderByReleased(int year);
}
// end::repository[]
