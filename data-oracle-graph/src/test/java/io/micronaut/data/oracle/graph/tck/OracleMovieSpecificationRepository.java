package io.micronaut.data.oracle.graph.tck;

import io.micronaut.data.graph.tck.repositories.MovieSpecificationRepository;
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository;

@OracleGraphRepository(graph = "tck")
public interface OracleMovieSpecificationRepository extends MovieSpecificationRepository {
}
