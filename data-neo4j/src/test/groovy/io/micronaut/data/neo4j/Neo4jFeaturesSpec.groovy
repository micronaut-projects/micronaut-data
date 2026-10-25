package io.micronaut.data.neo4j

import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.neo4j.features.ReactiveMovieRepository
import io.micronaut.data.neo4j.features.Player
import io.micronaut.data.neo4j.features.Tag
import io.micronaut.data.neo4j.features.TagRepository
import io.micronaut.data.neo4j.features.Team
import io.micronaut.data.neo4j.features.TeamRepository
import io.micronaut.data.neo4j.features.Topic
import io.micronaut.data.neo4j.features.TopicRepository
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class Neo4jFeaturesSpec extends Specification implements TestPropertyProvider {

    @Inject
    TagRepository tagRepository

    @Inject
    TopicRepository topicRepository

    @Inject
    TeamRepository teamRepository

    @Inject
    ReactiveMovieRepository reactiveMovieRepository

    @Inject
    Neo4jRepositoryOperations operations

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }

    void setup() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
    }

    void "database generated numeric identifiers"() {
        when:
        def tag = tagRepository.save(new Tag("graph"))

        then:
        tag.id != null
        tagRepository.findById(tag.id).get().name == "graph"
        tagRepository.findByName("graph").get().id == tag.id

        when:
        tagRepository.deleteById(tag.id)

        then:
        tagRepository.count() == 0
    }

    void "database generated element identifiers on immutable records"() {
        when:
        def saved = topicRepository.save(new Topic(null, "Databases", [new Tag("graph"), new Tag("nosql")]))

        then:
        saved.id() != null
        saved.tags().every { it.id != null }

        when:
        def found = topicRepository.findByTitle("Databases").get()

        then:
        found.id() == saved.id()
        found.tags()*.name.sort() == ["graph", "nosql"]
    }

    void "immutable records that reference each other"() {
        given: "a team whose player refers back to the team instance being saved"
        def players = []
        def team = new Team(null, "Wanderers", players)
        players << new Player(null, "Ada", team)

        when:
        def saved = teamRepository.save(team)

        then:
        saved.id() != null
        saved.players()*.id().every { it != null }
        saved.players()[0].team().id() == saved.id()

        and: "the player is linked to the one team node"
        teamRepository.findByName("Wanderers").get().players()*.name() == ["Ada"]
        operations.executeQuery("MATCH (p:Player)-[:PLAYS_FOR]->(t:Team) RETURN count(t)", [:], Long) == [1L]
        teamRepository.count() == 1
    }

    void "reactive repositories"() {
        when:
        reactiveMovieRepository.save(new Movie("Speed", "Get ready for rush hour", 1994)).block()
        reactiveMovieRepository.save(new Movie("The Matrix", "Welcome to the Real World", 1999)).block()

        then:
        reactiveMovieRepository.findByReleasedGreaterThan(1995).collectList().block()*.title == ["The Matrix"]
        reactiveMovieRepository.count().block() == 2
    }

    void "run explicit Cypher through the operations"() {
        given:
        tagRepository.save(new Tag("graph"))

        expect:
        operations.executeQuery("MATCH (t:Tag) RETURN t", [:], Tag)*.name == ["graph"]
        operations.executeQuery("MATCH (t:Tag) RETURN t.name AS name", [:])*.get("name") == ["graph"]
        operations.executeQuery("MATCH (t:Tag {name: \$name}) RETURN count(t)", [name: "graph"], Long) == [1L]
    }
}
