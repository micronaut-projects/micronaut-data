from java.util import Optional
from micronaut.http.annotation import Controller, Get

from example.Book import Book
from example.BookCatalog import BookCatalog


@Controller("/books")
class BookController:
    def __init__(self, catalog: BookCatalog):
        self.catalog = catalog

    @Get("/{id}")
    def find(self, id: int) -> Optional[Book]:
        return self.catalog.find(id)
