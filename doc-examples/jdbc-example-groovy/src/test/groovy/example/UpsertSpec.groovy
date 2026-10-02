package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class UpsertSpec extends Specification {

    @Inject
    FlightRepository flightRepository

    @Inject
    PassengerRepository passengerRepository

    void setup() {
        flightRepository.deleteAll()
        passengerRepository.deleteAll()
    }

    void 'test upsert'() {
        given:
        Flight flight = new Flight("MN100", "Athens", "London")

        when:
        flightRepository.upsert(flight)

        then:
        assertFlight("MN100", "Athens", "London")

        when:
        flight.destination = "Paris"
        flightRepository.upsert(flight)

        then:
        assertFlight("MN100", "Athens", "Paris")
        flightRepository.count() == 1
    }

    void 'test upsertAll'() {
        given:
        Flight flight1 = new Flight("MN101", "Athens", "London")
        Flight flight2 = new Flight("MN102", "Athens", "Paris")

        when:
        flightRepository.upsertAll([flight1, flight2])

        then:
        assertFlight("MN101", "Athens", "London")
        assertFlight("MN102", "Athens", "Paris")

        when:
        flight1.destination = "Rome"
        flight2.destination = "Madrid"
        flightRepository.upsertAll([flight1, flight2])

        then:
        assertFlight("MN101", "Athens", "Rome")
        assertFlight("MN102", "Athens", "Madrid")
        flightRepository.count() == 2
    }

    void 'test put'() {
        given:
        Flight flight = new Flight("MN103", "Athens", "London")

        when:
        flightRepository.put(flight)

        then:
        assertFlight("MN103", "Athens", "London")

        when:
        flight.destination = "Paris"
        flightRepository.put(flight)

        then:
        assertFlight("MN103", "Athens", "Paris")
        flightRepository.count() == 1
    }

    void 'test put all'() {
        given:
        Flight flight1 = new Flight("MN104", "Belgrade", "London")
        Flight flight2 = new Flight("MN105", "Belgrade", "Paris")

        when:
        flightRepository.put([flight1, flight2] as Iterable<Flight>)

        then:
        assertFlight("MN104", "Belgrade", "London")
        assertFlight("MN105", "Belgrade", "Paris")

        when:
        flight1.destination = "Rome"
        flight2.destination = "Madrid"
        flightRepository.put([flight1, flight2] as Iterable<Flight>)

        then:
        assertFlight("MN104", "Belgrade", "Rome")
        assertFlight("MN105", "Belgrade", "Madrid")
        flightRepository.count() == 2
    }

    void 'test upsert future'() {
        given:
        Flight flight = new Flight("MN106", "Athens", "Berlin")

        when:
        flightRepository.upsertFuture(flight).join()

        then:
        assertFlight("MN106", "Athens", "Berlin")

        when:
        flight.destination = "Amsterdam"
        flightRepository.upsertFuture(flight).join()

        then:
        assertFlight("MN106", "Athens", "Amsterdam")
        flightRepository.count() == 1
    }

    void 'test upsert all future'() {
        given:
        Flight flight1 = new Flight("MN107", "Athens", "Belgrade")
        Flight flight2 = new Flight("MN108", "Athens", "Zurich")

        when:
        flightRepository.upsertFuture([flight1, flight2] as Iterable<Flight>).join()

        then:
        assertFlight("MN107", "Athens", "Belgrade")
        assertFlight("MN108", "Athens", "Zurich")

        when:
        flight1.destination = "Lisbon"
        flight2.destination = "Copenhagen"
        flightRepository.upsertFuture([flight1, flight2] as Iterable<Flight>).join()

        then:
        assertFlight("MN107", "Athens", "Lisbon")
        assertFlight("MN108", "Athens", "Copenhagen")
        flightRepository.count() == 2
    }

    void 'test upsert by email'() {
        given:
        Passenger passenger = new Passenger("test@example.com", "testFN", "testLN")

        when:
        passengerRepository.upsertByEmail(passenger)

        then:
        assertPassenger("test@example.com", "testFN", "testLN")
        passenger.id != null

        when:
        passenger.firstName = "testFN2"
        passengerRepository.upsertByEmail(passenger)

        then:
        assertPassenger("test@example.com", "testFN2", "testLN")
        passengerRepository.count() == 1
    }

    void 'test upsert all by email'() {
        given:
        Passenger passenger1 = new Passenger("test1@example.com", "testFN1", "testLN1")
        Passenger passenger2 = new Passenger("test2@example.com", "testFN2", "testLN2")

        when:
        passengerRepository.upsertByEmail([passenger1, passenger2] as Iterable<Passenger>)

        then:
        assertPassenger("test1@example.com", "testFN1", "testLN1")
        assertPassenger("test2@example.com", "testFN2", "testLN2")
        passenger1.id != null
        passenger2.id != null

        when:
        passenger1.firstName = "testFN3"
        passenger2.lastName = "testLN4"
        passengerRepository.upsertByEmail([passenger1, passenger2] as Iterable<Passenger>)

        then:
        assertPassenger("test1@example.com", "testFN3", "testLN1")
        assertPassenger("test2@example.com", "testFN2", "testLN4")
        passengerRepository.count() == 2
    }

    private void assertFlight(String number, String origin, String destination) {
        Flight flight = flightRepository.findById(number).orElseThrow()
        assert flight.origin == origin
        assert flight.destination == destination
    }

    private void assertPassenger(String email, String firstName, String lastName) {
        Passenger passenger = passengerRepository.findAll().find { it.email == email }
        assert passenger != null
        assert passenger.firstName == firstName
        assert passenger.lastName == lastName
    }
}
