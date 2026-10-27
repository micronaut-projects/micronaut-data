package io.micronaut.data.neo4j.features;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

@Neo4jRepository("other")
public interface OtherDriverTagRepository extends CrudRepository<Tag, Long> {

    Optional<Tag> findByName(String name);
}
