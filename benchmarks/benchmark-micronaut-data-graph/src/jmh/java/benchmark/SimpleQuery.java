package benchmark;

import example.MovieDatabase;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Runs finders of Micronaut Data Neo4j against an in-process Neo4j server. The Spring Data Neo4j counterpart is the
 * {@code SimpleQuery} benchmark of benchmark-spring-data-neo4j, over the same data.
 */
@State(Scope.Benchmark)
public class SimpleQuery {

    private MovieDatabase database;

    @Setup
    public void prepare() {
        database = new MovieDatabase();
    }

    @TearDown
    public void cleanup() {
        database.close();
    }

    @Benchmark
    public void findByTitle(Blackhole blackhole) {
        blackhole.consume(database.movieRepository().findByTitle("Movie 42"));
    }

    @Benchmark
    public void findByReleasedGreaterThan(Blackhole blackhole) {
        // A third of the movies
        blackhole.consume(database.movieRepository().findByReleasedGreaterThan(2009));
    }
}
