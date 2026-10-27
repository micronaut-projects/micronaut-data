package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
class MovieRepositorySpec {

    @Inject
    MovieRepository movieRepository;

    @AfterEach
    void cleanup() {
        movieRepository.deleteAll().block();
    }

    @Test
    void testReactive() {
        // tag::reactive[]
        Person lana = new Person("Lana Wachowski", 1965);
        List<Movie> directedByLana = movieRepository.saveAll(List.of( // <1>
                new Movie("The Matrix", 1999, List.of(lana)),
                new Movie("The Matrix Reloaded", 2003, List.of(lana))
            ))
            .thenMany(movieRepository.findByDirectorsName("Lana Wachowski")) // <2>
            .collectList()
            .block(); // <3>
        // end::reactive[]

        assertEquals(2, directedByLana.size());
        assertEquals(List.of("The Matrix Reloaded"), movieRepository.findByReleasedGreaterThanOrderByReleased(2000).map(Movie::title).collectList().block());
    }
}
