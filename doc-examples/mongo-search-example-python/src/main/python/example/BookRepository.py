from micronaut.data.mongodb.annotation import MongoRepository
from micronaut.data.model.vector.search import SearchResults
from micronaut.data.repository import CrudRepository, SearchRepository

from example.Book import Book

# tag::repository[]
@MongoRepository
class BookRepository(CrudRepository[Book, str], SearchRepository[Book, str]):  # <1>

    def findByTitleSearch(self, query: str) -> list[Book]: ...  # <2>

    def searchByTitleSearchAll(self, query: str) -> SearchResults[Book]: ...  # <3>

    def findByDescriptionSearchPhrase(self, query: str) -> list[Book]: ...  # <4>

    def findBySearchAndAuthor(self, query: str, author: str) -> list[Book]: ...  # <5>
# end::repository[]
