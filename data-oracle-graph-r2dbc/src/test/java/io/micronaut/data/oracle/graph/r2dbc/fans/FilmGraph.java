package io.micronaut.data.oracle.graph.r2dbc.fans;

import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

@OracleGraphR2dbcRepository(graph = "fan_graph")
public interface FilmGraph extends ReactorCrudRepository<Film, Long> {

    Flux<Film> findByFansFirstName(String firstName);
}
