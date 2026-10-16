package io.micronaut.data.oracle.graph.library;

import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;
import io.micronaut.data.repository.CrudRepository;

@OracleGraphRepository(graph = "library_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ)
public interface AuthorRepository extends CrudRepository<Author, Long> {
}
