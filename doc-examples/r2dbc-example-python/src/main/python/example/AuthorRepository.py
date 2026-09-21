from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.r2dbc.annotation import R2dbcRepository
from micronaut.data.repository.reactive import ReactiveStreamsCrudRepository
from reactor.core.publisher import Flux, Mono

from example.Author import Author


@R2dbcRepository(dialect=Dialect.POSTGRES)  # <1>
class AuthorRepository(ReactiveStreamsCrudRepository[Author, int]):

    def findById(self, id: int) -> Mono[Author]: ...  # <2>

    def findAll(self) -> Flux[Author]: ...
