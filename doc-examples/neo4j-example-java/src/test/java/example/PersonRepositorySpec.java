package example;

import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.graph.model.PathSegment;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonRepositorySpec extends AbstractNeo4jTest {

    @Inject
    PersonRepository personRepository;

    Person fred;
    Person joe;

    @BeforeEach
    void setup() {
        fred = new Person("Fred", 1970);
        Person barney = new Person("Barney", 1971);
        Person wilma = new Person("Wilma", 1972);
        joe = new Person("Joe", 1973);
        fred.setFollows(List.of(barney, wilma));
        barney.setFollows(List.of(joe));
        wilma.setFollows(List.of(joe));
        personRepository.save(fred);
    }

    @AfterEach
    void cleanup() {
        personRepository.deleteAll();
    }

    @Test
    void testShortestPath() {
        // tag::shortestPath[]
        GraphPath<Person> path = personRepository.findShortestPath(fred, joe).orElseThrow();
        for (PathSegment<Person> segment : path.segments()) { // <1>
            System.out.println(segment.start().getName() + " -> " + segment.end().getName());
        }
        // end::shortestPath[]

        assertEquals(2, path.length());
        assertEquals("Fred", path.start().getName());
        assertEquals("Joe", path.end().getName());
        assertFalse(personRepository.findShortestPathByFollows(fred.getId(), joe.getId(), 1).isPresent());
    }

    @Test
    void testAllPaths() {
        List<GraphPath<Person>> paths = personRepository.findAllPathsByFollows(fred, joe);

        assertEquals(2, paths.size());
        assertTrue(personRepository.existsPathByFollows(fred, joe));
        assertFalse(personRepository.existsPathByFollows(joe, fred));
    }

    @Test
    void testRelationships() {
        Person barney = personRepository.findByName("Barney").orElseThrow();

        assertEquals("FOLLOWS", personRepository.findRelationships(fred, barney).get(0).type());
        assertEquals(4, personRepository.countRelationshipsByFollows());
    }

    @Test
    void testLabels() {
        // tag::labels[]
        Person keanu = new Person("Keanu Reeves", 1964);
        keanu.getLabels().add("Actor"); // <1>
        personRepository.save(keanu);

        Set<String> labels = personRepository.findByName("Keanu Reeves").orElseThrow().getLabels(); // <2>
        // end::labels[]

        assertEquals(Set.of("Actor"), labels);
    }

    @Test
    void testPathDirection() {
        // tag::pathDirection[]
        boolean follows = personRepository.hasPathByFollows(fred, joe, Direction.OUTGOING);
        boolean followedBy = personRepository.hasPathByFollows(joe, fred, Direction.INCOMING);
        // end::pathDirection[]

        assertTrue(follows);
        assertTrue(followedBy);
        assertFalse(personRepository.hasPathByFollows(joe, fred, Direction.OUTGOING));
        assertTrue(personRepository.hasPathByFollows(joe, fred, Direction.UNDIRECTED));
    }

    @Test
    void testPathOptions() {
        assertEquals("Joe", personRepository.findShortestPathByFollowsBetweenName("Fred", "Joe", 3).orElseThrow().end().getName());
        assertTrue(personRepository.existsPathByFollowsBetweenName("Joe", "Fred"));
    }
}
