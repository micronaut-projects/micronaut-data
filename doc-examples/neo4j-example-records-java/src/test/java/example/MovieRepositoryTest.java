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
class MovieRepositoryTest extends AbstractNeo4jTest {

    @Inject
    MovieRepository movieRepository;

    @AfterEach
    void cleanup() {
        movieRepository.deleteAll();
    }

    @Test
    void testRecords() {
        movieRepository.save(new Movie("The Matrix", 1999, List.of(new Person("Lana Wachowski", 1965))));
        movieRepository.save(new Movie("Speed", 1994, null));

        assertEquals(List.of("The Matrix"), movieRepository.findByReleasedGreaterThan(1995).stream().map(Movie::title).toList());
        List<Movie> directed = movieRepository.findByDirectorsName("Lana Wachowski");
        assertEquals(List.of("Lana Wachowski"), directed.get(0).directors().stream().map(Person::name).toList());
    }
}
