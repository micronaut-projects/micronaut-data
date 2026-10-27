package example;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

// tag::repository[]
@Neo4jRepository("archive") // <1>
public interface ArchivedMovieRepository extends CrudRepository<Movie, String> {
}
// end::repository[]
