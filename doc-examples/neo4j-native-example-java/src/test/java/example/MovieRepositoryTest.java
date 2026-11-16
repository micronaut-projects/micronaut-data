package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs Neo4j graph repositories, and in native image tests the native executable, against a Neo4j container.
 */
@MicronautTest(transactional = false)
class MovieRepositoryTest {

    @Inject
    MovieRepository movieRepository;

    @AfterEach
    void cleanup() {
        movieRepository.deleteAll();
    }

    @Test
    void testRepository() {
        Person lana = new Person("Lana Wachowski", 1965);
        movieRepository.save(new Movie("The Matrix", 1999, List.of(lana)));
        movieRepository.save(new Movie("The Matrix Reloaded", 2003, List.of(lana)));
        movieRepository.save(new Movie("Speed", 1994, List.of()));

        assertEquals(List.of("The Matrix", "The Matrix Reloaded"),
            movieRepository.findByDirectorsName("Lana Wachowski").stream().map(Movie::title).sorted().toList());
        assertEquals(List.of("The Matrix", "The Matrix Reloaded"),
            movieRepository.findByReleasedGreaterThanOrderByReleased(1995).stream().map(Movie::title).toList());
        assertEquals(List.of("Lana Wachowski"),
            movieRepository.findByTitle("The Matrix").orElseThrow().directors().stream().map(Person::name).toList());
        assertEquals(3, movieRepository.count());
    }
}
