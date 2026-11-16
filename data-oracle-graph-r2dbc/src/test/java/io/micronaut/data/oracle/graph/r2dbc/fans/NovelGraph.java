package io.micronaut.data.oracle.graph.r2dbc.fans;

import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

@OracleGraphR2dbcRepository(graph = "fan_graph")
public interface NovelGraph extends ReactorCrudRepository<Novel, Long> {

    Flux<Novel> findBySequelsTitle(String title);

    Flux<Novel> findByPrequelsTitle(String title);
}
