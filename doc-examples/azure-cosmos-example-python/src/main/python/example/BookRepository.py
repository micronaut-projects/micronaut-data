from typing import Annotated

from micronaut.data.annotation import Id
from micronaut.data.cosmos.annotation import CosmosRepository
from micronaut.data.model import Pageable, Slice
from micronaut.data.repository import CrudRepository

from example.Book import Book
from example.BookDTO import BookDTO


# tag::repository[]
@CosmosRepository  # <1>
class BookRepository(CrudRepository[Book, str]):  # <2>
    # end::repository[]

    # tag::simple[]
    def findByTitle(self, title: str) -> Book: ...

    def getByTitle(self, title: str) -> Book: ...

    def retrieveByTitle(self, title: str) -> Book: ...
    # end::simple[]

    # tag::greaterthan[]
    def findByPagesGreaterThan(self, pageCount: int) -> list[Book]: ...
    # end::greaterthan[]

    # tag::simple-alt[]
    # tag::repository[]
    def find(self, title: str) -> Book: ...
    # end::simple-alt[]
    # end::repository[]

    # tag::pageable[]
    def findAllByPagesGreaterThan(self, pageCount: int, pageable: Pageable) -> list[Book]: ...

    def list(self, pageable: Pageable) -> Slice[Book]: ...
    # end::pageable[]

    # tag::simple-projection[]
    def findTitleByPagesGreaterThan(self, pageCount: int) -> list[str]: ...
    # end::simple-projection[]

    # tag::ordering[]
    def listOrderByTitle(self) -> list[Book]: ...

    def listOrderByTitleDesc(self) -> list[Book]: ...
    # end::ordering[]

    # tag::save[]
    def persist(self, entity: Book) -> Book: ...
    # end::save[]

    # tag::save2[]
    def store(self, title: str, pages: int) -> Book: ...
    # end::save2[]

    # tag::update[]
    def update(self, id: Annotated[str, Id], title: str) -> None: ...
    # end::update[]

    # tag::update2[]
    def updateByTitle(self, title: str, pages: int) -> None: ...
    # end::update2[]

    # tag::deleteall[]
    def deleteAll(self) -> None: ...
    # end::deleteall[]

    # tag::deleteone[]
    def delete(self, title: str) -> None: ...
    # end::deleteone[]

    # tag::dto[]
    def findOne(self, title: str) -> BookDTO: ...
    # end::dto[]
