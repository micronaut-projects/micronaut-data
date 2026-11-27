package example;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@Neo4jRepository
public interface MovieRepository extends CrudRepository<Movie, String> {

    Optional<Movie> findByTitle(String title);

    List<Movie> findByReleasedGreaterThan(int released);
}
