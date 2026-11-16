package io.micronaut.data.oracle.graph.r2dbc.wallet;

import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Mono;

@OracleGraphR2dbcRepository(graph = "vault_graph")
public interface VaultGraph extends ReactorCrudRepository<Vault, Long> {

    Mono<String> updateReturningName(Vault vault);
}
