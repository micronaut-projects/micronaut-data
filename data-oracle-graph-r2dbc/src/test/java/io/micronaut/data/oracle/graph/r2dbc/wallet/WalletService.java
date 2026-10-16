package io.micronaut.data.oracle.graph.r2dbc.wallet;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class WalletService {

    private final WalletGraph walletGraph;

    public WalletService(WalletGraph walletGraph) {
        this.walletGraph = walletGraph;
    }

    @Transactional
    public Mono<Long> saveAndCount(Wallet wallet) {
        return walletGraph.save(wallet).then(walletGraph.findByOwnerStartsWithOrderByOwner("").count());
    }

    @Transactional
    public Mono<Wallet> saveAndFail(Wallet wallet) {
        return walletGraph.save(wallet).then(Mono.error(new IllegalStateException("Rolled back")));
    }
}
