package example;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static example.MovieSpecifications.connectedTo;
import static example.MovieSpecifications.withRoles;
import static example.MovieSpecifications.directedBy;
import static example.MovieSpecifications.releasedAfter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MovieRepositorySpec extends AbstractNeo4jTest {

    @Inject
    MovieRepository movieRepository;

    @Inject
    PersonRepository personRepository;

    @Inject
    MovieSpecificationRepository movieSpecificationRepository;

    @BeforeEach
    void setup() {
        // tag::save[]
        Person keanu = new Person("Keanu Reeves", 1964);
        Person carrie = new Person("Carrie-Anne Moss", 1967);
        Person lana = new Person("Lana Wachowski", 1965);

        Movie matrix = new Movie("The Matrix", "Welcome to the Real World", 1999);
        matrix.setRoles(List.of(
            new Role(List.of("Neo"), keanu),
            new Role(List.of("Trinity"), carrie)
        ));
        matrix.setDirectors(List.of(lana));

        movieRepository.save(matrix); // <1>
        // end::save[]
        Movie reloaded = new Movie("The Matrix Reloaded", "Free your mind", 2003);
        reloaded.setRoles(List.of(new Role(List.of("Neo"), keanu)));
        reloaded.setDirectors(List.of(lana));
        movieRepository.save(reloaded);
        Movie speed = new Movie("Speed", "Get ready for rush hour", 1994);
        speed.setRoles(List.of(new Role(List.of("Jack Traven"), keanu)));
        movieRepository.save(speed);
    }

    @AfterEach
    void cleanup() {
        movieRepository.deleteAll();
        personRepository.deleteAll();
    }

    @Test
    void testFinders() {
        // tag::find[]
        List<Movie> recent = movieRepository.findByReleasedGreaterThanOrderByReleased(1995);
        long older = movieRepository.countByReleasedLessThan(2000);
        Page<Movie> page = movieRepository.findByReleasedGreaterThan(1990, Pageable.from(0, 2, Sort.of(Sort.Order.asc("title"))));
        // end::find[]

        assertEquals(List.of("The Matrix", "The Matrix Reloaded"), recent.stream().map(Movie::getTitle).toList());
        assertEquals(2, older);
        assertEquals(3, page.getTotalSize());
        assertEquals(List.of("Speed", "The Matrix"), page.getContent().stream().map(Movie::getTitle).toList());
        assertEquals(2, movieRepository.findByTitleContainsIgnoreCase("matrix").size());
    }

    @Test
    void testTraversal() {
        // tag::traverse[]
        List<Movie> directedByLana = movieRepository.findByDirectorsName("Lana Wachowski");
        List<Movie> withKeanu = movieRepository.findByRolesPersonName("Keanu Reeves");
        // end::traverse[]

        assertEquals(2, directedByLana.size());
        assertEquals(3, withKeanu.size());
    }

    @Test
    void testJoin() {
        // tag::fetch[]
        Movie matrix = movieRepository.findByTitle("The Matrix").orElseThrow();
        Role neo = matrix.getRoles().stream()
            .filter(role -> role.getPerson().getName().equals("Keanu Reeves"))
            .findFirst()
            .orElseThrow();
        // end::fetch[]

        assertEquals(List.of("Neo"), neo.getRoles());
        assertEquals("Lana Wachowski", matrix.getDirectors().get(0).getName());
    }

    @Test
    void testNestedJoin() {
        Person lilly = new Person("Lilly Wachowski", 1967);
        lilly.getFollows().add(new Person("Lana Wachowski", 1965));
        personRepository.save(lilly);
        Movie bound = new Movie("Bound", "In the end, it's all about the money", 1996);
        bound.setDirectors(List.of(lilly));
        movieRepository.save(bound);

        // tag::nestedFetch[]
        Movie fetched = movieRepository.findByReleased(1996).get(0);
        Person followed = fetched.getDirectors().get(0).getFollows().get(0); // <1>
        // end::nestedFetch[]

        assertEquals("Lana Wachowski", followed.getName());
    }

    @Test
    void testProjections() {
        assertEquals(List.of("Speed", "The Matrix", "The Matrix Reloaded"), movieRepository.findTitleByReleasedGreaterThanOrderByTitle(1990));
        assertEquals(2, movieRepository.findByReleasedBetween(1990, 2000).size());
    }

    @Test
    void testExplicitQueries() {
        movieRepository.vote("Speed");
        List<Movie> movies = movieRepository.findMoviesActedInBy("Keanu Reeves");

        assertEquals(List.of("Speed", "The Matrix", "The Matrix Reloaded"), movies.stream().map(Movie::getTitle).toList());
        assertTrue(movieRepository.existsById("Speed"));
    }

    @Test
    void testSpecifications() {
        // tag::useSpecifications[]
        List<Movie> recent = movieSpecificationRepository.findAll(releasedAfter(1995)); // <1>
        long byLana = movieSpecificationRepository.count(directedBy("Lana Wachowski")); // <2>
        // end::useSpecifications[]

        assertEquals(List.of("The Matrix", "The Matrix Reloaded"), recent.stream().map(Movie::getTitle).sorted().toList());
        assertEquals(2, byLana);
    }

    @Test
    void testGraphCriteria() {
        // tag::useGraphCriteria[]
        long withCast = movieSpecificationRepository.count(withRoles()); // <1>
        List<Movie> nearSpeed = movieSpecificationRepository.findAll(connectedTo("Speed", 2)); // <2>
        // end::useGraphCriteria[]

        assertEquals(3, withCast);
        assertEquals(List.of("The Matrix", "The Matrix Reloaded"), nearSpeed.stream().map(Movie::getTitle).sorted().toList());
    }
}
