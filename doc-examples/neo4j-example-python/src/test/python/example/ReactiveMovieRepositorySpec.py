from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.neo4j.operations import Neo4jReactorRepositoryOperations
from micronaut.data.neo4j.transaction import Neo4jReactorTransactionOperations
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.transaction import TransactionDefinition
from org.junit.jupiter.api import AfterEach, Test

from example.Movie import Movie
from example.ReactiveMovieRepository import ReactiveMovieRepository
from example.ReactiveMovieService import ReactiveMovieService


@MicronautTest(transactional=False)
class ReactiveMovieRepositorySpec:

    movieRepository: Annotated[ReactiveMovieRepository, Inject]
    movieService: Annotated[ReactiveMovieService, Inject]
    transactionOperations: Annotated[Neo4jReactorTransactionOperations, Inject]
    reactiveOperations: Annotated[Neo4jReactorRepositoryOperations, Inject]

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

    @Test
    def testTransactions(self):
        count = self.movieService.saveAndCount(Movie("The Matrix", "Welcome to the Real World", 1999)).block()

        # tag::transactionOperations[]
        saved = self.transactionOperations.withTransaction(TransactionDefinition.DEFAULT, lambda status:  # <1>
            self.movieRepository.saveAll([Movie("Speed", "Get ready for rush hour", 1994)])
        ).collectList().block()
        # end::transactionOperations[]

        assert count == 1
        assert len(saved) == 1
        assert self.movieRepository.count().block() == 2

    @Test
    def testReactiveQuery(self):
        self.movieRepository.save(Movie("Speed", "Get ready for rush hour", 1994)).block()

        # tag::reactiveQuery[]
        movies = self.reactiveOperations.executeQuery("MATCH (m:Movie) RETURN m", {}, Movie).collectList().block()  # <1>
        # end::reactiveQuery[]

        assert movies[0].title == "Speed"
