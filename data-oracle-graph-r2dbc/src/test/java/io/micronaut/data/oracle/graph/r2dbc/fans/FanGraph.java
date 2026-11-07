package io.micronaut.data.oracle.graph.r2dbc.fans;

import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

@OracleGraphR2dbcRepository(graph = "fan_graph")
public interface FanGraph extends ReactorCrudRepository<Fan, Long> {

    Flux<Fan> findByFirstName(String firstName);

    Flux<Fan> findByFilmsTitle(String title);

    Flux<Fan> findByNovelsTitle(String title);
}
