package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;

@OracleGraphRepository(graph = "bank_graph", language = PropertyGraph.QueryLanguage.PGQL)
public interface PgqlBankGraph extends GenericRepository<Account, Long> {

    List<Account> findByIdGreaterThan(Long id);
}
