package io.micronaut.data.oracle.graph.r2dbc.wallet;

import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;

@OracleGraphRepository(value = "jdbc", graph = "wallet_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ)
public interface JdbcWalletGraph extends GenericRepository<Wallet, Long> {

    Optional<Wallet> findById(Long id);

    Wallet save(Wallet wallet);
}
