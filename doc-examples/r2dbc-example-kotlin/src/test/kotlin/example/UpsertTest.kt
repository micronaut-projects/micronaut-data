package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpsertTest : AbstractTest(false) {

    @Inject
    lateinit var flightRepository: FlightRepository

    @BeforeEach
    fun cleanUp() {
        flightRepository.deleteAll()
    }

    @Test
    fun testUpsertMono() {
        val flight = Flight("MN100", "Athens", "London")

        flightRepository.upsertMono(flight).block()

        assertFlight("MN100", "Athens", "London")

        flight.destination = "Paris"

        flightRepository.upsertMono(flight).block()

        assertFlight("MN100", "Athens", "Paris")
        assertEquals(1, flightRepository.count())
    }

    @Test
    fun testUpsertFlux() {
        val flight1 = Flight("MN101", "Athens", "London")
        val flight2 = Flight("MN102", "Athens", "Paris")

        flightRepository.upsertFlux(listOf(flight1, flight2)).collectList().block()

        assertFlight("MN101", "Athens", "London")
        assertFlight("MN102", "Athens", "Paris")

        flight1.destination = "Rome"
        flight2.destination = "Madrid"

        flightRepository.upsertFlux(listOf(flight1, flight2)).collectList().block()

        assertFlight("MN101", "Athens", "Rome")
        assertFlight("MN102", "Athens", "Madrid")
        assertEquals(2, flightRepository.count())
    }

    private fun assertFlight(number: String, origin: String, destination: String) {
        val flight = flightRepository.findById(number)
        assertNotNull(flight)
        assertEquals(origin, flight!!.origin)
        assertEquals(destination, flight.destination)
    }
}
