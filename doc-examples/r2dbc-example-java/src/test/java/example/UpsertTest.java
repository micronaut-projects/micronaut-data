package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
public class UpsertTest {

    @Inject
    FlightRepository flightRepository;

    @BeforeEach
    void cleanUp() {
        flightRepository.deleteAll();
    }

    @Test
    void testUpsertMono() {
        Flight flight = new Flight("MN100", "Athens", "London");

        flightRepository.upsertMono(flight).block();

        assertFlight("MN100", "Athens", "London");

        flight.setDestination("Paris");

        flightRepository.upsertMono(flight).block();

        assertFlight("MN100", "Athens", "Paris");
        assertEquals(1, flightRepository.count());
    }

    @Test
    void testUpsertFlux() {
        Flight flight1 = new Flight("MN101", "Athens", "London");
        Flight flight2 = new Flight("MN102", "Athens", "Paris");

        flightRepository.upsertFlux(List.of(flight1, flight2)).collectList().block();

        assertFlight("MN101", "Athens", "London");
        assertFlight("MN102", "Athens", "Paris");

        flight1.setDestination("Rome");
        flight2.setDestination("Madrid");

        flightRepository.upsertFlux(List.of(flight1, flight2)).collectList().block();

        assertFlight("MN101", "Athens", "Rome");
        assertFlight("MN102", "Athens", "Madrid");
        assertEquals(2, flightRepository.count());
    }

    private void assertFlight(String number, String origin, String destination) {
        Flight flight = flightRepository.findById(number).orElseThrow();
        assertEquals(origin, flight.getOrigin());
        assertEquals(destination, flight.getDestination());
    }
}
