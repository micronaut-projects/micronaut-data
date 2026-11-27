package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.annotation.PathLimit;
import io.micronaut.data.graph.model.GraphPath;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@Neo4jRepository
public interface StationRepository extends CrudRepository<Station, String> {

    Optional<GraphPath<Station>> findShortestPathByLinksBetweenId(String from, String to);

    @PathLimit(5)
    boolean existsPathByLinksBetweenId(String from, String to);
}
