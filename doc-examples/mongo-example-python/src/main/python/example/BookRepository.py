from typing import Annotated

from micronaut.data.annotation import Id
from micronaut.data.model import Page, Pageable, Slice
from micronaut.data.mongodb.annotation import MongoAggregateQuery, MongoDeleteQuery, MongoFindQuery, MongoRepository, MongoUpdateQuery
from micronaut.data.repository import CrudRepository
from org.bson.types import ObjectId

from example.Book import Book
from example.BookDTO import BookDTO
from example.Person import Person


# tag::repository[]
@MongoRepository  # <1>
class BookRepository(CrudRepository[Book, ObjectId]):  # <2>
    # end::repository[]

    # tag::simple[]
    def findByTitle(self, title: str) -> Book: ...

    def getByTitle(self, title: str) -> Book: ...

    def retrieveByTitle(self, title: str) -> Book: ...
    # end::simple[]

    # tag::greaterthan[]
    def findByPagesGreaterThan(self, pageCount: int) -> list[Book]: ...
    # end::greaterthan[]

    # tag::logical[]
    def findByPagesGreaterThanOrTitleRegex(self, pageCount: int, title: str) -> list[Book]: ...
    # end::logical[]

    # tag::simple-alt[]
    # tag::repository[]
    def find(self, title: str) -> Book: ...
    # end::simple-alt[]
    # end::repository[]

    # tag::pageable[]
    def findAllByPagesGreaterThan(self, pageCount: int, pageable: Pageable) -> list[Book]: ...

    def findByTitleRegex(self, title: str, pageable: Pageable) -> Page[Book]: ...

    def list(self, pageable: Pageable) -> Slice[Book]: ...
    # end::pageable[]

    # tag::simple-projection[]
    def findTitleByPagesGreaterThan(self, pageCount: int) -> list[str]: ...
    # end::simple-projection[]

    # tag::top-projection[]
    def findTop3ByTitleRegex(self, title: str) -> list[Book]: ...
    # end::top-projection[]

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
    def update(self, id: Annotated[ObjectId, Id], title: str) -> None: ...
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

    # tag::deleteby[]
    def deleteByTitleRegex(self, title: str) -> None: ...
    # end::deleteby[]

    # tag::dto[]
    def findOne(self, title: str) -> BookDTO: ...
    # end::dto[]

    # tag::custom[]
    @MongoFindQuery(filter="{title:{$regex: :t}}", sort="{title: 1}")
    def customFind(self, t: str) -> list[Book]: ...

    @MongoAggregateQuery("[{$match: {name:{$regex: :t}}}, {$sort: {name: 1}}, {$project: {name: 1}}]")
    def customAggregate(self, t: str) -> list[Person]: ...

    @MongoUpdateQuery(filter="{title:{$regex: :t}}", update="{$set:{name: 'tom'}}")
    def customUpdate(self, t: str) -> None: ...

    @MongoDeleteQuery(filter="{title:{$regex: :t}}", collation="{locale:'en_US', numericOrdering:true}")
    def customDelete(self, t: str) -> None: ...
    # end::custom[]
