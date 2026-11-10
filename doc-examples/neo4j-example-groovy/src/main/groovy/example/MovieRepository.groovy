package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.CrudRepository

// tag::repository[]
@Neo4jRepository // <1>
interface MovieRepository extends CrudRepository<Movie, String> { // <2>
// end::repository[]

    // tag::finders[]
    List<Movie> findByReleasedGreaterThanOrderByReleased(int year)

    List<Movie> findByTitleContainsIgnoreCase(String text)

    long countByReleasedLessThan(int year)

    Page<Movie> findByReleasedGreaterThan(int year, Pageable pageable)
    // end::finders[]

    // tag::traversal[]
    List<Movie> findByDirectorsName(String name) // <1>

    List<Movie> findByRolesPersonName(String name) // <2>
    // end::traversal[]

    // tag::join[]
    @Join("roles") // <1>
    @Join("directors")
    Optional<Movie> findByTitle(String title)
    // end::join[]

    // tag::nestedJoin[]
    @Join("directors")
    @Join("directors.follows") // <1>
    List<Movie> findByReleased(int year)
    // end::nestedJoin[]

    // tag::projection[]
    List<String> findTitleByReleasedGreaterThanOrderByTitle(int year)

    List<MovieSummary> findByReleasedBetween(int from, int to)
    // end::projection[]

    // tag::query[]
    @Query('MATCH (m:Movie)<-[:ACTED_IN]-(p:Person) WHERE p.name = $name RETURN m ORDER BY m.released')
    List<Movie> findMoviesActedInBy(String name)

    @Query('MATCH (m:Movie {title: $title}) SET m.votes = coalesce(m.votes, 0) + 1')
    void vote(String title)
    // end::query[]
// tag::repository[]
}
// end::repository[]
