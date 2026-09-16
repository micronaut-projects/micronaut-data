from micronaut.data.annotation import Upsert
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.r2dbc.annotation import R2dbcRepository
from micronaut.data.repository import CrudRepository
from reactor.core.publisher import Flux, Mono

from example.Flight import Flight


# tag::upsert-repository[]
@R2dbcRepository(dialect=Dialect.POSTGRES)
class FlightRepository(CrudRepository[Flight, str]):

    @Upsert
    def upsertMono(self, flight: Flight) -> Mono[Flight]: ...

    @Upsert
    def upsertFlux(self, flights: list[Flight]) -> Flux[Flight]: ...
# end::upsert-repository[]
