package io.micronaut.data.oracle.graph.r2dbc.library;

import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

@OracleGraphR2dbcRepository(graph = "library_graph")
public interface AuthorRepository extends ReactorCrudRepository<Author, Long> {
}
