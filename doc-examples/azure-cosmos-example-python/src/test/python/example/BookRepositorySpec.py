from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.context import BeanContext
from micronaut.data.model import Pageable
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Book import Book
from example.BookRepository import BookRepository
from example.ItemPrice import ItemPrice


@MicronautTest
class BookRepositorySpec:

    # tag::inject[]
    bookRepository: Annotated[BookRepository, Inject]
    # end::inject[]

    # tag::metadata[]
    beanContext: Annotated[BeanContext, Inject]

    @Test
    def testAnnotationMetadata(self):
        query = (self.beanContext.getBeanDefinition(BookRepository)  # <1>
                 .getRequiredMethod("find", String)  # <2>
                 .getAnnotationMetadata()
                 .stringValue("io.micronaut.data.annotation.Query")  # <3>
                 .orElse(None))

        assert query == "SELECT DISTINCT VALUE book_ FROM book book_ WHERE (book_.title = @p1)"  # <4>
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
        book.itemPrice = ItemPrice(200)
        book = self.bookRepository.insert(book)
        # end::save[]
        id = book.id
        assert id is not None
        assert book.createdDate is not None
        assert book.updatedDate is not None
        assert book.itemPrice.price == 200

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
        assert book is not None
        assert book.title == "Changed"

        # Delete: Delete the book
        # tag::delete[]
        self.bookRepository.deleteById(id)
        # end::delete[]
        assert self.bookRepository.count() == 0

    @Test
    def testPageable(self):
        # tag::saveall[]
        self.bookRepository.insertAll([
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
        # end::pageable[]

        assert slice.getNumberOfElements() == 3
        assert len(result_list) == 3

    @Test
    def testDto(self):
        self.bookRepository.save(Book("The Shining", 400))
        book = self.bookRepository.findOne("The Shining")

        assert book.title == "The Shining"
