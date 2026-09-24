from java.util.concurrent import CompletableFuture
from micronaut.data.annotation import Join
from micronaut.data.mongodb.annotation import MongoRepository
from micronaut.data.repository import CrudRepository
from org.bson.types import ObjectId
from reactor.core.publisher import Mono

from example.Product import Product


# tag::join[]
# tag::async[]
@MongoRepository
class ProductRepository(CrudRepository[Product, ObjectId]):
    # end::join[]
    # end::async[]

    # tag::join[]
    @Join("manufacturer")  # <1>
    def list(self) -> list[Product]: ...
    # end::join[]

    # tag::async[]
    @Join("manufacturer")
    def findFirstByNameRegex(self, str: str) -> CompletableFuture[Product]: ...

    def countByManufacturerName(self, name: str) -> CompletableFuture[int]: ...
    # end::async[]

    # tag::reactive[]
    @Join("manufacturer")
    def queryFirstByNameRegex(self, str: str) -> Mono[Product]: ...

    def countDistinctByManufacturerName(self, name: str) -> Mono[int]: ...
    # end::reactive[]
