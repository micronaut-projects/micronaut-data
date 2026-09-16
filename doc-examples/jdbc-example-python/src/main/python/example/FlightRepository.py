from java.lang import Void
from java.util.concurrent import CompletableFuture
from micronaut.data.annotation import Upsert
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.Flight import Flight


# tag::upsert-repository[]
@JdbcRepository(dialect=Dialect.H2)
class FlightRepository(CrudRepository[Flight, str]):

    def upsert(self, flight: Flight) -> None: ...

    def upsertAll(self, flights: list[Flight]) -> None: ...

    @Upsert
    def put(self, flight: Flight) -> None: ...

    @Upsert
    def putAll(self, flights: list[Flight]) -> None: ...

    @Upsert
    def upsertFuture(self, flight: Flight) -> CompletableFuture[Void]: ...

    @Upsert
    def upsertAllFuture(self, flights: list[Flight]) -> CompletableFuture[Void]: ...
# end::upsert-repository[]
