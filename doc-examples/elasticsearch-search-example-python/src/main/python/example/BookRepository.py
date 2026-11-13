from micronaut.data.search.elasticsearch.annotation import ElasticsearchRepository
from micronaut.data.model.vector.search import SearchResults
from micronaut.data.repository import CrudRepository, SearchRepository

from example.Book import Book


# tag::repository[]
@ElasticsearchRepository
class BookRepository(CrudRepository[Book, str], SearchRepository[Book, str]):  # <1>

    def findByTitleSearch(self, query: str) -> list[Book]: ...  # <2>

    def searchByTitleSearchAll(self, query: str) -> SearchResults[Book]: ...  # <3>

    def findByDescriptionSearchPhrase(self, query: str) -> list[Book]: ...  # <4>

    def findByTitleSearchPrefix(self, query: str) -> list[Book]: ...  # <5>

    def findBySearchAndAuthor(self, query: str, author: str) -> list[Book]: ...  # <6>
# end::repository[]
