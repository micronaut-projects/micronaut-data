from micronaut.data.annotation import Repository
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.model.vector import Vector
from micronaut.data.model.vector.search import Score, ScoringFunction, SearchResults
from micronaut.data.repository import CrudRepository

from example.DocumentEmbedding import DocumentEmbedding


@JdbcRepository(dialect=Dialect.ORACLE)
@Repository
class DocumentEmbeddingRepository(CrudRepository[DocumentEmbedding, int]):

    # Example showing vector as a parameter
    def save(self, embedding: Vector) -> DocumentEmbedding: ...

    def findTop2ByEmbeddingNear(self, vec: Vector, maxDistance: float) -> list[DocumentEmbedding]: ...

    def searchByEmbeddingNear(self, vector: Vector, maxDistance: Score, function: ScoringFunction) -> SearchResults[DocumentEmbedding]: ...
