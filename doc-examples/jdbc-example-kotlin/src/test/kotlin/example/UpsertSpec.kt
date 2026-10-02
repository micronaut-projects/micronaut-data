package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class UpsertSpec {

    @Inject
    lateinit var flightRepository: FlightRepository

    @Inject
    lateinit var passengerRepository: PassengerRepository

    @BeforeEach
    fun cleanUp() {
        flightRepository.deleteAll()
        passengerRepository.deleteAll()
    }

    @Test
    fun testUpsert() {
        val flight = Flight("MN100", "Athens", "London")

        flightRepository.upsert(flight)

        assertFlight("MN100", "Athens", "London")

        flight.destination = "Paris"

        flightRepository.upsert(flight)

        assertFlight("MN100", "Athens", "Paris")
        assertEquals(1, flightRepository.count())
    }

    @Test
    fun testUpsertAll() {
        val flight1 = Flight("MN101", "Athens", "London")
        val flight2 = Flight("MN102", "Athens", "Paris")

        flightRepository.upsertAll(listOf(flight1, flight2))

        assertFlight("MN101", "Athens", "London")
        assertFlight("MN102", "Athens", "Paris")

        flight1.destination = "Rome"
        flight2.destination = "Madrid"

        flightRepository.upsertAll(listOf(flight1, flight2))

        assertFlight("MN101", "Athens", "Rome")
        assertFlight("MN102", "Athens", "Madrid")
        assertEquals(2, flightRepository.count())
    }

    @Test
    fun testPut() {
        val flight = Flight("MN103", "Athens", "London")

        flightRepository.put(flight)

        assertFlight("MN103", "Athens", "London")

        flight.destination = "Paris"

        flightRepository.put(flight)

        assertFlight("MN103", "Athens", "Paris")
        assertEquals(1, flightRepository.count())
    }

    @Test
    fun testPutAll() {
        val flight1 = Flight("MN104", "Belgrade", "London")
        val flight2 = Flight("MN105", "Belgrade", "Paris")

        flightRepository.put(listOf(flight1, flight2))

        assertFlight("MN104", "Belgrade", "London")
        assertFlight("MN105", "Belgrade", "Paris")

        flight1.destination = "Rome"
        flight2.destination = "Madrid"

        flightRepository.put(listOf(flight1, flight2))

        assertFlight("MN104", "Belgrade", "Rome")
        assertFlight("MN105", "Belgrade", "Madrid")
        assertEquals(2, flightRepository.count())
    }

    @Test
    fun testUpsertSuspend() = runBlocking {
        val flight = Flight("MN106", "Athens", "Berlin")

        flightRepository.upsertSuspend(flight)

        assertFlight("MN106", "Athens", "Berlin")

        flight.destination = "Amsterdam"

        flightRepository.upsertSuspend(flight)

        assertFlight("MN106", "Athens", "Amsterdam")
        assertEquals(1, flightRepository.count())
    }

    @Test
    fun testUpsertAllSuspend() = runBlocking {
        val flight1 = Flight("MN107", "Athens", "Belgrade")
        val flight2 = Flight("MN108", "Athens", "Zurich")

        flightRepository.upsertSuspend(listOf(flight1, flight2))

        assertFlight("MN107", "Athens", "Belgrade")
        assertFlight("MN108", "Athens", "Zurich")

        flight1.destination = "Lisbon"
        flight2.destination = "Copenhagen"

        flightRepository.upsertSuspend(listOf(flight1, flight2))

        assertFlight("MN107", "Athens", "Lisbon")
        assertFlight("MN108", "Athens", "Copenhagen")
        assertEquals(2, flightRepository.count())
    }

    @Test
    fun testUpsertByEmail() {
        val passenger = Passenger(email = "test@example.com", firstName = "testFN", lastName = "testLN")

        passengerRepository.upsertByEmail(passenger)

        assertPassenger("test@example.com", "testFN", "testLN")
        assertNotNull(passenger.id)

        passenger.firstName = "testFN2"

        passengerRepository.upsertByEmail(passenger)

        assertPassenger("test@example.com", "testFN2", "testLN")
        assertEquals(1, passengerRepository.count())
    }

    @Test
    fun testUpsertAllByEmail() {
        val passenger1 = Passenger(email = "test1@example.com", firstName = "testFN1", lastName = "testLN1")
        val passenger2 = Passenger(email = "test2@example.com", firstName = "testFN2", lastName = "testLN2")

        passengerRepository.upsertByEmail(listOf(passenger1, passenger2))

        assertPassenger("test1@example.com", "testFN1", "testLN1")
        assertPassenger("test2@example.com", "testFN2", "testLN2")
        assertNotNull(passenger1.id)
        assertNotNull(passenger2.id)

        passenger1.firstName = "testFN3"
        passenger2.lastName = "testLN4"

        passengerRepository.upsertByEmail(listOf(passenger1, passenger2))

        assertPassenger("test1@example.com", "testFN3", "testLN1")
        assertPassenger("test2@example.com", "testFN2", "testLN4")
        assertEquals(2, passengerRepository.count())
    }

    @Test
    fun testUpsertByEmailSuspend() = runBlocking {
        val passenger = Passenger(email = "test3@example.com", firstName = "testFN", lastName = "testLN")

        val saved = passengerRepository.upsertByEmailSuspend(passenger)

        assertPassenger("test3@example.com", "testFN", "testLN")
        assertNotNull(saved.id)

        passenger.firstName = "testFN2"

        passengerRepository.upsertByEmailSuspend(passenger)

        assertPassenger("test3@example.com", "testFN2", "testLN")
        assertEquals(1, passengerRepository.count())
    }

    @Test
    fun testUpsertAllByEmailSuspend() = runBlocking {
        val passenger1 = Passenger(email = "test4@example.com", firstName = "testFN1", lastName = "testLN1")
        val passenger2 = Passenger(email = "test5@example.com", firstName = "testFN2", lastName = "testLN2")

        val saved = passengerRepository.upsertByEmailSuspend(listOf(passenger1, passenger2))

        assertEquals(2, saved.size)
        assertPassenger("test4@example.com", "testFN1", "testLN1")
        assertPassenger("test5@example.com", "testFN2", "testLN2")

        passenger1.firstName = "testFN3"
        passenger2.lastName = "testLN4"

        passengerRepository.upsertByEmailSuspend(listOf(passenger1, passenger2))

        assertPassenger("test4@example.com", "testFN3", "testLN1")
        assertPassenger("test5@example.com", "testFN2", "testLN4")
        assertEquals(2, passengerRepository.count())
    }

    private fun assertFlight(number: String, origin: String, destination: String) {
        val flight = flightRepository.findById(number)
        assertNotNull(flight)
        assertEquals(origin, flight!!.origin)
        assertEquals(destination, flight.destination)
    }

    private fun assertPassenger(email: String, firstName: String, lastName: String) {
        val passenger = passengerRepository.findAll().first { it.email == email }
        assertEquals(firstName, passenger.firstName)
        assertEquals(lastName, passenger.lastName)
    }
}
