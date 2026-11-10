from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Movie import Movie
from example.MovieRepository import MovieRepository
from example.Person import Person


@MicronautTest(transactional=False)
class MovieRepositorySpec:

    movieRepository: Annotated[MovieRepository, Inject]

    @AfterEach
    def afterEach(self):
        self.movieRepository.deleteAll()

    @Test
    def testSaveAndFind(self):
        # tag::save[]
        lana = Person("Lana Wachowski", 1965)
        self.movieRepository.save(Movie("The Matrix", 1999, [lana]))  # <1>
        self.movieRepository.save(Movie("The Matrix Reloaded", 2003, [lana]))

        directed_by_lana = self.movieRepository.findByDirectorsName("Lana Wachowski")  # <2>
        # end::save[]

        assert len(directed_by_lana) == 2
        assert [m.title for m in self.movieRepository.findByReleasedGreaterThanOrderByReleased(2000)] == ["The Matrix Reloaded"]
