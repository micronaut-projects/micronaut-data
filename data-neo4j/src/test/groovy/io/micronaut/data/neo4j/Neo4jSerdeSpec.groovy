package io.micronaut.data.neo4j

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Query
import io.micronaut.data.graph.runtime.GraphNode
import io.micronaut.data.graph.runtime.serde.GraphSerde
import io.micronaut.serde.SerdeRegistry
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.core.type.Argument
import io.micronaut.data.graph.tck.entities.Role
import io.micronaut.data.neo4j.features.Event
import io.micronaut.data.neo4j.features.EventRepository
import io.micronaut.data.neo4j.features.Tag
import io.micronaut.data.neo4j.features.Topic
import io.micronaut.data.neo4j.features.TopicRepository
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.data.neo4j.tck.Neo4jMovieRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Reads nodes with Micronaut Serialization and with GraphEntityMapper, and checks both give the same entities.
 */
@MicronautTest(transactional = false)
class Neo4jSerdeSpec extends Specification implements TestPropertyProvider {

    @Inject
    ApplicationContext context

    @Inject
    Neo4jRepositoryOperations operations

    @Inject
    Neo4jMovieRepository movieRepository

    @Inject
    RuntimeEntityRegistry entityRegistry

    @Inject
    TopicRepository topicRepository

    @Inject
    EventRepository eventRepository

    @Inject
    GraphSerde serde

    @Override
    Map<String, String> getProperties() {
        return ['neo4j.uri': EmbeddedNeo4j.boltUri()]
    }

    void setup() {
        operations.executeQuery("MATCH (n) DETACH DELETE n", [:])
        def keanu = new Person("Keanu Reeves", 1964)
        def carrie = new Person("Carrie-Anne Moss", 1967)
        def lana = new Person("Lana Wachowski", 1965)
        def matrix = new Movie("The Matrix", "Welcome to the Real World", 1999)
        matrix.roles = [new Role(["Neo"], keanu), new Role(["Trinity"], carrie)]
        matrix.directors = [lana]
        matrix.votes = 42L
        movieRepository.save(matrix)
    }

    void "temporal values, identifiers, enums, decimals and attribute converters"() {
        given:
        def instant = Instant.parse("2026-10-16T09:30:15.123456789Z")
        def event = new Event("launch", LocalDate.of(2026, 10, 16), LocalDateTime.of(2026, 10, 16, 9, 30, 15),
            instant, ZonedDateTime.of(2026, 10, 16, 11, 30, 15, 0, ZoneId.of("Europe/Madrid")),
            OffsetDateTime.of(2026, 10, 16, 9, 30, 15, 0, ZoneOffset.ofHours(2)), Date.from(instant.truncatedTo(ChronoUnit.MILLIS)),
            Duration.ofMinutes(90), UUID.fromString("6f9619ff-8b86-d011-b42d-00cf4fc964ff"), Event.Priority.HIGH,
            new BigDecimal("19.99"), new Event.Points(7))

        when:
        eventRepository.save(event)
        def found = eventRepository.findById("launch").get()

        then:
        found.day() == event.day()
        found.start() == event.start()
        found.created() == event.created()
        found.zoned().toInstant() == event.zoned().toInstant()
        found.offset().toInstant() == event.offset().toInstant()
        found.legacy() == event.legacy()
        found.length() == event.length()
        found.ref() == event.ref()
        found.priority() == Event.Priority.HIGH
        found.price() == new BigDecimal("19.99")
        found.points() == new Event.Points(7)
        operations.executeQuery('MATCH (e:Event) RETURN e.points AS points', [:])[0].get("points") == 70L
    }

    void "read plain nodes"() {
        given:
        def entity = entityRegistry.getEntity(Person)
        def rows = operations.executeQuery("MATCH (n:Person) RETURN n ORDER BY n.name", [:])

        when:
        List<Person> viaSerde = rows.collect { serde.readNode(entity, it.get(0) as GraphNode, it) }
        List<Person> viaMapper = operations.executeQuery("MATCH (n:Person) RETURN n ORDER BY n.name", [:], Person)

        then:
        viaSerde*.name == ["Carrie-Anne Moss", "Keanu Reeves", "Lana Wachowski"]
        viaSerde*.name == viaMapper*.name
        viaSerde*.born == viaMapper*.born
        viaSerde*.id == viaMapper*.id
        viaSerde.every { it.id != null }
    }

    void "read nodes with fetched relationships and relationship properties"() {
        given:
        def entity = entityRegistry.getEntity(Movie)
        def method = context.getBeanDefinition(Neo4jMovieRepository).findMethod("findByTitle", String).get()
        String cypher = method.stringValue(Query, "rawQuery").orElseGet(() -> method.stringValue(Query).get())
        def rows = operations.executeQuery(cypher, [p0: "The Matrix"])

        when:
        Movie viaSerde = serde.readNode(entity, rows[0].get("n") as GraphNode, rows[0])
        Movie viaMapper = movieRepository.findByTitle("The Matrix").get()

        then: "properties, including the renamed tagline"
        viaSerde.title == "The Matrix"
        viaSerde.description == viaMapper.description
        viaSerde.description == "Welcome to the Real World"
        viaSerde.released == 1999
        viaSerde.votes == 42L

        and: "relationship entities with their properties, identifiers and target nodes"
        viaSerde.roles.size() == 2
        viaSerde.roles*.roles.flatten().sort() == ["Neo", "Trinity"]
        viaSerde.roles*.person*.name.sort() == viaMapper.roles*.person*.name.sort()
        viaSerde.roles*.id.sort() == viaMapper.roles*.id.sort()
        viaSerde.roles.every { it.id != null }

        and: "related nodes"
        viaSerde.directors*.name == ["Lana Wachowski"]
    }

    void "record nodes are read by the deserializer serde generated for them"() {
        given:
        def saved = topicRepository.save(new Topic(null, "Databases", [new Tag("graph"), new Tag("nosql")]))
        def method = context.getBeanDefinition(TopicRepository).findMethod("findByTitle", String).get()
        String cypher = method.stringValue(Query, "rawQuery").orElseGet(() -> method.stringValue(Query).get())
        def rows = operations.executeQuery(cypher, [p0: "Databases"])
        def registry = context.getBean(SerdeRegistry)

        expect: "serde generated the source of the deserializer of the record"
        registry.findDeserializer(Argument.of(Topic)).toString().contains("beanType=class io.micronaut.data.neo4j.features.SerdeTopicDeserializer")

        when:
        Topic topic = serde.readNode(entityRegistry.getEntity(Topic), rows[0].get("n") as GraphNode, rows[0])

        then: "a generated identifier and fetched relationships"
        topic.id() == saved.id()
        topic.title() == "Databases"
        topic.tags()*.name.sort() == ["graph", "nosql"]
    }

    void "relationships that were not fetched keep the value the class initializes"() {
        given:
        def entity = entityRegistry.getEntity(Movie)
        def rows = operations.executeQuery("MATCH (n:Movie) RETURN n", [:])

        when:
        Movie movie = serde.readNode(entity, rows[0].get(0) as GraphNode, rows[0])

        then:
        movie.title == "The Matrix"
        movie.roles != null
        movie.roles.isEmpty()
        movie.directors.isEmpty()
    }
}
