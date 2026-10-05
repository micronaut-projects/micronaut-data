package example

import io.micronaut.data.annotation.Upsert
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// tag::upsert-repository[]
@R2dbcRepository(dialect = Dialect.POSTGRES)
interface FlightRepository : KotlinCrudRepository<Flight, String> {

    @Upsert
    fun upsertMono(flight: Flight): Mono<Flight>

    @Upsert
    fun upsertFlux(flights: Iterable<Flight>): Flux<Flight>
}
// end::upsert-repository[]
