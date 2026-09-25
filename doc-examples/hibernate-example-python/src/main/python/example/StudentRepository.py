# tag::studentRepository[]
from typing import Annotated

from micronaut.data.annotation import Id, Repository, Version
from micronaut.data.repository import CrudRepository

from example.Student import Student


@Repository
class StudentRepository(CrudRepository[Student, int]):

    def update(self, id: Annotated[int, Id], version: Annotated[int, Version], name: str) -> None: ...

    def delete(self, id: Annotated[int, Id], version: Annotated[int, Version]) -> None: ...
# end::studentRepository[]
