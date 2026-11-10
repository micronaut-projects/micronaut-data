package example

import io.micronaut.data.graph.annotation.PathLimit
import io.micronaut.data.graph.model.GraphPath
import io.micronaut.data.graph.model.GraphRelationship
import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.CrudRepository
import java.util.UUID

@Neo4jRepository
interface PersonRepository : CrudRepository<Person, UUID> {

    fun findByName(name: String): Person?

    // tag::paths[]
    fun findShortestPath(from: Person, to: Person): GraphPath<Person>? // <1>

    fun findShortestPathByFollows(from: UUID, to: UUID, @PathLimit maxDepth: Int): GraphPath<Person>? // <2>

    @PathLimit(4)
    fun findAllPathsByFollows(from: Person, to: Person): List<GraphPath<Person>> // <3>

    fun existsPathByFollows(from: Person, to: Person): Boolean // <4>
    // end::paths[]

    // tag::relationships[]
    fun findRelationships(from: Person, to: Person): List<GraphRelationship<Person, Person>>

    fun countRelationshipsByFollows(): Long
    // end::relationships[]
}
