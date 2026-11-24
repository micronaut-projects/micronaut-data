package io.micronaut.data.oracle.graph.schemas;

import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;

@OracleGraphRepository(graph = "shelf_graph")
public interface ShelfGraph extends GenericRepository<Shelf, Long> {

    Optional<Shelf> findById(Long id);
}
