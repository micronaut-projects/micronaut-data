package example;

import io.micronaut.data.neo4j.operations.Neo4jReactorRepositoryOperations;
import io.micronaut.data.neo4j.transaction.Neo4jReactorTransactionOperations;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.TransactionDefinition;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReactiveMovieRepositorySpec extends AbstractNeo4jTest {

    @Inject
    ReactiveMovieRepository movieRepository;

    @Inject
    ReactiveMovieService movieService;

    @Inject
    Neo4jReactorTransactionOperations transactionOperations;

    @Inject
    Neo4jReactorRepositoryOperations reactiveOperations;

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

    @Test
    void testTransactions() {
        long count = movieService.saveAndCount(new Movie("The Matrix", "Welcome to the Real World", 1999)).block();

        // tag::transactionOperations[]
        List<Movie> saved = transactionOperations.withTransaction(TransactionDefinition.DEFAULT, status -> // <1>
            movieRepository.saveAll(List.of(new Movie("Speed", "Get ready for rush hour", 1994)))
        ).collectList().block();
        // end::transactionOperations[]

        assertEquals(1, count);
        assertEquals(1, saved.size());
        assertEquals(2L, movieRepository.count().block());
    }

    @Test
    void testReactiveQuery() {
        movieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994)).block();

        // tag::reactiveQuery[]
        List<Movie> movies = reactiveOperations.executeQuery("MATCH (m:Movie) RETURN m", Map.of(), Movie.class) // <1>
            .collectList()
            .block();
        // end::reactiveQuery[]

        assertEquals("Speed", movies.get(0).getTitle());
    }
}
