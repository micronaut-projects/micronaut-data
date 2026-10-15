package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReactiveMovieRepositorySpec extends AbstractNeo4jTest {

    @Inject
    ReactiveMovieRepository movieRepository;

    @AfterEach
    void cleanup() {
        movieRepository.deleteAll().block();
    }

    @Test
    void testReactive() {
        // tag::reactive[]
        List<String> titles = movieRepository.saveAll(List.of( // <1>
                new Movie("The Matrix", "Welcome to the Real World", 1999),
                new Movie("Speed", "Get ready for rush hour", 1994)
            ))
            .thenMany(movieRepository.findByReleasedGreaterThan(1995)) // <2>
            .map(Movie::getTitle)
            .collectList()
            .block(); // <3>
        // end::reactive[]

        assertEquals(List.of("The Matrix"), titles);
    }
}
