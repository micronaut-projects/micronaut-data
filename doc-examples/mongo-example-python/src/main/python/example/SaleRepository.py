from java.util import Optional
from micronaut.data.annotation import Join
from micronaut.data.mongodb.annotation import MongoAggregateOptions, MongoCollation, MongoFindOptions, MongoRepository
from micronaut.data.repository import CrudRepository
from org.bson.types import ObjectId

from example.Quantity import Quantity
from example.Sale import Sale


# tag::options[]
@MongoFindOptions(allowDiskUse=True, maxTimeMS=1000)
@MongoAggregateOptions(allowDiskUse=True, maxTimeMS=100)
@MongoCollation("{ locale: 'en_US', numericOrdering: true}")
@MongoRepository
class SaleRepository(CrudRepository[Sale, ObjectId]):
    # end::options[]

    @Join("product")
    @Join("product.manufacturer")
    def getById(self, id: ObjectId) -> Optional[Sale]: ...  # TODO(python): overriding the inherited findById drops the method

    @Join("product")
    @Join("product.manufacturer")
    def findByQuantity(self, quantity: Quantity) -> Optional[Sale]: ...
