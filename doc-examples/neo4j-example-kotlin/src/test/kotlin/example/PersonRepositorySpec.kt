package example

import io.micronaut.data.graph.annotation.Direction
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonRepositorySpec : AbstractNeo4jTest() {

    @Inject
    lateinit var personRepository: PersonRepository

    lateinit var fred: Person
    lateinit var joe: Person

    @BeforeEach
    fun setup() {
        joe = Person(name = "Joe", born = 1973)
        val barney = Person(name = "Barney", born = 1971, follows = listOf(joe))
        val wilma = Person(name = "Wilma", born = 1972, follows = listOf(joe))
        fred = personRepository.save(Person(name = "Fred", born = 1970, follows = listOf(barney, wilma)))
        joe = personRepository.findByName("Joe")!!
    }

    @AfterEach
    fun cleanup() {
        personRepository.deleteAll()
    }

    @Test
    fun testPaths() {
        // tag::shortestPath[]
        val path = personRepository.findShortestPath(fred, joe)!!
        path.segments().forEach { segment -> // <1>
            println("${segment.start().name} -> ${segment.end().name}")
        }
        // end::shortestPath[]

        assertEquals(2, path.length())
        assertEquals("Fred", path.start().name)
        assertEquals("Joe", path.end().name)
        assertNull(personRepository.findShortestPathByFollows(fred.id!!, joe.id!!, 1))
        assertEquals(2, personRepository.findAllPathsByFollows(fred, joe).size)
        assertTrue(personRepository.existsPathByFollows(fred, joe))
        assertFalse(personRepository.existsPathByFollows(joe, fred))
    }

    @Test
    fun testRelationships() {
        val barney = personRepository.findByName("Barney")!!

        assertEquals(listOf("FOLLOWS"), personRepository.findRelationships(fred, barney).map { it.type() })
        assertEquals(4, personRepository.countRelationshipsByFollows())
    }

    @Test
    fun testLabels() {
        // tag::labels[]
        val keanu = Person(name = "Keanu Reeves", born = 1964, labels = setOf("Actor")) // <1>
        personRepository.save(keanu)

        val labels = personRepository.findByName("Keanu Reeves")!!.labels // <2>
        // end::labels[]

        assertEquals(setOf("Actor"), labels)
    }

    @Test
    fun testPathDirection() {
        // tag::pathDirection[]
        val follows = personRepository.hasPathByFollows(fred, joe, Direction.OUTGOING)
        val followedBy = personRepository.hasPathByFollows(joe, fred, Direction.INCOMING)
        // end::pathDirection[]

        assertTrue(follows)
        assertTrue(followedBy)
        assertFalse(personRepository.hasPathByFollows(joe, fred, Direction.OUTGOING))
        assertTrue(personRepository.hasPathByFollows(joe, fred, Direction.UNDIRECTED))

    @Test
    fun testCascade() {
        // tag::cascade[]
        val keanu = personRepository.save(Person(name = "Keanu Reeves", born = 1964)) // <1>
        val carrie = Person(name = "Carrie-Anne Moss", born = 1967, knows = listOf(keanu)) // <2>
        personRepository.save(carrie)
        // end::cascade[]
        assertEquals(listOf("Carrie-Anne Moss"), personRepository.findByKnowsName("Keanu Reeves").map { it.name })
        assertEquals(1964, personRepository.findByName("Keanu Reeves")!!.born)
    }

    @Test
    fun testPathOptions() {
        assertEquals("Joe", personRepository.findShortestPathByFollowsBetweenName("Fred", "Joe", 3)!!.end().name)
        assertTrue(personRepository.existsPathByFollowsBetweenName("Joe", "Fred"))
    }
}
