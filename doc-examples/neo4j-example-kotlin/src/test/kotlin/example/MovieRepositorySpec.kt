package example

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
}
