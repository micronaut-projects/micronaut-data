from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Movie import Movie
from example.ReactiveMovieRepository import ReactiveMovieRepository


@MicronautTest(transactional=False)
class ReactiveMovieRepositorySpec:

    movieRepository: Annotated[ReactiveMovieRepository, Inject]

    @AfterEach
    def afterEach(self):
        self.movieRepository.deleteAll().block()

    @Test
    def testReactive(self):
        # tag::reactive[]
        movies = self.movieRepository.saveAll([  # <1>
            Movie("The Matrix", "Welcome to the Real World", 1999),
            Movie("Speed", "Get ready for rush hour", 1994),
        ]).thenMany(self.movieRepository.findByReleasedGreaterThan(1995)).collectList().block()  # <2> <3>
        # end::reactive[]

        assert [m.title for m in movies] == ["The Matrix"]
