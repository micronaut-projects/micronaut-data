package io.micronaut.data.neo4j

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.NonUniqueResultException
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.neo4j.features.Employee
import io.micronaut.data.neo4j.features.EmployeeRepository
import io.micronaut.data.neo4j.features.Reading
import io.micronaut.data.neo4j.features.ReadingRepository
import io.micronaut.data.neo4j.features.ReactiveMovieRepository
import io.micronaut.data.neo4j.features.Player
import io.micronaut.data.neo4j.features.Tag
import io.micronaut.data.neo4j.features.TagRepository
import io.micronaut.data.neo4j.features.Team
import io.micronaut.data.neo4j.features.TeamRepository
import io.micronaut.data.neo4j.features.Topic
import io.micronaut.data.neo4j.features.TopicRepository
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.data.neo4j.tck.Neo4jMovieRepository
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
    EmployeeRepository employeeRepository

    @Inject
    Neo4jMovieRepository movieRepository

    @Inject
    ReadingRepository readingRepository

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

    void "numeric identifiers are those the queries match on"() {
        when:
        def tag = tagRepository.save(new Tag("graph"))

        then:
        operations.executeQuery("MATCH (t:Tag) RETURN id(t)", [:], Long) == [tag.id]
        tagRepository.findByName("graph").get().id == tag.id
    }

    void "sort by a database generated identifier"() {
        given:
        def saved = ["c", "a", "b"].collect { tagRepository.save(new Tag(it)) }

        expect:
        tagRepository.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.desc("id"))))*.id == saved*.id.sort().reverse().take(2)
    }

    void "a single result query rejects several rows"() {
        given:
        ["same", "same"].each { tagRepository.save(new Tag(it)) }

        when:
        tagRepository.findByName("same")

        then:
        thrown(NonUniqueResultException)
    }

    void "relationship entities between the same nodes stay separate, and saving one again updates it"() {
        given:
        def keanu = new Person("Keanu", 1964)
        def movie = new Movie("The Matrix", "Sci-fi", 1999)
        movie.roles = [new Role(["Neo"], keanu), new Role(["Thomas"], keanu)]

        when:
        def saved = movieRepository.save(movie)

        then:
        saved.roles*.id.toSet().size() == 2
        roleNames() == [["Neo"], ["Thomas"]]

        when:
        saved.roles[0].roles[0] = "The One"
        movieRepository.update(saved)

        then:
        roleNames() == [["The One"], ["Thomas"]]
    }

    private List<Object> roleNames() {
        return operations.executeQuery("MATCH (:Person)-[r:ACTED_IN]->(:Movie) RETURN r.roles ORDER BY r.roles", [:], List)
    }

    void "changing a single valued relationship replaces the old one"() {
        given:
        def ada = new Employee("Ada")
        ada.manager = new Employee("Bob")
        employeeRepository.save(ada)

        when:
        ada.manager = new Employee("Carol")
        employeeRepository.save(ada)

        then:
        employeeRepository.findByName("Ada").get().manager.name == "Carol"
        operations.executeQuery("MATCH (:Employee {name: 'Ada'})-[:REPORTS_TO]->(m) RETURN m.name", [:], String) == ["Carol"]
    }

    void "a null single valued relationship keeps the one stored, as it may not have been fetched"() {
        given:
        def ada = new Employee("Ada")
        ada.manager = new Employee("Bob")
        employeeRepository.save(ada)

        when:
        employeeRepository.update(employeeRepository.findById("Ada").get())

        then:
        employeeRepository.findByName("Ada").get().manager.name == "Bob"
    }

    void "a new related node with an assigned identifier goes through the persist lifecycle"() {
        given:
        def ada = new Employee("Ada")
        ada.manager = new Employee("Bob")

        when:
        employeeRepository.save(ada)

        then:
        ada.manager.created != null

        when: "a saved one is linked again"
        def created = employeeRepository.findById("Bob").get().created
        ada.manager = employeeRepository.findById("Bob").get()
        employeeRepository.update(ada)

        then:
        employeeRepository.findById("Bob").get().created == created
    }

    void "bound parameters are encoded once"() {
        given:
        readingRepository.save(new Reading("a", 1 as Short))

        expect: "the encoder that adds one was applied to the value stored and the argument the same number of times"
        readingRepository.findByLevel(1 as Short)*.name == ["a"]
    }

    void "page an explicit query that limits a WITH clause, not one that limits its RETURN"() {
        given:
        ["c", "a", "b"].each { tagRepository.save(new Tag(it)) }

        expect: "the page is taken from the two tags the WITH clause keeps"
        tagRepository.findFirstTwo(Pageable.unpaged())*.name == ["a", "b"]
        tagRepository.findFirstTwo(Pageable.from(1, 1))*.name == ["b"]
        tagRepository.findFirstTwo(Pageable.from(2, 1)).empty

        when:
        tagRepository.findTwo(Pageable.from(0, 1))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("declares SKIP or LIMIT itself")
    }
}
