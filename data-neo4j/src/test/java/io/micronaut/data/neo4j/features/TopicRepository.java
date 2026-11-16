package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@Neo4jRepository
public interface TopicRepository extends CrudRepository<Topic, String> {
    @Join("tags")
    Optional<Topic> findByTitle(String title);
}
