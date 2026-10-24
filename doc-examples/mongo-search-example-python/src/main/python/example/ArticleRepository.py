from micronaut.data.mongodb.annotation import MongoRepository
from micronaut.data.model.vector import Vector
from micronaut.data.model.vector.search import Score, SearchResults
from micronaut.data.repository import CrudRepository, SearchRepository

from example.Article import Article


# tag::repository[]
@MongoRepository
class ArticleRepository(CrudRepository[Article, str], SearchRepository[Article, str]):
    def findByTitleSearch(self, query: str) -> list[Article]: ...  # <1>
    def findByTitleSearchPrefix(self, query: str) -> list[Article]: ...  # <2>
    def searchByBodySearchFuzzy(self, query: str) -> SearchResults[Article]: ...  # <3>
    def searchBySearch(self, query: str, minScore: Score) -> SearchResults[Article]: ...  # <4>
    def findBySearchAndAuthor(self, query: str, author: str) -> list[Article]: ...  # <5>
    def searchByEmbeddingNear(self, embedding: Vector, maxDistance: Score) -> SearchResults[Article]: ...  # <6>
# end::repository[]
