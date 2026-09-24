from micronaut.data.mongodb.annotation import MongoRepository
from micronaut.data.repository import CrudRepository
from org.bson.types import ObjectId

from example.Book import Book


@MongoRepository
class AbstractBookRepository(CrudRepository[Book, ObjectId]):

    def findByTitle(self, title: str) -> list[Book]: ...
