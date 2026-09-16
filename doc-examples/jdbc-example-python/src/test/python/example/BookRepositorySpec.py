from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.context import BeanContext
from micronaut.data.model import CursoredPageable, Pageable, Sort
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Disabled, Test

from example.Book import Book
from example.BookRepository import BookRepository
from example.Review import Review


@MicronautTest
class BookRepositorySpec:

    # tag::inject[]
    bookRepository: Annotated[BookRepository, Inject]
    # end::inject[]

    # tag::metadata[]
    beanContext: Annotated[BeanContext, Inject]

    @AfterEach
    def cleanup(self):
        self.bookRepository.deleteAll()

    @Test
    def testAnnotationMetadata(self):
        query = (self.beanContext.getBeanDefinition(BookRepository)  # <1>
                 .getRequiredMethod("find", String)  # <2>
                 .getAnnotationMetadata()
                 .stringValue("io.micronaut.data.annotation.Query")  # <3>
                 .orElse(None))

        assert query == "SELECT book_.`id`,book_.`title`,book_.`pages` FROM `book` book_ WHERE (book_.`title` = ?)"  # <4>
    # end::metadata[]

    @Test
    def testCrud(self):
        assert self.bookRepository is not None

        # Create: Save a new book
        # tag::save[]
        book = Book("The Stand", 1000)
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
            Book("The Stand", 1000),
            Book("The Shining", 600),
            Book("The Power of the Dog", 500),
            Book("The Border", 700),
            Book("Along Came a Spider", 300),
            Book("Pet Cemetery", 400),
            Book("A Game of Thrones", 900),
            Book("A Clash of Kings", 1100),
        ])
        # end::saveall[]

        # tag::pageable[]
        slice = self.bookRepository.list(Pageable.from_(0, 3))
        resultList = self.bookRepository.findAllByPagesGreaterThan(500, Pageable.from_(0, 3))
        page = self.bookRepository.findByTitleLike("The%", Pageable.from_(0, 3))
        # end::pageable[]

        assert slice.getNumberOfElements() == 3
        assert len(resultList) == 3
        assert page.getNumberOfElements() == 3
        assert page.getTotalSize() == 4

    @Test
    def testCursoredPageable(self):
        self.bookRepository.saveAll([
            Book("The Stand", 1000),
            Book("The Shining", 600),
            Book("The Power of the Dog", 500),
            Book("The Border", 700),
            Book("Along Came a Spider", 300),
            Book("Pet Cemetery", 400),
            Book("A Game of Thrones", 900),
            Book("A Clash of Kings", 1100),
        ])

        # tag::cursored-pageable[]
        page = self.bookRepository.findAll(CursoredPageable.from_(5, Sort.of(Sort.Order.asc("title"))))  # <1>
        page2 = self.bookRepository.findAll(page.nextPageable())  # <2>
        pageByPagesBetween = self.bookRepository.findByPagesBetween(400, 700, Pageable.from_(0, 3))  # <3>
        pageByTitleStarts = self.bookRepository.findByTitleStartingWith("The", CursoredPageable.from_(3, Sort.unsorted()))  # <4>
        # end::cursored-pageable[]

        assert page.getNumberOfElements() == 5
        assert page2.getNumberOfElements() == 3
        assert pageByPagesBetween.getNumberOfElements() == 3
        assert pageByTitleStarts.getNumberOfElements() == 3

    @Test
    def testDto(self):
        self.bookRepository.save(Book("The Shining", 400))
        book = self.bookRepository.findOne("The Shining")

        assert book.title == "The Shining"

    @Test
    def testExpressions(self):
        assert self.bookRepository.count() == 0

        book = Book("The Stand", 1000)
        self.bookRepository.insertCustomExp(book)

        book = self.bookRepository.findByTitle("The StandABC")
        assert book is not None
        assert book.title == "The StandABC"  # Modified by expression

        assert self.bookRepository.count() == 1
        assert self.bookRepository.findAll().iterator().hasNext()

    @Test
    @Disabled("TODO(python): the generated equals/hashCode of a dataclass recurse over a bidirectional association, see DISABLED_TESTS.md")
    def testOneToManyCustomQuery(self):
        self.bookRepository.save(Book("Dummy Book", 20, [Review("Anonymous", "Lorem Ipsum"), Review("Member", "Interesting")]))
        books = self.bookRepository.searchBooksByTitle("Dummy Book")
        assert len(books) == 1
        book = books[0]
        assert book.title == "Dummy Book"
        assert len(book.reviews) == 2
