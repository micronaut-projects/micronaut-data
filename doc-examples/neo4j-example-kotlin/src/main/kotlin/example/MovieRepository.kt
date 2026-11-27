package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.CrudRepository

// tag::repository[]
@Neo4jRepository // <1>
interface MovieRepository : CrudRepository<Movie, String> { // <2>
// end::repository[]

    // tag::finders[]
    fun findByReleasedGreaterThanOrderByReleased(year: Int): List<Movie>

    fun findByTitleContainsIgnoreCase(text: String): List<Movie>

    fun countByReleasedLessThan(year: Int): Long

    fun findByReleasedGreaterThan(year: Int, pageable: Pageable): Page<Movie>
    // end::finders[]

    // tag::traversal[]
    fun findByDirectorsName(name: String): List<Movie> // <1>

    fun findByRolesPersonName(name: String): List<Movie> // <2>
    // end::traversal[]

    // tag::join[]
    @Join("roles") // <1>
    @Join("directors")
    fun findByTitle(title: String): Movie?
    // end::join[]

    // tag::nestedJoin[]
    @Join("directors")
    @Join("directors.follows") // <1>
    fun findByReleased(year: Int): List<Movie>
    // end::nestedJoin[]

    // tag::projection[]
    fun findTitleByReleasedGreaterThanOrderByTitle(year: Int): List<String>

    fun findByReleasedBetween(from: Int, to: Int): List<MovieSummary>
    // end::projection[]

    // tag::query[]
    @Query("MATCH (m:Movie)<-[:ACTED_IN]-(p:Person) WHERE p.name = \$name RETURN m ORDER BY m.released")
    fun findMoviesActedInBy(name: String): List<Movie>

    @Query("MATCH (m:Movie {title: \$title}) SET m.votes = coalesce(m.votes, 0) + 1")
    fun vote(title: String)
    // end::query[]
// tag::repository[]
}
// end::repository[]
