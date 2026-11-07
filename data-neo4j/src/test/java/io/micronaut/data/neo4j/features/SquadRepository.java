package io.micronaut.data.neo4j.features;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

@Neo4jRepository
public interface SquadRepository extends CrudRepository<Squad, String> {
}
