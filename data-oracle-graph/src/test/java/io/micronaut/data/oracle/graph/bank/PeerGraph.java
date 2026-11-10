package io.micronaut.data.oracle.graph.bank;

import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.GenericRepository;

import java.util.List;

@OracleGraphRepository(graph = "bank_graph")
public interface PeerGraph extends GenericRepository<Peer, Long> {

    List<GraphRelationship<Peer, Peer>> findRelationshipsByPeers(Long from, Long to);
}
