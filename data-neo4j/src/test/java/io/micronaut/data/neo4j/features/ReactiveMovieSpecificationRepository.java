package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.tck.entities.Movie;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor;

@Neo4jRepository
public interface ReactiveMovieSpecificationRepository extends GenericRepository<Movie, String>, ReactorJpaSpecificationExecutor<Movie> {
}
