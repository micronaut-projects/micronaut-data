package example;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Neo4jRepository
public interface MovieRepository extends CrudRepository<Movie, String> {

    List<Movie> findByReleasedGreaterThan(int released);

    @Join("directors")
    List<Movie> findByDirectorsName(String name);
}
