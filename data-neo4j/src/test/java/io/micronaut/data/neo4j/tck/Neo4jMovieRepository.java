package io.micronaut.data.neo4j.tck;

import io.micronaut.data.graph.tck.repositories.MovieRepository;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;

@Neo4jRepository
public interface Neo4jMovieRepository extends MovieRepository {
}
