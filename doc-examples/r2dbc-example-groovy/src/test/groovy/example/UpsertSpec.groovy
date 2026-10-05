package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class UpsertSpec extends Specification {

    @Inject
    FlightRepository flightRepository

    void setup() {
        flightRepository.deleteAll()
    }

    void 'test upsert mono'() {
        given:
        Flight flight = new Flight("MN100", "Athens", "London")

        when:
        flightRepository.upsertMono(flight).block()

        then:
        assertFlight("MN100", "Athens", "London")

        when:
        flight.destination = "Paris"
        flightRepository.upsertMono(flight).block()

        then:
        assertFlight("MN100", "Athens", "Paris")
        flightRepository.count() == 1
    }

    void 'test upsert flux'() {
        given:
        Flight flight1 = new Flight("MN101", "Athens", "London")
        Flight flight2 = new Flight("MN102", "Athens", "Paris")

        when:
        flightRepository.upsertFlux([flight1, flight2]).collectList().block()

        then:
        assertFlight("MN101", "Athens", "London")
        assertFlight("MN102", "Athens", "Paris")

        when:
        flight1.destination = "Rome"
        flight2.destination = "Madrid"
        flightRepository.upsertFlux([flight1, flight2]).collectList().block()

        then:
        assertFlight("MN101", "Athens", "Rome")
        assertFlight("MN102", "Athens", "Madrid")
        flightRepository.count() == 2
    }

    private void assertFlight(String number, String origin, String destination) {
        Flight flight = flightRepository.findById(number).orElseThrow()
        assert flight.origin == origin
        assert flight.destination == destination
    }
}
