from typing import Annotated

from java.util.stream import Stream
from micronaut.context.annotation import Parameter
from micronaut.data.annotation import Fetch
from micronaut.data.annotation import Id, ParameterExpression, Query, QueryHint, Repository
from micronaut.data.model import Page, Pageable, Slice
from micronaut.data.repository import CrudRepository

from example.Book import Book
from example.BookDTO import BookDTO


# tag::repository[]
@Repository  # <1>
class BookRepository(CrudRepository[Book, int]):  # <2>
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
    def findByPagesGreaterThanOrTitleLike(self, pageCount: int, title: str) -> list[Book]: ...
    # end::logical[]

    # tag::simple-alt[]
    # tag::repository[]
    def find(self, title: str) -> Book: ...
    # end::simple-alt[]
    # end::repository[]

    # tag::pageable[]
    def findAllByPagesGreaterThan(self, pageCount: int, pageable: Pageable) -> list[Book]: ...

    def findByTitleLike(self, title: str, pageable: Pageable) -> Page[Book]: ...

    def list(self, pageable: Pageable) -> Slice[Book]: ...
    # end::pageable[]

    # tag::simple-projection[]
    def findTitleByPagesGreaterThan(self, pageCount: int) -> list[str]: ...
    # end::simple-projection[]

    # tag::top-projection[]
    def findTop3ByTitleLike(self, title: str) -> list[Book]: ...
    # end::top-projection[]

    # tag::ordering[]
    def listOrderByTitle(self) -> list[Book]: ...

    def listOrderByTitleDesc(self) -> list[Book]: ...
    # end::ordering[]

    # tag::explicit[]
    @Query("FROM Book b WHERE b.title = :t ORDER BY b.title")
    def listBooks(self, t: str) -> list[Book]: ...
    # end::explicit[]

    # tag::save[]
    def save(self, entity: Book) -> Book: ...
    # end::save[]

    # tag::inserts[]
    @Query("INSERT INTO Book(title, pages) VALUES (:title, :pages)")
    @ParameterExpression(name="title", expression="#{book.title + 'ABC'}")
    @ParameterExpression(name="pages", expression="#{book.pages}")
    def insertCustomExp(self, book: Book) -> None: ...

    @Query("INSERT INTO Book(title, pages) VALUES (:title, :pages)")
    def insertOne(self, book: Book) -> None: ...

    @Query("INSERT INTO Book(title, pages) VALUES (:title, :pages)")
    def insertMany(self, books: list[Book]) -> None: ...
    # end::inserts[]

    # tag::save2[]
    def persist(self, title: str, pages: int) -> Book: ...
    # end::save2[]

    # tag::update[]
    def update(self, newBook: Book) -> Book: ...
    # end::update[]

    # tag::update1[]
    def updatePagesById(self, id: Annotated[int, Id], pages: int) -> None: ...
    # end::update1[]

    # tag::update2[]
    def updateByTitle(self, title: str, pages: int) -> None: ...
    # end::update2[]

    # tag::update3[]
    @QueryHint(name="jakarta.persistence.FlushModeType", value="AUTO")
    def updatePages(self, id: Annotated[int, Id], pages: Annotated[int, Parameter("pages")]) -> None: ...
    # end::update3[]

    # tag::updateCustomQuery[]
    @Query("UPDATE book SET title = :title where id = :id")
    def updateOne(self, book: Book) -> None: ...

    @Query("UPDATE book SET title = :title where id = :id")
    def updateMany(self, books: list[Book]) -> None: ...
    # end::updateCustomQuery[]

    # tag::deleteall[]
    def deleteAll(self) -> None: ...
    # end::deleteall[]

    # tag::deleteCustomQuery[]
    @Query("DELETE FROM Book WHERE title = :title")
    def deleteOne(self, book: Book) -> None: ...

    @Query("DELETE FROM Book WHERE title = :title")
    def deleteMany(self, books: list[Book]) -> None: ...
    # end::deleteCustomQuery[]

    # tag::deleteone[]
    def delete(self, title: str) -> None: ...
    # end::deleteone[]

    # tag::deleteby[]
    def deleteByTitleLike(self, title: str) -> None: ...
    # end::deleteby[]

    # tag::dto[]
    def findOne(self, title: str) -> BookDTO: ...
    # end::dto[]

    # tag::native[]
    @Query(value="select * from books b where b.title like :title limit 5", nativeQuery=True)
    def findNativeBooks(self, title: str) -> list[Book]: ...
    # end::native[]

    # tag::stream_projection[]
    @Fetch(1000)
    def listAll(self) -> Stream[BookDTO]: ...
    # end::stream_projection[]
