from jakarta.inject import Singleton
from micronaut.transaction.annotation import Transactional
from reactor.core.publisher import Mono

from example.Movie import Movie
from example.ReactiveMovieRepository import ReactiveMovieRepository


@Singleton
class ReactiveMovieService:

    def __init__(self, movieRepository: ReactiveMovieRepository):
        self.movieRepository = movieRepository

    # tag::transactional[]
    @Transactional  # <1>
    def saveAndCount(self, movie: Movie) -> Mono[int]:
        return self.movieRepository.save(movie).then(self.movieRepository.count())  # <2>
    # end::transactional[]
