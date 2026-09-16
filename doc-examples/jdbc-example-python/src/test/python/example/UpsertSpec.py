from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test

from example.Flight import Flight
from example.FlightRepository import FlightRepository
from example.Passenger import Passenger
from example.PassengerRepository import PassengerRepository


@MicronautTest
class UpsertSpec:

    flightRepository: Annotated[FlightRepository, Inject]
    passengerRepository: Annotated[PassengerRepository, Inject]

    @BeforeEach
    def cleanUp(self):
        self.flightRepository.deleteAll()
        self.passengerRepository.deleteAll()

    @Test
    def testUpsert(self):
        flight = Flight("MN100", "Athens", "London")
        self.flightRepository.upsert(flight)
        self.assert_flight("MN100", "Athens", "London")
        flight.destination = "Paris"
        self.flightRepository.upsert(flight)
        self.assert_flight("MN100", "Athens", "Paris")
        assert self.flightRepository.count() == 1

    @Test
    def testUpsertAll(self):
        flight1 = Flight("MN101", "Athens", "London")
        flight2 = Flight("MN102", "Athens", "Paris")
        self.flightRepository.upsertAll([flight1, flight2])
        self.assert_flight("MN101", "Athens", "London")
        self.assert_flight("MN102", "Athens", "Paris")
        flight1.destination = "Rome"
        flight2.destination = "Madrid"
        self.flightRepository.upsertAll([flight1, flight2])
        self.assert_flight("MN101", "Athens", "Rome")
        self.assert_flight("MN102", "Athens", "Madrid")
        assert self.flightRepository.count() == 2

    @Test
    def testPut(self):
        flight = Flight("MN103", "Athens", "London")
        self.flightRepository.put(flight)
        self.assert_flight("MN103", "Athens", "London")
        flight.destination = "Paris"
        self.flightRepository.put(flight)
        self.assert_flight("MN103", "Athens", "Paris")
        assert self.flightRepository.count() == 1

    @Test
    def testPutAll(self):
        flight1 = Flight("MN104", "Belgrade", "London")
        flight2 = Flight("MN105", "Belgrade", "Paris")
        self.flightRepository.putAll([flight1, flight2])
        self.assert_flight("MN104", "Belgrade", "London")
        self.assert_flight("MN105", "Belgrade", "Paris")
        flight1.destination = "Rome"
        flight2.destination = "Madrid"
        self.flightRepository.putAll([flight1, flight2])
        self.assert_flight("MN104", "Belgrade", "Rome")
        self.assert_flight("MN105", "Belgrade", "Madrid")
        assert self.flightRepository.count() == 2

    @Test
    def testUpsertFuture(self):
        flight = Flight("MN106", "Athens", "Berlin")
        self.flightRepository.upsertFuture(flight).join()
        self.assert_flight("MN106", "Athens", "Berlin")
        flight.destination = "Amsterdam"
        self.flightRepository.upsertFuture(flight).join()
        self.assert_flight("MN106", "Athens", "Amsterdam")
        assert self.flightRepository.count() == 1

    @Test
    def testUpsertAllFuture(self):
        flight1 = Flight("MN107", "Athens", "Belgrade")
        flight2 = Flight("MN108", "Athens", "Zurich")
        self.flightRepository.upsertAllFuture([flight1, flight2]).join()
        self.assert_flight("MN107", "Athens", "Belgrade")
        self.assert_flight("MN108", "Athens", "Zurich")
        flight1.destination = "Lisbon"
        flight2.destination = "Copenhagen"
        self.flightRepository.upsertAllFuture([flight1, flight2]).join()
        self.assert_flight("MN107", "Athens", "Lisbon")
        self.assert_flight("MN108", "Athens", "Copenhagen")
        assert self.flightRepository.count() == 2

    @Test
    def testUpsertByEmail(self):
        passenger = Passenger("test@example.com", "testFN", "testLN")
        passenger = self.passengerRepository.upsertByEmail(passenger)
        self.assert_passenger("test@example.com", "testFN", "testLN")
        assert passenger.id is not None
        passenger.firstName = "testFN2"
        self.passengerRepository.upsertByEmail(passenger)
        self.assert_passenger("test@example.com", "testFN2", "testLN")
        assert self.passengerRepository.count() == 1

    @Test
    def testUpsertAllByEmail(self):
        passenger1 = Passenger("test1@example.com", "testFN1", "testLN1")
        passenger2 = Passenger("test2@example.com", "testFN2", "testLN2")
        passenger1, passenger2 = self.passengerRepository.upsertAllByEmail([passenger1, passenger2])
        self.assert_passenger("test1@example.com", "testFN1", "testLN1")
        self.assert_passenger("test2@example.com", "testFN2", "testLN2")
        assert passenger1.id is not None
        assert passenger2.id is not None
        passenger1.firstName = "testFN3"
        passenger2.lastName = "testLN4"
        self.passengerRepository.upsertAllByEmail([passenger1, passenger2])
        self.assert_passenger("test1@example.com", "testFN3", "testLN1")
        self.assert_passenger("test2@example.com", "testFN2", "testLN4")
        assert self.passengerRepository.count() == 2

    def assert_flight(self, number: str, origin: str, destination: str):
        flight = self.flightRepository.findById(number).orElseThrow()
        assert flight.origin == origin
        assert flight.destination == destination

    def assert_passenger(self, email: str, first_name: str, last_name: str):
        passenger = next(candidate for candidate in self.passengerRepository.findAll() if candidate.email == email)
        assert passenger.firstName == first_name
        assert passenger.lastName == last_name
