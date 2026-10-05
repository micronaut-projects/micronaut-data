from typing import Annotated

from jakarta.inject import Inject
from micronaut.core.type import Argument
from micronaut.data.model import Page, Sort
from micronaut.http import HttpRequest
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, BeforeEach, Test

from example.Book import Book
from example.BookSummary import BookSummary
from example.PagedBookRepository import PagedBookRepository


@MicronautTest(transactional=False)
class BookControllerTest:

    client: Annotated[HttpClient, Inject, Client("/")]
    bookRepository: Annotated[PagedBookRepository, Inject]

    @BeforeEach
    def setup(self):
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

    @AfterEach
    def cleanup(self):
        self.bookRepository.deleteAll()

    @Test
    def testPageableBinding(self):
        # tag::request[]
        page = self.client.toBlocking().retrieve(
            HttpRequest.GET("/books?page=1&size=3&sort=pages,desc&sort=title"),  # <1>
            Argument.of(Page, BookSummary)  # <2>
        )
        # end::request[]

        assert page.getTotalSize() == 8
        assert page.getTotalPages() == 3
        assert page.getPageNumber() == 1
        assert page.getSize() == 3
        order_by = page.getPageable().getOrderBy()
        expected = [Sort.Order.desc("pages"), Sort.Order.asc("title")]
        assert len(order_by) == len(expected)
        assert all(actual.equals(order) for actual, order in zip(order_by, expected))
        assert [b.title for b in page.getContent()] == ["The Border", "The Shining", "The Power of the Dog"]

    @Test
    def testDefaults(self):
        page = self.client.toBlocking().retrieve(
            HttpRequest.GET("/books"),
            Argument.of(Page, BookSummary)
        )
        # no parameters: the first page, the default page size (the max page size: 100) and no sorting
        assert page.getPageNumber() == 0
        assert page.getSize() == 100
        assert len(page.getContent()) == 8
        assert len(page.getPageable().getOrderBy()) == 0

        page = self.client.toBlocking().retrieve(
            HttpRequest.GET("/books?size=1000&sort=title"),
            Argument.of(Page, BookSummary)
        )
        # the requested size is capped by the max page size
        assert page.getSize() == 100
        assert page.getContent().get(0).title == "A Clash of Kings"
