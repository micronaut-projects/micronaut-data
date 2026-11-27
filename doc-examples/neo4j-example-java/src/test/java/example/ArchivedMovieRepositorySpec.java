package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchivedMovieRepositorySpec extends AbstractNeo4jTest {

    @Inject
    ArchivedMovieRepository archivedMovieRepository;

    @Inject
    MovieRepository movieRepository;

    @Override
    public Map<String, String> getProperties() {
        // Both drivers connect to the same server in the test
        return Map.of(
            "neo4j.uri", EmbeddedNeo4j.boltUri(),
            "micronaut.data.neo4j.drivers.archive.uri", EmbeddedNeo4j.boltUri()
        );
    }

    @AfterEach
    void cleanup() {
        movieRepository.deleteAll();
    }

    @Test
    void testArchiveDriver() {
        archivedMovieRepository.save(new Movie("Metropolis", "The mediator between head and hands must be the heart", 1927));

        assertTrue(movieRepository.existsById("Metropolis"));
    }
}
