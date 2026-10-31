package io.micronaut.data.neo4j.features;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@Neo4jRepository
public interface TagRepository extends CrudRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    @Query("MATCH (t:Tag) WITH t ORDER BY t.name LIMIT 2 RETURN t")
    List<Tag> findFirstTwo(Pageable pageable);

    @Query("MATCH (t:Tag) RETURN t ORDER BY t.name LIMIT 2")
    List<Tag> findTwo(Pageable pageable);
}
