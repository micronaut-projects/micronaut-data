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
        movieRepository.deleteAll();
    }

    @Test
    void testSaveAndFind() {
        // tag::save[]
        Person lana = new Person("Lana Wachowski", 1965);
        movieRepository.save(new Movie("The Matrix", 1999, List.of(lana))); // <1>
        movieRepository.save(new Movie("The Matrix Reloaded", 2003, List.of(lana)));

        List<Movie> directedByLana = movieRepository.findByDirectorsName("Lana Wachowski"); // <2>
        // end::save[]

        assertEquals(2, directedByLana.size());
        assertEquals(List.of("The Matrix Reloaded"), movieRepository.findByReleasedGreaterThanOrderByReleased(2000).stream().map(Movie::title).toList());
    }
}
