package example

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MovieRepositorySpec : AbstractNeo4jTest() {

    @Inject
    lateinit var movieRepository: MovieRepository

    @Inject
    lateinit var personRepository: PersonRepository

    @Inject
    lateinit var movieSpecificationRepository: MovieSpecificationRepository

    @BeforeEach
    fun setup() {
        // tag::save[]
        val keanu = Person(name = "Keanu Reeves", born = 1964)
        val carrie = Person(name = "Carrie-Anne Moss", born = 1967)
        val lana = Person(name = "Lana Wachowski", born = 1965)

        val matrix = Movie(
            title = "The Matrix",
            description = "Welcome to the Real World",
            released = 1999,
            roles = listOf(Role(roles = listOf("Neo"), person = keanu), Role(roles = listOf("Trinity"), person = carrie)),
            directors = listOf(lana)
        )

        movieRepository.save(matrix) // <1>
        // end::save[]
        movieRepository.save(Movie("The Matrix Reloaded", "Free your mind", 2003,
            listOf(Role(roles = listOf("Neo"), person = keanu)), listOf(lana)))
        movieRepository.save(Movie("Speed", "Get ready for rush hour", 1994,
            listOf(Role(roles = listOf("Jack Traven"), person = keanu))))
    }

    @AfterEach
    fun cleanup() {
        movieRepository.deleteAll()
        personRepository.deleteAll()
    }

    @Test
    fun testFinders() {
        // tag::find[]
        val recent = movieRepository.findByReleasedGreaterThanOrderByReleased(1995)
        val older = movieRepository.countByReleasedLessThan(2000)
        val page = movieRepository.findByReleasedGreaterThan(1990, Pageable.from(0, 2, Sort.of(Sort.Order.asc("title"))))
        // end::find[]

        assertEquals(listOf("The Matrix", "The Matrix Reloaded"), recent.map { it.title })
        assertEquals(2, older)
        assertEquals(3, page.totalSize)
        assertEquals(listOf("Speed", "The Matrix"), page.content.map { it.title })
        assertEquals(2, movieRepository.findByTitleContainsIgnoreCase("matrix").size)
    }

    @Test
    fun testTraversal() {
        // tag::traverse[]
        val directedByLana = movieRepository.findByDirectorsName("Lana Wachowski")
        val withKeanu = movieRepository.findByRolesPersonName("Keanu Reeves")
        // end::traverse[]

        assertEquals(2, directedByLana.size)
        assertEquals(3, withKeanu.size)
    }

    @Test
    fun testJoin() {
        // tag::fetch[]
        val matrix = movieRepository.findByTitle("The Matrix")!!
        val neo = matrix.roles.first { it.person.name == "Keanu Reeves" }
        // end::fetch[]

        assertEquals(listOf("Neo"), neo.roles)
        assertEquals(listOf("Lana Wachowski"), matrix.directors.map { it.name })
    }

    @Test
    fun testNestedJoin() {
        val lilly = personRepository.save(Person(name = "Lilly Wachowski", born = 1967, follows = listOf(Person(name = "Lana Wachowski", born = 1965))))
        movieRepository.save(Movie("Bound", "In the end, it's all about the money", 1996, directors = listOf(lilly)))

        // tag::nestedFetch[]
        val fetched = movieRepository.findByReleased(1996).first()
        val followed = fetched.directors.first().follows.first() // <1>
        // end::nestedFetch[]

        assertEquals("Lana Wachowski", followed.name)
    }

    @Test
    fun testPathsToPeople() {
        val keanu = personRepository.findByName("Keanu Reeves")!!
        val lana = personRepository.findByName("Lana Wachowski")!!

        // tag::crossType[]
        val directed = movieRepository.findRelationshipsByDirectors("The Matrix", lana.id!!).first()
        val director = directed.from().name // <1>

        val path = movieRepository.findShortestPathToPersonBetweenTitleAndName("Speed", "Carrie-Anne Moss")!!
        val end = path.end() // <2>
        // end::crossType[]

        assertEquals("Lana Wachowski", director)
        assertEquals("The Matrix", directed.to().title)
        assertEquals(3, path.length())
        assertEquals("Carrie-Anne Moss", (end as Person).name)
        assertTrue(path.start() is Movie)
        assertEquals(1, movieRepository.findAllPathsToPerson("The Matrix", keanu.id!!).first().length())
    }

    @Test
    fun testRelationshipEntities() {
        val keanu = personRepository.findByName("Keanu Reeves")!!

        // tag::relationshipEntities[]
        val role = movieRepository.findRelationshipsByRoles("The Matrix", keanu.id!!).first()
        val characters = role.roles // <1>
        val actor = role.person // <2>
        // end::relationshipEntities[]

        assertEquals(listOf("Neo"), characters)
        assertEquals("Keanu Reeves", actor.name)
    }

    @Test
    fun testProjectionsAndQueries() {
        movieRepository.vote("Speed")

        assertEquals(listOf("Speed", "The Matrix", "The Matrix Reloaded"), movieRepository.findTitleByReleasedGreaterThanOrderByTitle(1990))
        assertEquals(2, movieRepository.findByReleasedBetween(1990, 2000).size)
        assertEquals(listOf("Speed", "The Matrix", "The Matrix Reloaded"), movieRepository.findMoviesActedInBy("Keanu Reeves").map { it.title })
    }

    @Test
    fun testSpecifications() {
        // tag::useSpecifications[]
        val recent = movieSpecificationRepository.findAll(MovieSpecifications.releasedAfter(1995)) // <1>
        val byLana = movieSpecificationRepository.count(MovieSpecifications.directedBy("Lana Wachowski")) // <2>
        // end::useSpecifications[]

        assertEquals(listOf("The Matrix", "The Matrix Reloaded"), recent.map { it.title }.sorted())
        assertEquals(2L, byLana)
    }

    @Test
    fun testGraphCriteria() {
        // tag::useGraphCriteria[]
        val withCast = movieSpecificationRepository.count(MovieSpecifications.withRoles()) // <1>
        val nearSpeed = movieSpecificationRepository.findAll(MovieSpecifications.connectedTo("Speed", 2)) // <2>
        // end::useGraphCriteria[]

        assertEquals(3L, withCast)
        assertEquals(listOf("The Matrix", "The Matrix Reloaded"), nearSpeed.map { it.title }.sorted())
    }
}
