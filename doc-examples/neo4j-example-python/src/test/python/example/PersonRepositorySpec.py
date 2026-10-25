from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.graph.annotation import Direction
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
        joe = self.personRepository.save(Person("Joe", 1973))
        fred = self.personRepository.save(Person("Fred", 1970, [Person("Barney", 1971, [joe]), Person("Wilma", 1972, [joe])]))

        # tag::shortestPath[]
        path = self.personRepository.findShortestPath(fred, joe)
        for segment in path.segments():  # <1>
            print(f"{segment.start().name} -> {segment.end().name}")
        # end::shortestPath[]

        assert path.length() == 2
        assert path.start().name == "Fred"
        assert path.end().name == "Joe"

    @Test
    def testPathDirection(self):
        joe = self.personRepository.save(Person("Joe", 1973))
        fred = self.personRepository.save(Person("Fred", 1970, [Person("Barney", 1971, [joe]), Person("Wilma", 1972, [joe])]))

        # tag::pathDirection[]
        follows = self.personRepository.hasPathByFollows(fred, joe, Direction.OUTGOING)
        followed_by = self.personRepository.hasPathByFollows(joe, fred, Direction.INCOMING)
        # end::pathDirection[]

        assert follows
        assert followed_by
        assert not self.personRepository.hasPathByFollows(joe, fred, Direction.OUTGOING)
        assert self.personRepository.hasPathByFollows(joe, fred, Direction.UNDIRECTED)

    @Test
    def testPathOptions(self):
        joe = self.personRepository.save(Person("Joe", 1973))
        self.personRepository.save(Person("Fred", 1970, [Person("Barney", 1971, [joe]), Person("Wilma", 1972, [joe])]))

        assert self.personRepository.findShortestPathByFollowsBetweenName("Fred", "Joe", 3).end().name == "Joe"
        assert self.personRepository.existsPathByFollowsBetweenName("Joe", "Fred")
