package io.micronaut.data.neo4j.features;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

@Neo4jRepository
public interface ReactiveTagRepository extends ReactorCrudRepository<Tag, Long> {
}
