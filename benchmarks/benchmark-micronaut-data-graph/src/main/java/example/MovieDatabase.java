package example;

import io.micronaut.context.ApplicationContext;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Starts an in-process Neo4j server and the application context, and saves the movies the benchmarks query.
 */
public final class MovieDatabase implements AutoCloseable {

    public static final int MOVIES = 100;

    private final Neo4j server;
    private final ApplicationContext applicationContext;
    private final MovieRepository movieRepository;

    public MovieDatabase() {
        server = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
        applicationContext = ApplicationContext.run(Map.of("neo4j.uri", server.boltURI().toString()));
        movieRepository = applicationContext.getBean(MovieRepository.class);
        List<Movie> movies = new ArrayList<>(MOVIES);
        for (int i = 0; i < MOVIES; i++) {
            Movie movie = new Movie("Movie " + i, "Tagline of movie " + i, 1990 + i % 30);
            movie.setVotes(1000L + i);
            movies.add(movie);
        }
        movieRepository.saveAll(movies);
    }

    public MovieRepository movieRepository() {
        return movieRepository;
    }

    @Override
    public void close() {
        applicationContext.close();
        server.close();
    }
}
