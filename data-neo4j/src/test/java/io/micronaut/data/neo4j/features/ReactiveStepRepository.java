package io.micronaut.data.neo4j.features;

import io.micronaut.data.graph.tck.entities.Step;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

import java.util.UUID;

@Neo4jRepository
public interface ReactiveStepRepository extends ReactorCrudRepository<Step, UUID> {
}
