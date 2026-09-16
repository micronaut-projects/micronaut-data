from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test
from reactor.core.publisher import Flux, Mono

from example.Flight import Flight
from example.FlightRepository import FlightRepository


@MicronautTest(transactional=False)
class UpsertTest:

    flightRepository: Annotated[FlightRepository, Inject]

    @BeforeEach
    def cleanUp(self):
        self.flightRepository.deleteAll()

    @Test
    def testUpsertMono(self):
        flight = Flight("MN100", "Athens", "London")
        Mono.from_(self.flightRepository.upsertMono(flight)).block()
        self.assert_flight("MN100", "Athens", "London")
        flight.destination = "Paris"
        Mono.from_(self.flightRepository.upsertMono(flight)).block()
        self.assert_flight("MN100", "Athens", "Paris")
        assert self.flightRepository.count() == 1

    @Test
    def testUpsertFlux(self):
        flight1 = Flight("MN101", "Athens", "London")
        flight2 = Flight("MN102", "Athens", "Paris")
        Flux.from_(self.flightRepository.upsertFlux([flight1, flight2])).collectList().block()
        self.assert_flight("MN101", "Athens", "London")
        self.assert_flight("MN102", "Athens", "Paris")
        flight1.destination = "Rome"
        flight2.destination = "Madrid"
        Flux.from_(self.flightRepository.upsertFlux([flight1, flight2])).collectList().block()
        self.assert_flight("MN101", "Athens", "Rome")
        self.assert_flight("MN102", "Athens", "Madrid")
        assert self.flightRepository.count() == 2

    def assert_flight(self, number: str, origin: str, destination: str):
        flight = self.flightRepository.findById(number).orElseThrow()
        assert flight.origin == origin
        assert flight.destination == destination
