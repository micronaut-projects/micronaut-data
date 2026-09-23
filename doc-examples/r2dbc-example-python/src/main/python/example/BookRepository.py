from jakarta.transaction import Transactional
from micronaut.data.annotation import Join
from micronaut.data.annotation.sql import Procedure
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.r2dbc.annotation import R2dbcRepository
from micronaut.data.repository.reactive import ReactiveStreamsCrudRepository
from org.reactivestreams import Publisher
from reactor.core.publisher import Flux, Mono

from example.Book import Book


@R2dbcRepository(dialect=Dialect.POSTGRES)  # <1>
class BookRepository(ReactiveStreamsCrudRepository[Book, int]):

    @Join("author")
    def findById(self, id: int) -> Mono[Book]: ...  # <2>

    @Join("author")
    def findAll(self) -> Flux[Book]: ...

    # tag::mandatory[]
    @Transactional("MANDATORY")
    def save(self, entity: Book) -> Publisher[Book]: ...
    # end::mandatory[]
    # TODO(python): overriding the inherited generic `<S extends Book> Publisher<S> saveAll(Iterable<S>)` is not
    # possible yet: a `list[Book]` hint clashes with the inherited erasure and a `[S: Book]` type parameter is still
    # rejected by the Micronaut Data visitor with core 5.2.4 (`Unsupported return type for a save method: python.S`)

    # tag::procedure[]
    @Procedure
    def calculateSum(self, bookId: int) -> Mono[int]: ...
    # end::procedure[]
