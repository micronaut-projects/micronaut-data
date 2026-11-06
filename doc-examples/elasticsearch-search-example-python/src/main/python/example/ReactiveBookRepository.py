from micronaut.data.model.vector.search import SearchResults
from micronaut.data.repository.reactive import ReactorCrudRepository, ReactorSearchRepository
from micronaut.data.search.elasticsearch.annotation import ElasticsearchRepository
from reactor.core.publisher import Flux, Mono

from example.Book import Book


# tag::reactive[]
@ElasticsearchRepository
class ReactiveBookRepository(ReactorCrudRepository[Book, str], ReactorSearchRepository[Book, str]):  # <1>

    def searchBySearch(self, query: str) -> Mono[SearchResults[Book]]: ...  # <2>

    def findByTitleSearch(self, query: str) -> Flux[Book]: ...  # <3>
# end::reactive[]
