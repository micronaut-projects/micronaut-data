from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.ArchivedMovieRepository import ArchivedMovieRepository
from example.Movie import Movie
from example.MovieRepository import MovieRepository


# Both drivers connect to the same server in the test
@MicronautTest(transactional=False)
@Property(name="micronaut.data.neo4j.drivers.archive.uri", value="${neo4j.uri}")
class ArchivedMovieRepositorySpec:

    archivedMovieRepository: Annotated[ArchivedMovieRepository, Inject]
    movieRepository: Annotated[MovieRepository, Inject]

    @AfterEach
    def afterEach(self):
        self.movieRepository.deleteAll()

    @Test
    def testArchiveDriver(self):
        self.archivedMovieRepository.save(Movie("Metropolis", "The mediator between head and hands must be the heart", 1927))

        assert self.movieRepository.existsById("Metropolis")
