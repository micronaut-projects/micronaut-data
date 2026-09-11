package example;

import io.micronaut.data.annotation.Upsert;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// tag::upsert-repository[]
@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface FlightRepository extends CrudRepository<Flight, String> {

    @Upsert
    Mono<Flight> upsertMono(Flight flight);

    @Upsert
    Flux<Flight> upsertFlux(Iterable<Flight> flights);
}
// end::upsert-repository[]
