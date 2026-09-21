from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.r2dbc.operations import R2dbcOperations
from micronaut.http.annotation import Get
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, BeforeEach, Test
from reactor.core.publisher import Flux

from example.Author import Author
from example.AuthorRepository import AuthorRepository
from example.Book import Book
from example.BookRepository import BookRepository


@Client("/books")
class BookClient:
    @Get("/")
    def list(self) -> list[Book]: ...


@MicronautTest(transactional=False)
class BookControllerTest:

    bookClient: Annotated[BookClient, Inject]
    operations: Annotated[R2dbcOperations, Inject]
    authorRepository: Annotated[AuthorRepository, Inject]
    bookRepository: Annotated[BookRepository, Inject]

    @BeforeEach
    def setupData(self):
        operations = self.operations
        authorRepository = self.authorRepository
        bookRepository = self.bookRepository
        # tag::programmatic-tx[]
        Flux.from_(operations.withTransaction(lambda status:
            Flux.from_(authorRepository.save(Author("Stephen King")))
                .flatMap(lambda author: bookRepository.saveAll([
                    Book("The Stand", 1000, author),
                    Book("The Shining", 400, author),
                ]))
                .thenMany(Flux.from_(authorRepository.save(Author("James Patterson"))))
                .flatMap(lambda author: bookRepository.save(Book("Along Came a Spider", 300, author)))
                .then()
        )).collectList().block()
        # end::programmatic-tx[]

        # tag::programmatic-tx-status[]
        Flux.from_(operations.withTransaction(lambda status:  # <1>
            Flux.from_(authorRepository.save(Author("Michael Crichton")))
                .flatMap(lambda author: operations.withTransaction(status, lambda s:  # <2>
                    bookRepository.saveAll([
                        Book("Jurassic Park", 300, author),
                        Book("Disclosure", 400, author),
                    ])))
        )).collectList().block()
        # end::programmatic-tx-status[]

    @AfterEach
    def cleanup(self):
        Flux.from_(self.bookRepository.deleteAll()).collectList().block()
        Flux.from_(self.authorRepository.deleteAll()).collectList().block()

    @Test
    def testListBooks(self):
        books = self.bookClient.list()
        assert len(books) == 5
