package example

import io.micronaut.data.annotation.Upsert
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

// tag::upsert-repository[]
@JdbcRepository(dialect = Dialect.H2)
interface PassengerRepository : KotlinCrudRepository<Passenger, Long> {

    @Upsert(conflictsOn = ["email"])
    fun upsertByEmail(passenger: Passenger): Passenger

    @Upsert(conflictsOn = ["email"])
    fun upsertByEmail(passengers: Iterable<Passenger>): List<Passenger>

    @Upsert(conflictsOn = ["email"])
    suspend fun upsertByEmailSuspend(passenger: Passenger): Passenger

    @Upsert(conflictsOn = ["email"])
    suspend fun upsertByEmailSuspend(passengers: Iterable<Passenger>): List<Passenger>
}
// end::upsert-repository[]
