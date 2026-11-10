package io.micronaut.data.oracle.graph.tck;

import io.micronaut.data.graph.tck.repositories.MovieRepository;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;

@OracleGraphRepository(graph = "tck")
public interface OracleMovieRepository extends MovieRepository {
}
