package io.micronaut.data.neo4j.features;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Neo4jRepository
public interface ReadingRepository extends CrudRepository<Reading, String> {

    List<Reading> findByLevel(Short level);
}
