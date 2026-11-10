package io.micronaut.data.oracle.graph.tck;

import io.micronaut.data.graph.annotation.PropertyGraph;
import io.micronaut.data.graph.tck.repositories.MovieRepository;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;

@OracleGraphRepository(graph = "tck", language = PropertyGraph.QueryLanguage.SQL_PGQ)
public interface OracleMovieRepository extends MovieRepository {
}
