from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.model import Pageable, Sort
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, BeforeEach, Test

from example.Book import Book
from example.PagedBookRepository import PagedBookRepository


@MicronautTest
class PagedBookRepositorySpec:

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
    def testExplicitQueryPagination(self):
        # tag::explicit-usage[]
        pageable = Pageable.from_(1, 2, Sort.of(Sort.Order.desc("pages")))  # <1>
        page = self.bookRepository.findLongBooks(500, pageable)  # <2>
        slice = self.bookRepository.sliceLongBooks(500, pageable)  # <3>
        books = self.bookRepository.listLongBooks(500, Pageable.from_(0, 3))  # <4>
        sorted_books = self.bookRepository.listLongBooksSorted(500, Sort.of(Sort.Order.asc("title")))  # <5>
        # end::explicit-usage[]

        assert page.getTotalSize() == 5
        assert page.getTotalPages() == 3
        assert [b.title for b in page.getContent()] == ["A Game of Thrones", "The Border"]
        assert [b.title for b in slice.getContent()] == ["A Game of Thrones", "The Border"]
        assert len(books) == 3
        assert [b.title for b in sorted_books] == ["A Clash of Kings", "A Game of Thrones", "The Border", "The Shining", "The Stand"]

    @Test
    def testNativeQueryPagination(self):
        # tag::native-usage[]
        page = self.bookRepository.searchByTitle(
            "The%", Pageable.from_(0, 3, Sort.of(Sort.Order.asc("b.pages"))))  # <1>
        # end::native-usage[]

        assert page.getTotalSize() == 4
        assert page.getTotalPages() == 2
        assert page.hasNext()
        assert [b.title for b in page.getContent()] == ["The Power of the Dog", "The Shining", "The Border"]

        last = self.bookRepository.searchByTitle("The%", page.nextPageable())
        assert [b.title for b in last.getContent()] == ["The Stand"]
        assert not last.hasNext()
