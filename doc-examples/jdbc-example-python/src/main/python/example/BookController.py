from micronaut.data.model import Page, Pageable
from micronaut.http.annotation import Controller, Get

from example.BookSummary import BookSummary
from example.PagedBookRepository import PagedBookRepository


# tag::controller[]
@Controller("/books")
class BookController:

    def __init__(self, book_repository: PagedBookRepository):
        self.book_repository = book_repository

    @Get  # <1>
    def list(self, pageable: Pageable) -> Page[BookSummary]:  # <2>
        return self.book_repository.list(pageable)  # <3>
# end::controller[]
