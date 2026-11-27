package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.graph.annotation.PathLimit
import io.micronaut.data.graph.model.GraphPath
import io.micronaut.data.graph.model.GraphRelationship
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.CrudRepository
import java.util.UUID

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

    // tag::crossType[]
    fun findRelationshipsByDirectors(title: String, director: UUID): List<GraphRelationship<Person, Movie>> // <1>

    @PathLimit(3)
    fun findAllPathsToPerson(title: String, person: UUID): List<GraphPath<Any>> // <2>

    @PathLimit(4)
    fun findShortestPathToPersonBetweenTitleAndName(title: String, name: String): GraphPath<Any>? // <3>
    // end::crossType[]

    // tag::relationshipEntities[]
    fun findRelationshipsByRoles(title: String, person: UUID): List<Role> // <1>
    // end::relationshipEntities[]

    // tag::query[]
    @Query("MATCH (m:Movie)<-[:ACTED_IN]-(p:Person) WHERE p.name = \$name RETURN m ORDER BY m.released")
    fun findMoviesActedInBy(name: String): List<Movie>

    @Query("MATCH (m:Movie {title: \$title}) SET m.votes = coalesce(m.votes, 0) + 1")
    fun vote(title: String)
    // end::query[]
// tag::repository[]
}
// end::repository[]
