from micronaut.data.annotation import Query
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model import Page, Pageable, Slice, Sort
from micronaut.data.repository import CrudRepository

from example.Book import Book
from example.BookSummary import BookSummary


@JdbcRepository(dialect="H2")
class PagedBookRepository(CrudRepository[Book, int]):

    # tag::explicit[]
    @Query(value="SELECT book_.* FROM book book_ WHERE book_.pages > :pages",  # <1>
           countQuery="SELECT COUNT(*) FROM book book_ WHERE book_.pages > :pages")  # <2>
    def findLongBooks(self, pages: int, pageable: Pageable) -> Page[Book]: ...

    @Query("SELECT book_.* FROM book book_ WHERE book_.pages > :pages")  # <3>
    def sliceLongBooks(self, pages: int, pageable: Pageable) -> Slice[Book]: ...

    @Query("SELECT book_.* FROM book book_ WHERE book_.pages > :pages")  # <3>
    def listLongBooks(self, pages: int, pageable: Pageable) -> list[Book]: ...

    @Query("SELECT book_.* FROM book book_ WHERE book_.pages > :pages")  # <4>
    def listLongBooksSorted(self, pages: int, sort: Sort) -> list[Book]: ...
    # end::explicit[]

    # tag::native[]
    @Query(value="SELECT * FROM book b WHERE b.title LIKE :title",
           countQuery="SELECT COUNT(*) FROM book b WHERE b.title LIKE :title",
           nativeQuery=True)  # <1>
    def searchByTitle(self, title: str, pageable: Pageable) -> Page[Book]: ...
    # end::native[]

    def list(self, pageable: Pageable) -> Page[BookSummary]: ...
