from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.model.vector.search import Score, SearchResults
from micronaut.data.repository import CrudRepository, SearchRepository

from example.Book import Book


# tag::repository[]
@JdbcRepository(dialect=Dialect.POSTGRES)
class BookRepository(CrudRepository[Book, int], SearchRepository[Book, int]):  # <1>

    def findByTitleSearch(self, query: str) -> list[Book]: ...  # <2>

    def searchByTitleSearchAll(self, query: str) -> SearchResults[Book]: ...  # <3>

    def findByDescriptionSearchPhrase(self, query: str) -> list[Book]: ...  # <4>

    def findByTitleSearchPrefix(self, query: str) -> list[Book]: ...  # <5>

    def findBySearchAndAuthor(self, query: str, author: str) -> list[Book]: ...  # <6>

    def searchByTitleSearch(self, query: str, minScore: Score) -> SearchResults[Book]: ...  # <7>
# end::repository[]
