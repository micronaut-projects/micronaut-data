# tag::repository[]
from typing import Annotated

from micronaut.data.annotation import Id, Join, ParameterExpression, Query
from micronaut.data.annotation.sql import Procedure
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model import CursoredPage, CursoredPageable, Page, Pageable, Slice
from micronaut.data.repository import CrudRepository

from example.Book import Book
from example.BookDTO import BookDTO


@JdbcRepository(dialect="H2")  # <1>
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

    # tag::cursored-pageable[]
    def findAll(self, pageable: CursoredPageable) -> CursoredPage[Book]: ...  # <1>

    def findByPagesBetween(self, minPageCount: int, maxPageCount: int, pageable: Pageable) -> CursoredPage[Book]: ...  # <2>

    def findByTitleStartingWith(self, title: str, pageable: Pageable) -> Page[Book]: ...  # <3>
    # end::cursored-pageable[]

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
    @Query("SELECT * FROM Book AS b WHERE b.title = :t ORDER BY b.title")
    def listBooks(self, t: str) -> list[Book]: ...
    # end::explicit[]

    # tag::save[]
    def persist(self, entity: Book) -> Book: ...
    # end::save[]

    # tag::save2[]
    def store(self, title: str, pages: int) -> Book: ...
    # end::save2[]

    @Query("INSERT INTO Book(title, pages) VALUES (:title, :pages)")
    @ParameterExpression(name="title", expression="#{book.title + 'ABC'}")
    @ParameterExpression(name="pages", expression="#{book.pages}")
    def insertCustomExp(self, book: Book) -> None: ...

    # tag::update[]
    def update(self, id: Annotated[int, Id], title: str) -> None: ...
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
    def deleteByTitleLike(self, title: str) -> None: ...
    # end::deleteby[]

    # tag::dto[]
    def findOne(self, title: str) -> BookDTO: ...
    # end::dto[]

    # tag::native[]
    @Query("select * from book b where b.title like :title limit 5")
    def findBooks(self, title: str) -> list[Book]: ...
    # end::native[]

    # tag::procedure[]
    @Procedure
    def calculateSum(self, bookId: int) -> int: ...
    # end::procedure[]

    # tag::onetomanycustom[]
    @Query("""
        SELECT book_.*,
               reviews_.id AS reviews_id, reviews_.reviewer AS reviews_reviewer,
               reviews_.content AS reviews_content, reviews_.book_id AS reviews_book_id
        FROM book book_ INNER JOIN review reviews_ ON book_.id = reviews_.book_id
        WHERE book_.title = :title
        """)
    @Join("reviews")
    def searchBooksByTitle(self, title: str) -> list[Book]: ...
    # end::onetomanycustom[]
