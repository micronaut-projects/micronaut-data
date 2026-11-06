from typing import Annotated

from micronaut.data.model import Pageable, Sort
from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, BeforeEach, Test

from example.Movie import Movie
from example.MovieRepository import MovieRepository
from example.MovieSpecificationRepository import MovieSpecificationRepository
from example.MovieSpecifications import connected_to, directed_by, released_after, with_roles
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
        keanu = self.personRepository.save(Person("Keanu Reeves", 1964))  # <1>
        carrie = self.personRepository.save(Person("Carrie-Anne Moss", 1967))
        lana = self.personRepository.save(Person("Lana Wachowski", 1965))

        matrix = Movie("The Matrix", "Welcome to the Real World", 1999)
        matrix.roles = [Role(["Neo"], keanu), Role(["Trinity"], carrie)]
        matrix.directors = [lana]

        self.movieRepository.save(matrix)  # <2>
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
    def testPathsToPeople(self):
        keanu = self.personRepository.findByName("Keanu Reeves")
        lana = self.personRepository.findByName("Lana Wachowski")

        # tag::crossType[]
        directed = self.movieRepository.findRelationshipsByDirectors("The Matrix", lana.id)[0]
        director = getattr(directed, "from")().name  # <1>

        path = self.movieRepository.findShortestPathToPersonBetweenTitleAndName("Speed", "Carrie-Anne Moss")
        end = path.end()  # <2>
        # end::crossType[]

        assert director == "Lana Wachowski"
        assert directed.to().title == "The Matrix"
        assert path.length() == 3
        assert isinstance(end, Person)
        assert end.name == "Carrie-Anne Moss"
        assert isinstance(path.start(), Movie)
        assert self.movieRepository.findAllPathsToPerson("The Matrix", keanu.id)[0].length() == 1

    @Test
    def testRelationshipEntities(self):
        keanu = self.personRepository.findByName("Keanu Reeves")

        # tag::relationshipEntities[]
        role = self.movieRepository.findRelationshipsByRoles("The Matrix", keanu.id)[0]
        characters = role.roles  # <1>
        actor = role.person  # <2>
        # end::relationshipEntities[]

        assert list(characters) == ["Neo"]
        assert actor.name == "Keanu Reeves"

    @Test
    def testSpecifications(self):
        # tag::useSpecifications[]
        recent = self.movieSpecificationRepository.findAll(released_after(1995))  # <1>
        by_lana = self.movieSpecificationRepository.count(directed_by("Lana Wachowski"))  # <2>
        # end::useSpecifications[]

        assert sorted(m.title for m in recent) == ["The Matrix", "The Matrix Reloaded"]
        assert by_lana == 2

    @Test
    def testGraphCriteria(self):
        # tag::useGraphCriteria[]
        with_cast = self.movieSpecificationRepository.count(with_roles())  # <1>
        near_speed = self.movieSpecificationRepository.findAll(connected_to("Speed", 2))  # <2>
        # end::useGraphCriteria[]

        assert with_cast == 3, f"with_cast={with_cast!r}"
        titles = sorted(m.title for m in near_speed)
        assert titles == ["The Matrix", "The Matrix Reloaded"], f"titles={titles!r}"
