package example

import io.micronaut.data.annotation.Upsert
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

// tag::upsert-repository[]
@JdbcRepository(dialect = Dialect.H2)
interface FlightRepository : KotlinCrudRepository<Flight, String> {

    fun upsert(flight: Flight)

    fun upsertAll(flights: Iterable<Flight>)

    @Upsert
    fun put(flight: Flight)

    @Upsert
    fun put(flights: Iterable<Flight>)

    @Upsert
    suspend fun upsertSuspend(flight: Flight)

    @Upsert
    suspend fun upsertSuspend(flights: Iterable<Flight>)
}
// end::upsert-repository[]
