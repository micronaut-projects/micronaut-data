from micronaut.data.annotation import Repository
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.model.vector import BinaryVector
from micronaut.data.model.vector.search import ScoringFunction, SearchResults
from micronaut.data.repository import CrudRepository

from example.ImageFingerprint import ImageFingerprint


@JdbcRepository(dialect=Dialect.ORACLE)
@Repository
class ImageFingerprintRepository(CrudRepository[ImageFingerprint, int]):

    # Binary vectors are compared with HAMMING distance by default
    def searchTop3ByHashNear(self, hash: BinaryVector, maxDistance: float) -> SearchResults[ImageFingerprint]: ...

    # Pass ScoringFunction.JACCARD to compare them with Jaccard distance instead
    def searchTop2ByHashNear(self, hash: BinaryVector, maxDistance: float, function: ScoringFunction) -> SearchResults[ImageFingerprint]: ...
