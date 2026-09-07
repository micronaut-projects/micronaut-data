package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.Upsert;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Requires(notEnv="oracle")
// tag::upsert-repository[]
@JdbcRepository(dialect = Dialect.H2)
public interface PassengerRepository extends CrudRepository<Passenger, Long> {

    @Upsert(conflictsOn = "email")
    Passenger upsertByEmail(Passenger passenger);

    @Upsert(conflictsOn = "email")
    List<Passenger> upsertByEmail(Iterable<Passenger> passengers);

    @Upsert(conflictsOn = "email")
    CompletableFuture<Passenger> upsertByEmailFuture(Passenger passenger);

    @Upsert(conflictsOn = "email")
    CompletableFuture<List<Passenger>> upsertByEmailFuture(Iterable<Passenger> passengers);
}
// end::upsert-repository[]
