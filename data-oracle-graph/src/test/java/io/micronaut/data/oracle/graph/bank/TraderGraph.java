package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;

@OracleGraphRepository(graph = "bank_graph")
public interface TraderGraph extends GenericRepository<Trader, Long> {

    @PathLimit(3)
    List<GraphPath<Trader>> findAllPathsByExchanged(Long from, Long to);
}
