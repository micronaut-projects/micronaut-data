from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Person import Person
from example.PersonRepository import PersonRepository


@MicronautTest(transactional=False)
class PersonRepositorySpec:

    personRepository: Annotated[PersonRepository, Inject]

    @AfterEach
    def afterEach(self):
        self.personRepository.deleteAll()

    @Test
    def testLabels(self):
        # tag::labels[]
        keanu = Person("Keanu Reeves", 1964)
        keanu.labels.append("Actor")  # <1>
        self.personRepository.save(keanu)

        labels = self.personRepository.findByName("Keanu Reeves").labels  # <2>
        # end::labels[]

        assert list(labels) == ["Actor"]
