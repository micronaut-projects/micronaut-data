from java.util.concurrent import CompletableFuture
from micronaut.data.annotation import Upsert
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.Passenger import Passenger


# tag::upsert-repository[]
@JdbcRepository(dialect=Dialect.H2)
class PassengerRepository(CrudRepository[Passenger, int]):

    @Upsert(conflictsOn="email")
    def upsertByEmail(self, passenger: Passenger) -> Passenger: ...

    @Upsert(conflictsOn="email")
    def upsertAllByEmail(self, passengers: list[Passenger]) -> list[Passenger]: ...

    @Upsert(conflictsOn="email")
    def upsertByEmailFuture(self, passenger: Passenger) -> CompletableFuture[Passenger]: ...

    @Upsert(conflictsOn="email")
    def upsertAllByEmailFuture(self, passengers: list[Passenger]) -> CompletableFuture[list[Passenger]]: ...
# end::upsert-repository[]
