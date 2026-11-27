package example

import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsSpec : AbstractNeo4jTest() {

    @Inject
    lateinit var operations: Neo4jRepositoryOperations

    @Inject
    lateinit var personRepository: PersonRepository

    @AfterEach
    fun cleanup() {
        personRepository.deleteAll()
    }

    @Test
    fun testExecuteQuery() {
        personRepository.save(Person(name = "Keanu Reeves", born = 1964))

        // tag::executeQuery[]
        val rows = operations.executeQuery("MATCH (p:Person) RETURN p.name AS name", mapOf()) // <1>
        val people = operations.executeQuery("MATCH (p:Person) RETURN p", mapOf(), Person::class.java) // <2>
        // end::executeQuery[]

        assertEquals("Keanu Reeves", rows[0].get("name"))
        assertEquals("Keanu Reeves", people[0].name)
    }

    @Test
    fun testEncoder() {
        // tag::encode[]
        val price = Money(BigDecimal("9.99"), "EUR")
        val rows = operations.executeQuery("RETURN \$price.currency AS currency", mapOf("price" to price)) // <1>
        // end::encode[]

        assertEquals("EUR", rows[0].get("currency"))
    }
}
