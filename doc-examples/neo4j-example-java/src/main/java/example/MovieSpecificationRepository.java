package example;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;

// tag::repository[]
@Neo4jRepository
public interface MovieSpecificationRepository extends CrudRepository<Movie, String>, JpaSpecificationExecutor<Movie> { // <1>
}
// end::repository[]
