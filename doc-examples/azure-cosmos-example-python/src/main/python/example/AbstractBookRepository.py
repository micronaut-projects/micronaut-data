from micronaut.data.cosmos.annotation import CosmosRepository
from micronaut.data.repository import CrudRepository

from example.Book import Book


@CosmosRepository
class AbstractBookRepository(CrudRepository[Book, str]):

    def findByTitle(self, title: str) -> list[Book]: ...
