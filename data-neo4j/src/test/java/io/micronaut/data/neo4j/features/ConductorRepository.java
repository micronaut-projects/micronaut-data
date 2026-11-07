package io.micronaut.data.neo4j.features;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

@Neo4jRepository
public interface ConductorRepository extends CrudRepository<Conductor, String> {
}
