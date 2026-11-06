from typing import Annotated

from micronaut.data.model import Pageable, Sort
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, BeforeEach, Test

from example.Movie import Movie
from example.MovieRepository import MovieRepository
from example.MovieSpecificationRepository import MovieSpecificationRepository
from example.MovieSpecifications import directed_by, released_after
from example.Person import Person
from example.PersonRepository import PersonRepository
from example.Role import Role


@MicronautTest(transactional=False)
class MovieRepositorySpec:

    movieRepository: Annotated[MovieRepository, Inject]
    personRepository: Annotated[PersonRepository, Inject]
    movieSpecificationRepository: Annotated[MovieSpecificationRepository, Inject]

    @BeforeEach
    def beforeEach(self):
        # tag::save[]
        keanu = Person("Keanu Reeves", 1964)
        carrie = Person("Carrie-Anne Moss", 1967)
        lana = Person("Lana Wachowski", 1965)

        matrix = Movie("The Matrix", "Welcome to the Real World", 1999)
        matrix.roles = [Role(["Neo"], keanu), Role(["Trinity"], carrie)]
        matrix.directors = [lana]

        self.movieRepository.save(matrix)  # <1>
        # end::save[]
        self.movieRepository.save(Movie("The Matrix Reloaded", "Free your mind", 2003, [Role(["Neo"], keanu)], [lana]))
        self.movieRepository.save(Movie("Speed", "Get ready for rush hour", 1994, [Role(["Jack Traven"], keanu)]))

    @AfterEach
    def afterEach(self):
        self.movieRepository.deleteAll()
        self.personRepository.deleteAll()

    @Test
    def testFinders(self):
        # tag::find[]
        recent = self.movieRepository.findByReleasedGreaterThanOrderByReleased(1995)
        older = self.movieRepository.countByReleasedLessThan(2000)
        page = self.movieRepository.findByReleasedGreaterThan(1990, Pageable.from_(0, 2, Sort.of(Sort.Order.asc("title"))))
        # end::find[]

        assert [m.title for m in recent] == ["The Matrix", "The Matrix Reloaded"]
        assert older == 2
        assert page.getTotalSize() == 3

    @Test
    def testTraversal(self):
        # tag::traverse[]
        directed_by_lana = self.movieRepository.findByDirectorsName("Lana Wachowski")
        with_keanu = self.movieRepository.findByRolesPersonName("Keanu Reeves")
        # end::traverse[]

        assert len(directed_by_lana) == 2
        assert len(with_keanu) == 3

    @Test
    def testJoin(self):
        # tag::fetch[]
        matrix = self.movieRepository.findByTitle("The Matrix")
        neo = next(role for role in matrix.roles if role.person.name == "Keanu Reeves")
        # end::fetch[]

        assert neo.roles == ["Neo"]
        assert [d.name for d in matrix.directors] == ["Lana Wachowski"]

    @Test
    def testNestedJoin(self):
        lilly = self.personRepository.save(Person("Lilly Wachowski", 1967, [Person("Lana Wachowski", 1965)]))
        self.movieRepository.save(Movie("Bound", "In the end, it's all about the money", 1996, [], [lilly]))

        # tag::nestedFetch[]
        fetched = self.movieRepository.findByReleased(1996)[0]
        followed = fetched.directors[0].follows[0]  # <1>
        # end::nestedFetch[]

        assert followed.name == "Lana Wachowski"

    @Test
    def testSpecifications(self):
        # tag::useSpecifications[]
        recent = self.movieSpecificationRepository.findAll(released_after(1995))  # <1>
        by_lana = self.movieSpecificationRepository.count(directed_by("Lana Wachowski"))  # <2>
        # end::useSpecifications[]

        assert sorted(m.title for m in recent) == ["The Matrix", "The Matrix Reloaded"]
        assert by_lana == 2
