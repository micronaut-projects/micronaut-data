package io.micronaut.data.oracle.graph.r2dbc.wallet;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@OracleGraphR2dbcRepository(graph = "wallet_graph")
public interface WalletGraph extends ReactorCrudRepository<Wallet, Long> {

    Flux<Wallet> findByOwnerStartsWithOrderByOwner(String prefix);

    Flux<Wallet> findByPaymentsAmountGreaterThan(@Nullable BigDecimal amount);

    Flux<Wallet> findByPaymentsToOwner(String owner);

    @PathLimit(3)
    Mono<Boolean> existsPathByPayments(Long from, Long to);

    java.util.concurrent.CompletableFuture<Long> countByOwnerStartsWith(String prefix);
}
