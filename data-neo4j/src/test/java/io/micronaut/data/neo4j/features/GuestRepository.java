package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.PathDirection;
import io.micronaut.data.graph.model.GraphRelationship;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Neo4jRepository
public interface GuestRepository extends CrudRepository<Guest, String> {

    long countRelationshipsByHosts();

    long countRelationshipsByHosts(@PathDirection Direction direction);

    List<GraphRelationship<Guest, Host>> findRelationshipsByHosts();

    List<GraphRelationship<Guest, Host>> findRelationshipsByHosts(@PathDirection Direction direction);
}
