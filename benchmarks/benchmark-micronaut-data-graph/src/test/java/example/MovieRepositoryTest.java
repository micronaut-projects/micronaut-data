package example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovieRepositoryTest {

    @Test
    void testFinders() {
        try (MovieDatabase database = new MovieDatabase()) {
            assertEquals(1042L, database.movieRepository().findByTitle("Movie 42").orElseThrow().getVotes());
            assertEquals(30, database.movieRepository().findByReleasedGreaterThan(2009).size());
        }
    }
}
