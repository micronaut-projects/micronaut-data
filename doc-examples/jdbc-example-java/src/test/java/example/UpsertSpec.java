package example;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class UpsertSpec {

    @Inject
    FlightRepository flightRepository;

    @Inject
    PassengerRepository passengerRepository;

    @BeforeEach
    void cleanUp() {
        flightRepository.deleteAll();
        passengerRepository.deleteAll();
    }

    @Test
    void testUpsert() {
        Flight flight = new Flight("MN100", "Athens", "London");

        flightRepository.upsert(flight);

        assertFlight("MN100", "Athens", "London");

        flight.setDestination("Paris");

        flightRepository.upsert(flight);

        assertFlight("MN100", "Athens", "Paris");
        assertEquals(1, flightRepository.count());
    }

    @Test
    void testUpsertAll() {
        Flight flight1 = new Flight("MN101", "Athens", "London");
        Flight flight2 = new Flight("MN102", "Athens", "Paris");

        flightRepository.upsertAll(List.of(flight1, flight2));

        assertFlight("MN101", "Athens", "London");
        assertFlight("MN102", "Athens", "Paris");

        flight1.setDestination("Rome");
        flight2.setDestination("Madrid");

        flightRepository.upsertAll(List.of(flight1, flight2));

        assertFlight("MN101", "Athens", "Rome");
        assertFlight("MN102", "Athens", "Madrid");
        assertEquals(2, flightRepository.count());
    }

    @Test
    void testPut() {
        Flight flight = new Flight("MN103", "Athens", "London");

        flightRepository.put(flight);

        assertFlight("MN103", "Athens", "London");

        flight.setDestination("Paris");

        flightRepository.put(flight);

        assertFlight("MN103", "Athens", "Paris");
        assertEquals(1, flightRepository.count());
    }

    @Test
    void testPutAll() {
        Flight flight1 = new Flight("MN104", "Belgrade", "London");
        Flight flight2 = new Flight("MN105", "Belgrade", "Paris");

        flightRepository.put(List.of(flight1, flight2));

        assertFlight("MN104", "Belgrade", "London");
        assertFlight("MN105", "Belgrade", "Paris");

        flight1.setDestination("Rome");
        flight2.setDestination("Madrid");

        flightRepository.put(List.of(flight1, flight2));

        assertFlight("MN104", "Belgrade", "Rome");
        assertFlight("MN105", "Belgrade", "Madrid");
        assertEquals(2, flightRepository.count());
    }

    @Test
    void testUpsertFuture() {
        Flight flight = new Flight("MN106", "Athens", "Berlin");

        flightRepository.upsertFuture(flight).join();

        assertFlight("MN106", "Athens", "Berlin");

        flight.setDestination("Amsterdam");

        flightRepository.upsertFuture(flight).join();

        assertFlight("MN106", "Athens", "Amsterdam");
        assertEquals(1, flightRepository.count());
    }

    @Test
    void testUpsertAllFuture() {
        Flight flight1 = new Flight("MN107", "Athens", "Belgrade");
        Flight flight2 = new Flight("MN108", "Athens", "Zurich");

        flightRepository.upsertFuture(List.of(flight1, flight2)).join();

        assertFlight("MN107", "Athens", "Belgrade");
        assertFlight("MN108", "Athens", "Zurich");

        flight1.setDestination("Lisbon");
        flight2.setDestination("Copenhagen");

        flightRepository.upsertFuture(List.of(flight1, flight2)).join();

        assertFlight("MN107", "Athens", "Lisbon");
        assertFlight("MN108", "Athens", "Copenhagen");
        assertEquals(2, flightRepository.count());
    }

    @Test
    void testUpsertByEmail() {
        Passenger passenger = new Passenger("test@example.com", "testFN", "testLN");

        passengerRepository.upsertByEmail(passenger);

        assertPassenger("test@example.com", "testFN", "testLN");
        assertNotNull(passenger.getId());

        passenger.setFirstName("testFN2");

        passengerRepository.upsertByEmail(passenger);

        assertPassenger("test@example.com", "testFN2", "testLN");
        assertEquals(1, passengerRepository.count());
    }

    @Test
    void testUpsertAllByEmail() {
        Passenger passenger1 = new Passenger("test1@example.com", "testFN1", "testLN1");
        Passenger passenger2 = new Passenger("test2@example.com", "testFN2", "testLN2");

        passengerRepository.upsertByEmail(List.of(passenger1, passenger2));

        assertPassenger("test1@example.com", "testFN1", "testLN1");
        assertPassenger("test2@example.com", "testFN2", "testLN2");
        assertNotNull(passenger1.getId());
        assertNotNull(passenger2.getId());

        passenger1.setFirstName("testFN3");
        passenger2.setLastName("testLN4");

        passengerRepository.upsertByEmail(List.of(passenger1, passenger2));

        assertPassenger("test1@example.com", "testFN3", "testLN1");
        assertPassenger("test2@example.com", "testFN2", "testLN4");
        assertEquals(2, passengerRepository.count());
    }

    private void assertFlight(String number, String origin, String destination) {
        Flight flight = flightRepository.findById(number).orElseThrow();
        assertEquals(origin, flight.getOrigin());
        assertEquals(destination, flight.getDestination());
    }

    private void assertPassenger(String email, String firstName, String lastName) {
        Passenger passenger = passengerRepository.findAll().stream()
            .filter(candidate -> candidate.getEmail().equals(email))
            .findFirst()
            .orElseThrow();
        assertEquals(firstName, passenger.getFirstName());
        assertEquals(lastName, passenger.getLastName());
    }
}
