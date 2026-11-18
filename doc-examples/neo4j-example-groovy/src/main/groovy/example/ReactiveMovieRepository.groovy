package example

// tag::repository[]
import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Flux

@Neo4jRepository
interface ReactiveMovieRepository extends ReactorCrudRepository<Movie, String> { // <1>

    Flux<Movie> findByReleasedGreaterThan(int year) // <2>
}
// end::repository[]
