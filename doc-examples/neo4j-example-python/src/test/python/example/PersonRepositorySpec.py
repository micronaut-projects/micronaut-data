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

    @Test
    def testShortestPath(self):
        joe = Person("Joe", 1973)
        barney = Person("Barney", 1971, [joe])
        wilma = Person("Wilma", 1972, [joe])
        fred = self.personRepository.save(Person("Fred", 1970, [barney, wilma]))
        joe = self.personRepository.findByName("Joe")

        # tag::shortestPath[]
        path = self.personRepository.findShortestPath(fred, joe)
        for segment in path.segments():  # <1>
            print(f"{segment.start().name} -> {segment.end().name}")
        # end::shortestPath[]

        assert path.length() == 2
        assert path.start().name == "Fred"
        assert path.end().name == "Joe"

    @Test
    def testPathOptions(self):
        joe = Person("Joe", 1973)
        self.personRepository.save(Person("Fred", 1970, [Person("Barney", 1971, [joe]), Person("Wilma", 1972, [joe])]))

        assert self.personRepository.findShortestPathByFollowsBetweenName("Fred", "Joe", 3).end().name == "Joe"
        assert self.personRepository.existsPathByFollowsBetweenName("Joe", "Fred")
