# tag::studentRepository[]
from typing import Annotated

from micronaut.data.annotation import Id, Version
from micronaut.data.mongodb.annotation import MongoRepository
from micronaut.data.repository import CrudRepository
from org.bson.types import ObjectId

from example.Student import Student


@MongoRepository
class StudentRepository(CrudRepository[Student, ObjectId]):

    def update(self, id: Annotated[ObjectId, Id], version: Annotated[int, Version], name: str) -> None: ...

    def delete(self, id: Annotated[ObjectId, Id], version: Annotated[int, Version]) -> None: ...
# end::studentRepository[]
