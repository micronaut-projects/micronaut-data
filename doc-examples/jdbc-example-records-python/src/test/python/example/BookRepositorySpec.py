from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.model import Pageable
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Book import Book
from example.BookRepository import BookRepository


@MicronautTest
class BookRepositorySpec:

    bookRepository: Annotated[BookRepository, Inject]

    @AfterEach
    def cleanup(self):
        self.bookRepository.deleteAll()

    @Test
    def testCrud(self):
        assert self.bookRepository is not None

        # Create: Save a new book
        # tag::save[]
        book = Book(None, None, "The Stand", 1000)
        book = self.bookRepository.save(book)
        # end::save[]
        id = book.id
        assert id is not None

        # Read: Read a book from the database
        # tag::read[]
        book = self.bookRepository.findById(id).orElse(None)
        # end::read[]
        assert book is not None
        assert book.title == "The Stand"
        assert book.dateCreated is not None

        # Check the count
        assert self.bookRepository.count() == 1
        assert self.bookRepository.findAll().iterator().hasNext()

        # Update: Update the book and save it again
        # tag::update[]
        self.bookRepository.update(book.id, "Changed")
        # end::update[]
        book = self.bookRepository.findById(id).orElse(None)
        assert book.title == "Changed"

        # Delete: Delete the book
        # tag::delete[]
        self.bookRepository.deleteById(id)
        # end::delete[]
        assert self.bookRepository.count() == 0

    @Test
    def testPageable(self):
        # tag::saveall[]
        self.bookRepository.saveAll([
            Book(None, None, "The Stand", 1000),
            Book(None, None, "The Shining", 600),
            Book(None, None, "The Power of the Dog", 500),
            Book(None, None, "The Border", 700),
            Book(None, None, "Along Came a Spider", 300),
            Book(None, None, "Pet Cemetery", 400),
            Book(None, None, "A Game of Thrones", 900),
            Book(None, None, "A Clash of Kings", 1100),
        ])
        # end::saveall[]

        # tag::pageable[]
        slice = self.bookRepository.list(Pageable.from_(0, 3))
        result_list = self.bookRepository.findAllByPagesGreaterThan(500, Pageable.from_(0, 3))
        page = self.bookRepository.findByTitleLike("The%", Pageable.from_(0, 3))
        # end::pageable[]

        assert slice.getNumberOfElements() == 3
        assert len(result_list) == 3
        assert page.getNumberOfElements() == 3
        assert page.getTotalSize() == 4

    @Test
    def testDto(self):
        self.bookRepository.save(Book(None, None, "The Shining", 400))
        book = self.bookRepository.findOne("The Shining")

        assert book.title == "The Shining"
