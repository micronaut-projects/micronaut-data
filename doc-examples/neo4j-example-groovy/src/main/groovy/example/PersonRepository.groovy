package example

import io.micronaut.data.graph.annotation.PathLimit
import io.micronaut.data.graph.model.GraphPath
import io.micronaut.data.graph.model.GraphRelationship
import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.CrudRepository

@Neo4jRepository
interface PersonRepository extends CrudRepository<Person, UUID> {

    Optional<Person> findByName(String name)

    // tag::paths[]
    Optional<GraphPath<Person>> findShortestPath(Person from, Person to) // <1>

    Optional<GraphPath<Person>> findShortestPathByFollows(UUID from, UUID to, @PathLimit int maxDepth) // <2>

    @PathLimit(4)
    List<GraphPath<Person>> findAllPathsByFollows(Person from, Person to) // <3>

    boolean existsPathByFollows(Person from, Person to) // <4>
    // end::paths[]

    // tag::relationships[]
    List<GraphRelationship<Person, Person>> findRelationships(Person from, Person to)

    long countRelationshipsByFollows()
    // end::relationships[]
}
