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
        self.movieRepository.deleteAll().block()

    @Test
    def testReactive(self):
        # tag::reactive[]
        lana = Person("Lana Wachowski", 1965)
        directed_by_lana = self.movieRepository.saveAll([  # <1>
            Movie("The Matrix", 1999, [lana]),
            Movie("The Matrix Reloaded", 2003, [lana])
        ]).thenMany(
            self.movieRepository.findByDirectorsName("Lana Wachowski")  # <2>
        ).collectList().block()  # <3>
        # end::reactive[]

        assert len(directed_by_lana) == 2
        assert [m.title for m in self.movieRepository.findByReleasedGreaterThanOrderByReleased(2000).collectList().block()] == ["The Matrix Reloaded"]
