from typing import Annotated

from jakarta.transaction import Transactional
from java.lang import Void
from java.util.function import Consumer
from micronaut.data.annotation import Id, Repository
from micronaut.data.model import Page, Pageable, Slice
from micronaut.data.repository.reactive import ReactorCrudRepository
from reactor.core.publisher import Flux, Mono

from example.Book import Book
from example.BookDTO import BookDTO


# tag::repository[]
@Repository  # <1>
class BookRepository(ReactorCrudRepository[Book, int]):  # <2>

    # tag::read[]
    def find(self, title: str) -> Mono[Book]: ...

    def findOne(self, title: str) -> Mono[BookDTO]: ...

    def findByPagesGreaterThan(self, pageCount: int, pageable: Pageable) -> Flux[Book]: ...

    def findByTitleLike(self, title: str, pageable: Pageable) -> Mono[Page[Book]]: ...

    def list(self, pageable: Pageable) -> Mono[Slice[Book]]: ...
    # end::read[]

    @Transactional
    def find_by_id_and_update(self, id: int, book_consumer: Consumer[Book]) -> Mono[Void]:
        def apply(book):
            book_consumer(book)
            return book
        return self.findById(id).map(apply).then()

    # tag::save[]
    def save(self, entity: Book) -> Mono[Book]: ...
    # end::save[]

    # tag::update[]
    def update(self, newBook: Book) -> Mono[Book]: ...

    def updatePagesById(self, id: Annotated[int, Id], pages: int) -> Mono[Void]: ...
    # end::update[]

    # tag::delete[]
    def deleteAll(self) -> Mono[int]: ...

    def delete(self, title: str) -> Mono[Void]: ...
    # end::delete[]
# end::repository[]
