package io.micronaut.data.oracle.graph.library;

import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.CrudRepository;

@OracleGraphRepository(graph = "library_graph")
public interface AuthorRepository extends CrudRepository<Author, Long> {
}
