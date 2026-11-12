from typing import Annotated

from jakarta.inject import Inject
from java.math import BigDecimal
from micronaut.data.neo4j.operations import Neo4jRepositoryOperations
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Money import Money
from example.Person import Person
from example.PersonRepository import PersonRepository


@MicronautTest(transactional=False)
class OperationsSpec:

    operations: Annotated[Neo4jRepositoryOperations, Inject]
    personRepository: Annotated[PersonRepository, Inject]

    @AfterEach
    def afterEach(self):
        self.personRepository.deleteAll()

    @Test
    def testExecuteQuery(self):
        self.personRepository.save(Person("Keanu Reeves", 1964))

        # tag::executeQuery[]
        rows = self.operations.executeQuery("MATCH (p:Person) RETURN p.name AS name", {})  # <1>
        people = self.operations.executeQuery("MATCH (p:Person) RETURN p", {}, Person)  # <2>
        # end::executeQuery[]

        assert rows[0].get("name") == "Keanu Reeves"
        assert people[0].name == "Keanu Reeves"

    @Test
    def testEncoder(self):
        # tag::encode[]
        price = Money(BigDecimal("9.99"), "EUR")
        rows = self.operations.executeQuery("RETURN $price.currency AS currency", {"price": price})  # <1>
        # end::encode[]

        assert rows[0].get("currency") == "EUR"
