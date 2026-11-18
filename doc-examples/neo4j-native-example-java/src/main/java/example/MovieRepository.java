package example;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@Neo4jRepository
public interface MovieRepository extends CrudRepository<Movie, String> {

    List<Movie> findByDirectorsName(String name);

    List<Movie> findByReleasedGreaterThanOrderByReleased(int year);

    @Join("directors")
    Optional<Movie> findByTitle(String title);
}
