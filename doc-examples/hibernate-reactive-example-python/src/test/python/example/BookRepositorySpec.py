from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.model import Pageable
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Book import Book
from example.BookRepository import BookRepository


@MicronautTest(transactional=False)
class BookRepositorySpec:

    # tag::inject[]
    bookRepository: Annotated[BookRepository, Inject]
    # end::inject[]

    @AfterEach
    def cleanup(self):
        self.bookRepository.deleteAll().block()

    @Test
    def testCrud(self):
        assert self.bookRepository is not None

        # Create: Save a new book
        # tag::save[]
        book = Book("The Stand", 1000)
        book = self.bookRepository.save(book).block()
        # end::save[]
        id = book.id
        assert id is not None

        # Read: Read a book from the database
        # tag::read[]
        book = self.bookRepository.findById(id).block()
        # end::read[]
        assert book is not None
        assert book.title == "The Stand"

        # Check the count
        assert self.bookRepository.count().block() == 1

        # Update: Update the book and save it again
        # tag::update[]
        def change_title(found_book):
            found_book.title = "Changed"

        self.bookRepository.find_by_id_and_update(id, change_title).block()
        # end::update[]
        book = self.bookRepository.findById(id).block()
        assert book.title == "Changed"

        # Delete: Delete the book
        # tag::delete[]
        self.bookRepository.deleteById(id).block()
        # end::delete[]
        assert self.bookRepository.count().block() == 0

    @Test
    def testPageable(self):
        # tag::saveall[]
        self.bookRepository.saveAll([
            Book("The Stand", 1000),
            Book("The Shining", 600),
            Book("The Power of the Dog", 500),
            Book("The Border", 700),
            Book("Along Came a Spider", 300),
            Book("Pet Cemetery", 400),
            Book("A Game of Thrones", 900),
            Book("A Clash of Kings", 1100),
        ]).then().block()
        # end::saveall[]

        # tag::pageable[]
        slice = self.bookRepository.list(Pageable.from_(0, 3)).block()
        result_list = self.bookRepository.findByPagesGreaterThan(500, Pageable.from_(0, 3)).collectList().block()
        page = self.bookRepository.findByTitleLike("The%", Pageable.from_(0, 3)).block()
        # end::pageable[]

        assert slice.getNumberOfElements() == 3
        assert len(result_list) == 3
        assert page.getNumberOfElements() == 3
        assert page.getTotalSize() == 4
