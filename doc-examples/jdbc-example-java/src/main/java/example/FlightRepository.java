package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Upsert;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.concurrent.CompletableFuture;

@Requires(notEnv="oracle")
// tag::upsert-repository[]
@JdbcRepository(dialect = Dialect.H2)
public interface FlightRepository extends CrudRepository<Flight, String> {

    void upsert(Flight flight);

    void upsertAll(Iterable<Flight> flights);

    @Upsert
    void put(Flight flight);

    @Upsert
    void put(Iterable<Flight> flights);

    @Upsert
    CompletableFuture<Void> upsertFuture(Flight flight);

    @Upsert
    CompletableFuture<Void> upsertFuture(Iterable<Flight> flights);
}
// end::upsert-repository[]
