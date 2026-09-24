from micronaut.data.mongodb.annotation import MongoRepository
from micronaut.data.repository import GenericRepository
from org.bson.types import ObjectId

from example.Manufacturer import Manufacturer


@MongoRepository
class ManufacturerRepository(GenericRepository[Manufacturer, ObjectId]):
    def findByName(self, name: str) -> Manufacturer: ...

    def save(self, name: str) -> Manufacturer: ...
