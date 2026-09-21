from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.context import BeanContext
from micronaut.context.annotation import Property
from micronaut.data.model import Pageable
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.AbstractBookRepository import AbstractBookRepository
from example.Book import Book
from example.BookRepository import BookRepository


@MicronautTest
@Property(name="datasources.default.name", value="mydb")
@Property(name="jpa.default.properties.hibernate.hbm2ddl.auto", value="create-drop")
class BookRepositorySpec:

    # tag::inject[]
    bookRepository: Annotated[BookRepository, Inject]
    # end::inject[]

    abstractBookRepository: Annotated[AbstractBookRepository, Inject]

    # tag::metadata[]
    beanContext: Annotated[BeanContext, Inject]

    @Test
    def testAnnotationMetadata(self):
        query = (self.beanContext.getBeanDefinition(BookRepository)  # <1>
                 .getRequiredMethod("find", String)  # <2>
                 .getAnnotationMetadata().stringValue("io.micronaut.data.annotation.Query")  # <3>
                 .orElse(None))

        assert query == "SELECT book_ FROM example.Book AS book_ WHERE (book_.title = :p1)"  # <4>
    # end::metadata[]

    @AfterEach
    def cleanup(self):
        self.bookRepository.deleteAll()

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
        book.title = "Changed"
        self.bookRepository.save(book)
        # end::update[]
        book = self.bookRepository.findById(id).orElse(None)
        assert book.title == "Changed"

        # Partially update book via executeUpdate
        self.bookRepository.updatePages(id, 1200)
        book = self.bookRepository.findById(id).orElse(None)
        assert book.pages == 1200

        # Delete: Delete the book
        # tag::delete[]
        self.bookRepository.deleteById(id)
        # end::delete[]
        assert self.bookRepository.count() == 0

    @Test
    def testExpressions(self):
        assert self.bookRepository.count() == 0

        book = Book("The Stand", 1000)
        self.bookRepository.insertCustomExp(book)
        assert book.id is None  # Custom query doesn't update generated ID

        assert self.bookRepository.count() == 1
        iterator = self.bookRepository.findAll().iterator()
        assert iterator.hasNext()
        book = iterator.next()
        assert book is not None
        assert book.title == "The StandABC"  # Modified by expression

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
        result_list = self.bookRepository.findAllByPagesGreaterThan(500, Pageable.from_(0, 3))
        page = self.bookRepository.findByTitleLike("The%", Pageable.from_(0, 3))
        # end::pageable[]

        assert slice.getNumberOfElements() == 3
        assert len(result_list) == 3
        assert page.getNumberOfElements() == 3
        assert page.getTotalSize() == 4

        results = self.abstractBookRepository.findByTitle("The Shining")
        assert len(results) == 1

    @Test
    def testDto(self):
        self.bookRepository.save(Book("The Shining", 400))
        book = self.bookRepository.findOne("The Shining")

        assert book.title == "The Shining"
